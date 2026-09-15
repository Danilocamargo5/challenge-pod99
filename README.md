# POD99 — Plataforma de Autorização de Transações

[![Build](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/Danilocamargo5/challenge-pod99)
[![Java](https://img.shields.io/badge/java-21-blue)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/spring%20boot-3.1.5-green)](https://spring.io/projects/spring-boot)
[![AWS](https://img.shields.io/badge/AWS-DynamoDB%20%7C%20EventBridge%20%7C%20SQS-orange)](https://aws.amazon.com)

## O Desafio

Arquitetar uma plataforma transacional que:
- ✅ Autoriza **5.000 transações/segundo**
- ✅ Garante consistência de limites (sem race conditions)
- ✅ Publica eventos pra sistemas desacoplados
- ✅ Segue padrões production-grade (SOLID, DDD, Clean Architecture)

---

## Stack Tecnológico

- **Linguagem**: Java 21
- **Framework**: Spring Boot 3.1.5
- **Banco de Dados**: DynamoDB (AWS Local)
- **Message Broker**: EventBridge + SQS (LocalStack)
- **API Gateway**: 
  - **Local**: Simulator em Python/FastAPI (porta 8081)
  - **AWS**: AWS API Gateway oficial (com Lambda Authorizer)
- **Autorização**: Lambda Authorizer (JWT validation)
- **Orquestração**: Docker Compose
- **Logging**: Logback + Logstash (JSON estruturado)
- **Build**: Maven 3.9

---

## Arquitetura

### 3 Bounded Contexts (DDD)

1. **Authorization** - Valida e aprova transações
2. **Limits** - Gerencia limite disponível por contrato
3. **Accounting** - Registra lançamento contábil (async)

### Fluxo

```
POST /v1/contratos/{id}/autorizacoes
  ↓
[API Gateway] (LocalStack com Simulator)
  ↓
[Lambda Authorizer] Valida JWT → retorna accountId
  ↓
[Spring Boot] (localhost:8080) com X-Account-Id header
  ↓
[LockService] Adquire locks (5 retry × exponential backoff)
  ↓
[AuthorizeUseCase] Valida limite → Reserva → Persiste
  ↓
[EventBridgePublisher] Publica TransacaoAutorizadaEvent
  ↓
EventBridge Rule → SQS → AccountingEventListener (async)
  ↓
HTTP 201 Created {id_autorizacao, saldo_reservado, correlation_id}
```

---

## 🔐 Lambda Authorizer

LocalStack **não implementa API Gateway com Authorizer automaticamente**, então criamos um **Simulator em Python/FastAPI** que:

1. ✅ Recebe requisições na porta **8081**
2. ✅ Valida JWT chamando Lambda Authorizer (LocalStack)
3. ✅ Extrai `accountId` da resposta do Lambda
4. ✅ Passa como `X-Account-Id` header para Spring Boot
5. ✅ Retorna resposta do Spring ao cliente

**Por que não é AWS API Gateway oficialmente?**
- LocalStack não simula API Gateway com Authorizer completo
- Simulator garante comportamento equivalente localmente
- **Em produção (AWS real)**: Usa AWS API Gateway oficial com Lambda Authorizer nativo

### Concorrência

**LockService**: 
- Atomic lock acquisition (PutItem + ConditionExpression)
- Retry automático (5 tentativas × 100ms, 200ms, 400ms, 800ms, 1600ms)
- LIFO release (evita deadlock)
- TTL auto-cleanup (30s)

**DynamoDB Conditional Update**:
- `ConditionExpression: "disponivel >= :val"`
- Garante atomicidade da reserva

---

## Como Rodar

### Pré-requisitos

- Docker com Docker Compose V2
- Java 21
- Maven 3.9+
- Terraform
- `curl` e `jq`

> Não são necessárias credenciais AWS reais para execução local.
> O LocalStack utiliza as credenciais fictícias `test/test`.

### 1. Configurar o ambiente local

O arquivo `.env.example` contém o modelo das variáveis necessárias para executar o projeto.

Após clonar o repositório, crie o arquivo local:

```bash
cp .env.example .env.local
```

O `.env.example` é versionado. O `.env.local` é específico de cada ambiente e não é enviado ao Git.

Para o ambiente local padrão, os valores fornecidos no `.env.example` já estão preparados para LocalStack.

### 2. Iniciar a plataforma

Execute:

```bash
./QUICKSTART.sh
```

O `QUICKSTART.sh` utiliza `scripts/start-local.sh`, responsável por:

- subir LocalStack, DynamoDB Local e API Gateway Simulator;
- configurar a rede Docker;
- aplicar a infraestrutura Terraform;
- iniciar Authorization Service;
- iniciar Limits Service;
- iniciar Accounting Service;
- aguardar os health checks dos serviços.

A infraestrutura AWS simulada é declarada pelo Terraform em `infra/terraform/`.

### 3. Endpoints locais

| Componente | Endereço |
|---|---|
| API Gateway Simulator | `http://localhost:8081` |
| Authorization Service | `http://localhost:8080` |
| Limits Service | `http://localhost:8082` |
| Accounting Service | `http://localhost:8083` |
| LocalStack | `http://localhost:4566` |
| DynamoDB Local | `http://localhost:8000` |

As chamadas externas da API devem utilizar a porta **8081**, passando pelo API Gateway Simulator e pelo fluxo de autorização.

### 4. Testar uma autorização

```bash
curl -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: teste-1" \
  -d '{
    "idConta": "ACC-001",
    "valor": 100.00,
    "moeda": "BRL",
    "tipoOperacao": "DEBITO"
  }' | jq .
```

Resposta esperada: **HTTP 201 Created**.

### 5. Validar a infraestrutura

```bash
./scripts/validate-infra.sh
```

Para validar especificamente o fluxo EventBridge → SQS FIFO:

```bash
./scripts/validate-eventbridge-sqs.sh
```

### 6. Load test

```bash
./scripts/load-test.sh
```

### 7. Parar o ambiente

```bash
docker compose down -v
```

---

### Arquitetura em Tempo de Execução

```
┌─────────────────────────────────────────────────────────────┐
│ CLIENTE (curl, Postman, navegador)                          │
│ localhost:8081 (API Gateway Simulator - Python FastAPI)     │
└────────────────────┬────────────────────────────────────────┘
                     │ HTTP/POST
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ [1] Lambda Authorizer (LocalStack)                          │
│     - Valida JWT token                                       │
│     - Retorna accountId                                      │
└────────────────────┬────────────────────────────────────────┘
                     │ X-Account-Id header
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ [2] Spring Boot API (localhost:8080)                        │
│     - RateLimitInterceptor (5 TPS)                          │
│     - LockService (5 retry × exponential backoff)           │
│     - AuthorizeUseCase (valida limite → reserva → persiste) │
└────────────────────┬────────────────────────────────────────┘
                     │
        ┌────────────┴────────────┐
        ↓                         ↓
    [3] DynamoDB            [4] EventBridge
    (Locks, Limits,         (Publica evento)
     Authorizations)            │
                                ↓
                             [5] SQS
                    (AccountingEventListener
                      registra lançamento)
```

---

## 🛠️ API Gateway Simulator (Local Only)

Simulador em **Python/FastAPI** que replica o comportamento do AWS API Gateway com Lambda Authorizer.

**Localização:** `/infra/api-gateway-simulator/`

**O que faz:**
1. Recebe POST em `http://localhost:8081/v1/contratos/{id}/autorizacoes`
2. Valida `Authorization` header (obrigatório)
3. Invoca Lambda Authorizer (LocalStack)
4. Extrai `accountId` da resposta
5. Forward request para Spring Boot (8080) com `X-Account-Id` header
6. Retorna resposta ao cliente

**Porta:** 8081 (não conflita com Spring 8080)

**Logs:** `docker logs api-gateway-simulator`

**Por que existe o Simulator?**

Durante a execução local, a combinação utilizada pelo projeto — **API Gateway REST + Lambda Authorizer + integração HTTP_PROXY** — não reproduziu no LocalStack o roteamento necessário para executar o fluxo completo da aplicação.

Por isso, o Simulator funciona como um adaptador **exclusivamente local**, permitindo validar o fluxo de autenticação e roteamento de ponta a ponta.

Ele **não substitui a arquitetura AWS**: o Terraform continua declarando o API Gateway REST e o Lambda Authorizer. Em uma implantação na AWS real, esses serviços são utilizados nativamente.

---

## ✅ Validar EventBridge → SQS

O **EventBridge → SQS Target é configurado automaticamente pelo Terraform** durante a execução de `./scripts/start-local.sh`.

**Se precisar reconfigurar manualmente:**

```bash
./scripts/validate-eventbridge-sqs.sh
```

**Quando você fazer uma transação:**
```bash
curl -s -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: test-$(date +%s%N)" \
  -d '{"idConta":"ACC-001","valor":100.00,"moeda":"BRL","tipoOperacao":"DEBITO"}' | jq .
```

**Nos LOGS do Spring Boot você verá:**
```
📡 Evento publicado (CloudEvents válido): type=TransacaoAutorizada
📨 Recebido evento SQS
💰 Processando transação: id=..., valor=100.00
✅ Evento processado com sucesso
```

**Fluxo completo:**
1. ✅ EventBridge publica evento
2. ✅ SQS recebe mensagem (target configurado pelo Terraform)
3. ✅ Spring consome via @SqsListener
4. ✅ AccountingEventListener processa contabilização
5. ✅ Retorna 201 ao cliente

---

### Troubleshooting

| Problema | Solução |
|----------|---------|
| `docker compose: command not found` | Instalar [Docker Desktop](https://www.docker.com/products/docker-desktop) |
| `Port 8081 already in use` | `lsof -i :8081` → `kill -9 <PID>` |
| `Lambda Authorizer fails` | Esperar LocalStack pronto (~5s) |
| `Rate limit 429` | Esperado! Limite é 5 TPS. Ver `application-local.yml` |
| `Testes falham no Codespace` | Rodar `./scripts/start-local.sh` primeiro |

---

### Modo Manual (Alternativa - Não recomendado)

Se preferir iniciar cada componente manualmente:

```bash
# Terminal 1: Infraestrutura
docker compose up -d localstack dynamodb-local api-gateway-simulator

# Terminal 2: Terraform
cd infra/terraform
terraform init
terraform apply -var-file=local.tfvars

# Terminal 3: Spring Boot
mvn clean compile spring-boot:run

# Terminal 4: Testes
curl http://localhost:8081/v1/contratos/CONTA-001/autorizacoes ...
```

---

## Estrutura do Código

```
challenge-pod99/
├── src/main/java/com/pod99/
│   ├── authorization/         (BC 1: Autorização)
│   │   ├── domain/
│   │   │   ├── Authorization.java (Agregado)
│   │   │   ├── AuthorizationRepository.java (Port)
│   │   │   └── TransacaoAutorizadaEvent.java
│   │   ├── application/
│   │   │   └── AuthorizeTransactionUseCase.java
│   │   └── infrastructure/
│   │       ├── AuthorizationController.java
│   │       └── DynamoDBAuthorizationRepository.java
│   │
│   ├── limits/                (BC 2: Limites)
│   │   ├── domain/
│   │   │   ├── Limit.java (Agregado)
│   │   │   └── LimitRepository.java (Port)
│   │   ├── application/
│   │   │   └── ReserveLimitUseCase.java
│   │   └── infrastructure/
│   │       └── DynamoDBLimitRepository.java
│   │
│   ├── accounting/            (BC 3: Contabilidade)
│   │   ├── domain/
│   │   │   ├── AccountingEntry.java (Agregado)
│   │   │   └── AccountingRepository.java (Port)
│   │   ├── application/
│   │   │   └── RecordTransactionUseCase.java
│   │   └── infrastructure/
│   │       ├── DynamoDBAccountingRepository.java
│   │       └── AccountingEventListener.java
│   │
│   ├── common/
│   │   ├── domain/
│   │   │   └── DomainEvent.java (Base para eventos)
│   │   └── exception/
│   │       ├── InsufficientLimitException.java
│   │       ├── LockAcquisitionException.java
│   │       └── IdempotencyException.java
│   │
│   └── config/
│       ├── LockService.java (Distributed locking)
│       ├── EventBridgePublisher.java (Event publishing)
│       └── AwsConfig.java (AWS SDK config)
│
├── src/test/java/com/pod99/ (testes unitários)
├── src/main/resources/
│   ├── application.yml (config Spring)
│   └── logback-spring.xml (JSON logging)
├── infra/
│   ├── docker-compose.yml (orquestra tudo)
│   └── terraform/ (IaC - AWS)
├── docs/
│   ├── ARCHITECTURE.md (design doc)
│   ├── ADR-001-distributed-locking.md
│   ├── ADR-002-event-driven.md
│   └── openapi.yaml (contrato API)
└── README.md (este arquivo)
```

---

## Padrões Implementados

### SOLID
- ✅ **S**ingle Responsibility: Authorization, Limits, Accounting separados
- ✅ **O**pen/Closed: EventBridgePublisher permite novos listeners
- ✅ **L**iskov: AuthorizationRepository = interface (DynamoDB é implementação)
- ✅ **I**nterface Segregation: Métodos específicos por contexto
- ✅ **D**ependency Inversion: UseCase → Repository (não → implementação concreta)

### Clean Architecture
- ✅ Domain (regras de negócio) isolado
- ✅ Application (UseCases) independente de infraestrutura
- ✅ Infrastructure (HTTP, BD, eventos) desacoplado

### DDD (Domain-Driven Design)
- ✅ Bounded Contexts: 3 contextos bem definidos
- ✅ Agregados: Authorization, Limit, AccountingEntry
- ✅ Value Objects: AuthorizationStatus
- ✅ Domain Events: TransacaoAutorizadaEvent
- ✅ Linguagem Ubíqua: "autorizar", "limite", "reservar", "lançamento"

### Resilience Patterns
- ✅ Distributed Lock: LockService (retry + LIFO)
- ✅ Circuit Breaker: (via Spring Boot Actuator)
- ✅ Dead Letter Queue: SQS DLQ para falhas
- ✅ Idempotência: Idempotency-Key + event_id

---

## Testes

```bash
# Rodar testes unitários
mvn test

# Com coverage
mvn test jacoco:report
```

**Coverage alvo**: 80%+

**Testes inclusos**:
- Authorization: happy path, limite insuficiente, validação
- LockService: aquisição, retry, release, deadlock prevention
- DynamoDB: Conditional Update, race condition

---

## API Contracts

### Endpoint

**POST** `/v1/contratos/{id_contrato}/autorizacoes`

**Headers obrigatórios**:
- `Idempotency-Key: <UUID>` (RFC 7231)
- `Content-Type: application/json`

**Request**:
```json
{
  "idConta": "ACC-001",
  "valor": 100.00,
  "moeda": "BRL",
  "tipoOperacao": "DEBITO",
  "id_estabelecimento": "EST-123",  // opcional
  "metadata": {}  // opcional
}
```

**Response (201 Created)**:
```json
{
  "id_autorizacao": "AUTH-uuid",
  "saldo_reservado": 99900.00,
  "correlation_id": "trace-uuid",
  "status": "APPROVED"
}
```

**Error Responses**:

| HTTP | Caso | Body |
|------|------|------|
| 400 | Validação (formato) | `{error_code: "VALIDATION_ERROR", message: "..."}` |
| 402 | Limite insuficiente | `{error_code: "INSUFFICIENT_LIMIT", message: "..."}` |
| 409 | Race condition | `{error_code: "CONFLICT", message: "..."}` |
| 429 | Rate limit | `{error_code: "TOO_MANY_REQUESTS", message: "..."}` |
| 503 | Serviço indisponível | `{error_code: "SERVICE_UNAVAILABLE", message: "..."}` |

---

## Escalabilidade

### Volume Esperado
- **5.000 TPS** (pico)
- **80 milhões** de contas ativas
- **3-5 contratos** por conta (em média)
- **Latência p99**: < 100ms

### Como Escala

**DynamoDB**:
- Mode PAY_PER_REQUEST (autoscale)
- Particionamento por `id_contrato` (hash key)
- Resolves hotspot via sharding

**EventBridge/SQS**:
- Escalabilidade ilimitada (AWS gerencia)
- Multiple consumers paralelos
- Auto-scaling de processamento

**Lambda** (produção):
- Reserved concurrency: 1.000
- Suporta 200-300 TPS por instância
- 1.000 × 200 = 200k+ TPS teórico

---

## Decisões Arquiteturais

Por que X ao invés de Y?

| Decisão | Escolhida | Rejeitada | Motivo |
|---------|-----------|-----------|--------|
| Lock | LockService (DynamoDB) | Conditional Update simples | Retry automático + resiliência |
| Event Broker | EventBridge + SQS | Kafka / SNS | Serverless, fan-out nativo, simples |
| Database | DynamoDB | Aurora SQL | Serverless, escalável, TTL |

→ Veja `docs/ADR-*.md` para análise completa de alternativas.

---

## Logging Estruturado

**MDC Context** (rastreamento):
- `X-Correlation-ID`: ponta-a-ponta (gerado no controller)
- `X-Trace-ID`: W3C Trace Context (distribuído)

**Output JSON** (estruturado):
```json
{
  "@timestamp": "2026-09-11T17:30:00Z",
  "level": "INFO",
  "app": "pod99-authorization",
  "correlation_id": "trace-123",
  "message": "✅ Autorização aprovada",
  "context": {
    "authorization_id": "AUTH-xyz",
    "contract_id": "CONTA-001",
    "amount": 100.00,
    "latency_ms": 45
  }
}
```

---

## 🌍 Deploy em AWS

**A estrutura acima foi desenvolvida para rodar tanto local quanto em AWS:**

### Local (Development)
- API Gateway Simulator (Python/FastAPI) → simula comportamento do AWS API Gateway
- Lambda Authorizer (LocalStack) → simula Lambda real
- DynamoDB (DynamoDB Local) → compatível com AWS DynamoDB

### AWS (Production)
- AWS API Gateway oficial com Lambda Authorizer nativo
- AWS Lambda (substituir Simulator com função real)
- AWS DynamoDB (sem alterações)
- AWS EventBridge + SQS (sem alterações)

**Mínimas mudanças necessárias:**
1. Substituir URL do Simulator pela URL do API Gateway oficial
2. Criar Lambda Authorizer em AWS (mesmo código que LocalStack)
3. Apontar Spring Boot para DynamoDB produção
4. Tudo mais funciona igual (mesmos códigos, mesma arquitetura)

| Problema | Solução |
|----------|---------|
| `docker compose: command not found` | Instalar Docker Desktop |
| `Connection refused: localhost:8000` | `docker compose up -d dynamodb-local` (aguardar DynamoDB) |
| `404 /v1/contratos/...` | Verificar `http://localhost:8080/actuator/health` |
| `Limite insuficiente` | Contrato CONTA-001 tem limite de 100.000 |
| `Idempotency-Key obrigatório` | Adicionar header na requisição |

---

## Referências

- [AWS Well-Architected Framework](https://aws.amazon.com/architecture/well-architected/)
- [Domain-Driven Design - Eric Evans](https://www.domainlanguage.com/ddd/)
- [Clean Architecture - Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [SOLID Principles](https://en.wikipedia.org/wiki/SOLID)
- [RFC 7807 - Problem Details for HTTP APIs](https://tools.ietf.org/html/rfc7807)
- [AWS DynamoDB Best Practices](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/best-practices.html)

---

## Autor

Danilo Camargo

**Data**: Setembro 2026
**Tempo investido**: ~4 dias (40 horas)

---

**Status**: ✅ Pronto para defesa técnica
