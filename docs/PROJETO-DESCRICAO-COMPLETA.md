# POD99 - Desafio Técnico de Arquitetura de Aplicação

## 1. Introdução

Este documento descreve o desafio técnico para a posição de **Arquiteto de Aplicação** no time POD99 da AWS/Itaú Unibanco.

## 2. Requisitos Funcionais e Operacionais

### 2.1 Contexto

Você será responsável por arquitetar e implementar uma **plataforma de gestão de limites e autorização de transações financeiras** para o Itaú Unibanco, usando AWS como provedor de infraestrutura.

**Características do domínio:**
- ~80 milhões de contas ativas
- 3-5 contratos por conta (média)
- ~5.000 TPS (transações por segundo) em horários de pico
- Exigência de idempotência em todas operações
- Necessidade de arquitetura event-driven para desacoplamento
- Latência p99 < 100ms esperada

### 2.2 Volumetria e NFRs

| Critério | Valor |
|----------|-------|
| Contas ativas | ~80 milhões |
| Contratos por conta | 3-5 (média) |
| TPS pico | ~5.000 |
| Latência p99 esperada | < 100ms |
| Disponibilidade | 99.99% (4 noves) |

### 2.3 API Contract (Seção 2.3)

**Endpoint:**
```
POST /v1/contratos/{id_contrato}/autorizacoes
```

**Headers Obrigatórios:**
- `Idempotency-Key`: UUID (para idempotência)

**Request Body:**
```json
{
  "id_conta": "string",
  "valor": "decimal 18,2",
  "moeda": "ISO 4217",
  "tipo_operacao": "DEBITO|CREDITO|RESERVA|ESTORNO",
  "id_estabelecimento": "string (opcional)",
  "metadata": "object (opcional)"
}
```

**Respostas HTTP Esperadas:**
| Status | Significado |
|--------|-------------|
| 201 | Created - Transação aprovada |
| 200 | OK - Idempotência (repetição da mesma requisição) |
| 402 | Payment Required - Limite insuficiente |
| 409 | Conflict - Conflito de concorrência (lock falhou) |
| 422 | Unprocessable Entity - Erro de validação |
| 429 | Too Many Requests - Rate limit atingido |
| 503 | Service Unavailable - Serviço indisponível |

**Response Body (Sucesso):**
```json
{
  "id_autorizacao": "UUID",
  "saldo_reservado": "decimal 18,2",
  "status": "APPROVED|DENIED|PENDING",
  "correlation_id": "UUID",
  "timestamp": "ISO 8601"
}
```

**Error Response (RFC 7807 - Problem Details):**
```json
{
  "type": "https://api.pod99.com/errors/insufficient-limit",
  "title": "Insufficient Limit",
  "status": 402,
  "detail": "Limite disponível insuficiente para autorizar esta transação",
  "instance": "/v1/contratos/ABC123/autorizacoes",
  "correlation_id": "UUID"
}
```

**Requisitos de Contrato:**
- ✅ Idempotência garantida via `Idempotency-Key`
- ✅ Versionamento explícito (`/v1` com política de depreciação)
- ✅ Erros em RFC 7807 (Problem Details)
- ✅ Contrato descrito em OpenAPI 3.x (contract-first)
- ✅ Correlation ID em todas as respostas

### 2.4 Event Contract

**Evento:** `TransacaoAutorizada` (v1)

**Publicado em:** AWS EventBridge

**Formato:** CloudEvents (standard)

**Campos Obrigatórios:**
```json
{
  "event_id": "UUID (único para deduplicação)",
  "event_type": "com.pod99.transacao.autorizada",
  "event_version": "v1",
  "occurred_at": "2024-01-01T12:00:00Z (UTC)",
  "id_autorizacao": "string",
  "id_contrato": "string",
  "valor": "decimal 18,2 (BRL)",
  "saldo_reservado": "decimal 18,2",
  "correlation_id": "UUID",
  "trace_id": "string (W3C, nullable)"
}
```

**Requisitos de Evento:**
- ✅ Formato CloudEvents (imutável, nomeado no passado)
- ✅ `event_id` único para deduplicação (at-least-once delivery)
- ✅ Evolução de schema retrocompatível via schema registry
- ✅ Consumidores idempotentes, tolerando reordenação e reentrega

---

## 3. Requisitos Técnicos

### 3.1 Arquitetura de Aplicação

**Princípios Obrigatórios:**

1. **SOLID (5 princípios explícitos):**
   - Single Responsibility (SRP): Cada classe uma razão para mudar
   - Open/Closed (OCP): Aberto para extensão, fechado para modificação
   - Liskov Substitution (LSP): Subclasses substituem base classes
   - Interface Segregation (ISP): Interfaces específicas, não genéricas
   - Dependency Inversion (DIP): Depender de abstrações, não implementações

2. **Clean Code:**
   - Nomes reveladores (variáveis, funções, classes)
   - Funções pequenas e coesas
   - Error handling consistente
   - Testes legíveis e bem nomeados

3. **Clean/Hexagonal Architecture:**
   - Separação clara: Domínio, Aplicação, Infraestrutura
   - Ports & Adapters: Isolamento de dependências externas
   - Inversão de controle: Injeção de dependências

4. **Domain-Driven Design (DDD):**
   - Bounded Contexts alinhados a fronteiras de serviço
   - Agregados com invariantes claros
   - Domain Events para comunicação entre contextos
   - Linguagem ubíqua (vocabulário compartilhado)

5. **Padrões Táticos DDD (discussão obrigatória):**
   - CQRS: Command Query Responsibility Segregation (quando usar, trade-offs)
   - Repository Pattern: Abstração de persistência
   - Domain Events: Eventos de domínio vs. eventos de integração
   - Value Objects: Imutabilidade e equidade

### 3.2 Microsserviços e Comunicação AWS

**Requisito:**
- Fronteiras de serviço alinhadas a Bounded Contexts (BCs)
- Três serviços naturais:
  1. **Serviço de Autorização** (sync, crítico)
  2. **Serviço de Limites** (sync, cálculo)
  3. **Serviço de Contabilização** (async, analytics)

**Decisões Necessárias:**
- ✅ Escolher runtime (AWS Lambda, ECS/Fargate, EKS)
- ✅ Justificar SLA latência e throughput
- ✅ Database-per-Service strategy (DynamoDB ou Aurora)

**Avaliação Esperada:**
```
| Aspecto | Lambda | ECS Fargate | EKS |
|---------|--------|-------------|-----|
| Latência | ? | ? | ? |
| Custo | ? | ? | ? |
| Operações | ? | ? | ? |
| Escalabilidade | ? | ? | ? |
```

### 3.3 Arquitetura Event-Driven

**Componentes Obrigatórios:**
- ✅ AWS EventBridge: Event bus com roteamento por regras
- ✅ SNS: Tópicos para fan-out de eventos
- ✅ SQS: Filas durável para consumers assíncronos
- ✅ Dead Letter Queue (DLQ): Para tratamento de falhas

**Análise Esperada:**

Comparar **SQS vs Kinesis vs Kafka (MSK)** para alta volumetria (5k TPS):

```
| Critério | SQS | Kinesis | Kafka MSK |
|----------|-----|---------|-----------|
| Throughput | ? | ? | ? |
| Ordenação por chave | ? | ? | ? |
| Custo | ? | ? | ? |
| Operações | ? | ? | ? |
| Acoplamento com AWS | ? | ? | ? |
```

### 3.4 API Management e Contratos

**Requisitos:**

1. **API Gateway (AWS):**
   - REST/HTTP via API Gateway
   - Autorização (Cognito, Lambda Authorizer, OAuth2)
   - Rate limiting, throttling, usage plans
   - Quotas por consumidor

2. **Contratos:**
   - Contract-first com OpenAPI 3.x
   - Versionamento explícito (`/v1`, `/v2`)
   - Política de depreciação documentada
   - Semver (SemVer) para breaking changes

3. **Discussão Necessária:**
   - API Gateway vs. Service Mesh vs. BFF (Backend for Frontend)
   - Trade-offs de cada abordagem

---

## 4. Uso de Inteligência Artificial

O uso de ferramentas de IA é **PERMITIDO E ENCORAJADO** como competência relevante.

**Obrigações:**
- ✅ Saber explicar cada decisão arquitetural em detalhes
- ✅ Justificar trade-offs técnicos
- ✅ Demonstrar entendimento do código gerado
- ✅ Propor alternativas e discutir quando a solução não é ideal

---

## 5. Prazo e Entregáveis

### Prazo
**5 dias úteis** a partir do recebimento do documento

### Formato de Entrega
Arquivo ZIP contendo:
- Código-fonte completo
- Infrastructure as Code (Terraform)
- Diagramas (C4, arquitetura)
- Architecture Decision Records (ADRs)
- README com instruções de execução

### Defesa Técnica
**Duração:** 60 minutos
- **20 minutos:** Apresentação da solução
- **40 minutos:** Perguntas técnicas sobre trade-offs e alternativas
- **Demonstração:** Execução da solução (local com Docker ou AWS)

---

## Resumo de Checkpoints

### ✅ O QUE DEVE ESTAR 100% PRONTO

- [ ] API Contract (POST /v1/contratos/{id}/autorizacoes)
- [ ] Request/Response conforme 2.3 e 2.4
- [ ] RFC 7807 errors
- [ ] Idempotency-Key header
- [ ] OpenAPI 3.x spec
- [ ] SOLID Principles (todos os 5)
- [ ] Clean/Hexagonal Architecture
- [ ] DDD (Bounded Contexts, Domain Events)
- [ ] Event-Driven (EventBridge + SNS + SQS)
- [ ] Testes (unit + integration)
- [ ] Terraform IaC completo
- [ ] Diagramas C4
- [ ] ADRs (Architecture Decision Records)
- [ ] Documentação executável (docker-compose)
- [ ] Lambda vs ECS vs EKS (decisão justificada)
- [ ] SQS vs Kinesis vs Kafka (análise)

### ⚠️ GAPS ACEITOS (MAS SERÃO QUESTIONADOS)

- Escalabilidade real (não foi testado em 5k TPS)
- Compliance/PCI-DSS (opcional, mas pode ser perguntado)
- Multi-region (roadmap, não MVP)
- Circuit Breaker (não foi mencionado, não é obrigatório)
- X-Ray tracing completo (pode ser perguntado em defesa)

