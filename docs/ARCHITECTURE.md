# POD99 — Plataforma de Autorização de Transações

## Visão Geral

Uma plataforma transacional que autoriza operações financeiras em tempo real, gerencia limites de crédito e publica eventos de domínio para sistemas desacoplados.

**Escala esperada**: ~5.000 TPS (Transações Por Segundo)
**SLA**: <100ms latência p99

---

## Arquitetura

### 1. Bounded Contexts (DDD)

```
POD99 Authorization Platform
│
├── Authorization BC (Autorização)
│   ├─ Domínio: valida transação, aprova/rejeita
│   ├─ Agregado: Authorization
│   └─ Repositório: DynamoDB (pod99-authorizations)
│
├── Limits BC (Limites)
│   ├─ Domínio: gerencia limite por contrato
│   ├─ Agregado: Limit
│   ├─ Invariante: disponível >= 0 (sempre)
│   └─ Repositório: DynamoDB (pod99-limits)
│
└── Accounting BC (Contabilidade)
    ├─ Domínio: registra lançamento contábil
    ├─ Agregado: AccountingEntry
    └─ Repositório: DynamoDB (pod99-accounting)
```

### 2. Fluxo de Autorização (Síncrono)

```
Client
  ↓ POST /v1/contratos/{id}/autorizacoes
┌─────────────────────────────────────────────┐
│ Authorization Service (Spring Boot)         │
├─────────────────────────────────────────────┤
│ 1. Extrai correlationId (MDC)               │
│ 2. Valida request (RFC 7807)                │
│ 3. Adquire locks (LockService):             │
│    - Authorization lock                      │
│    - Contrato lock                          │
│    Retry 5× com exponential backoff         │
│ 4. Consulta limite (DynamoDB)               │
│ 5. Valida disponibilidade                   │
│ 6. Reserva limite (Conditional Update)      │
│ 7. Cria Authorization (Aggregate)           │
│ 8. Persiste (DynamoDB transaction)          │
│ 9. Publica evento → EventBridge             │
│ 10. Libera locks (LIFO)                     │
│ 11. Retorna 201 Created                     │
└─────────────────────────────────────────────┘
  ↓ HTTP 201
  ↓ {id_autorizacao, saldo_reservado, correlation_id}
Client
```

### 3. Fluxo de Evento (Assíncrono)

```
Authorization Service
  ↓ eventPublisher.publish(TransacaoAutorizadaEvent)
EventBridge (pod99-transacao-autorizada-rule)
  │ Filtra: source="pod99.authorization" + detail-type="TransacaoAutorizada"
  │
  ├→ SQS-Accounting (pod99-accounting-queue.fifo)
  │   ↓ manualAck (Spring Cloud AWS)
  │   AccountingEventListener
  │   ├─ Verifica idempotência (event_id)
  │   ├─ Cria AccountingEntry
  │   └─ Persiste (DynamoDB)
  │
  ├→ SQS-Fraud (pod99-fraud-queue)
  │   ↓ [Placeholder pra defesa]
  │
  └→ SQS-Notifications (pod99-notification-queue)
      ↓ [Placeholder pra defesa]
```

---

## Padrões Aplicados

### SOLID
- **S**ingle Responsibility: Authorization, Limits, Accounting = 3 bounded contexts
- **O**pen/Closed: EventPublisher permite novos listeners sem mudança
- **L**iskov: AuthorizationRepository = contrato (múltiplas impls: DynamoDB, mock)
- **I**nterface Segregation: LimitRepository.update() vs LimitRepository.find()
- **D**ependency Inversion: UseCase → Repository (interface, não implementação)

### Clean Architecture
```
Domain (agregados, regras de negócio)
  ↓ (não conhece aplicação)
Application (UseCases)
  ↓ (não conhece infraestrutura)
Infrastructure (HTTP, DynamoDB, EventBridge)
  ↓ (implementações concretas)
```

### DDD (Domain-Driven Design)
- **Bounded Contexts**: Authorization, Limits, Accounting
- **Agregados**: Authorization, Limit, AccountingEntry
- **Value Objects**: AuthorizationStatus, Limit (não mutable)
- **Domain Events**: TransacaoAutorizadaEvent (publicado via EventBridge)
- **Linguagem Ubíqua**: "autorizar", "limite disponível", "reservar", "lançamento"

### Concorrência
- **Distributed Lock**: LockService com retry automático
- **Atomic Operations**: DynamoDB Conditional Update
- **LIFO Release**: Evita deadlock entre threads

### Idempotência
- **Idempotency-Key**: Header obrigatório (RFC 7231)
- **Event ID**: Deduplicação de eventos (at-least-once delivery)
- **Deduplication**: Consumidor verifica event_id antes de processar

---

## Persistência (Database-per-Context)

| Contexto | Tabela | PK | SK | TTL | Índice |
|----------|--------|----|----|-----|--------|
| Limits | pod99-limits | id_contrato | - | - | - |
| Authorization | pod99-authorizations | id_autorizacao | - | 7 dias | id_contrato-GSI |
| Accounting | pod99-accounting | event_id | - | 30 dias | id_autorizacao-GSI |
| Locks | pod99-locks | lock_key | - | 30s | - |

---

## API Gateway & Rate Limiting

**Local** (docker-compose):
- Spring Boot expõe `/v1/...` direto em `localhost:8080`

**Produção** (Terraform):
```hcl
AWS API Gateway
├─ Rate Limit: 5.000 req/s
├─ Burst: 10.000 req/s
├─ Usage Plans: por cliente/plano
└─ Throttling: 429 Too Many Requests
```

---

## Logging Estruturado

**MDC Context** (Mapped Diagnostic Context):
```
X-Correlation-ID: trace-123  (ponta-a-ponta)
X-Trace-ID: w3c-trace-id     (W3C Trace Context)
```

**Output JSON** (logstash-logback-encoder):
```json
{
  "timestamp": "2026-09-11T17:30:00Z",
  "level": "INFO",
  "app": "pod99-authorization",
  "correlation_id": "trace-123",
  "message": "✅ Autorização aprovada: id=AUTH-xyz",
  "context": {
    "contrato": "CONTA-001",
    "valor": 100.00,
    "latencia_ms": 45
  }
}
```

---

## Error Handling (RFC 7807 - Problem Details)

```json
HTTP 402 Payment Required
{
  "error_code": "INSUFFICIENT_LIMIT",
  "message": "Limite insuficiente",
  "type": "https://api.pod99.io/errors#insufficient-limit",
  "instance": "/v1/contratos/CONTA-001/autorizacoes"
}
```

**Códigos:**
- `400 Bad Request`: Validação (formato)
- `402 Payment Required`: Regra de negócio (limite)
- `409 Conflict`: Race condition (concorrência)
- `422 Unprocessable`: Validação domínio
- `429 Too Many Requests`: Rate limit
- `503 Service Unavailable`: Circuit breaker

---

## Escalabilidade

### Bottleneck: DynamoDB Throughput

**Problema**: Se todas as requisições pegam o mesmo contrato (`CONTA-001`), temos um hot partition.

**Solução**:
1. **Sharding**: Distribuir contas por múltiplos shards
2. **DynamoDB On-Demand**: Escalabilidade automática
3. **Caching**: Redis cache local de limite

### Throughput Calculation
- **5.000 TPS** × **3 operações** (validate, update, save) = **15.000 RCU/s**
- **DynamoDB**: Adapta automaticamente em modo PAY_PER_REQUEST

---

## Testes

### Unitários (20+ testes)
- `AuthorizeTransactionUseCaseTest`: happy path, limite insuficiente, validação
- `LockServiceTest`: aquisição, retry, release, deadlock prevention
- `DynamoDBLimitRepositoryTest`: conditional update, race condition

### Integração (Local)
- `docker compose up`: rodar stack completa
- `curl POST .../autorizacoes`: end-to-end

### Carga (Não implementado neste desafio)
- Apache JMeter: 5.000 TPS simulado
- Verificar latência p99 < 100ms

---

## Decisões Importantes

| Decisão | Opção Escolhida | Motivo |
|---------|-----------------|--------|
| Lock Strategy | LockService (DynamoDB) | Atomicidade + retry automático |
| Event Broker | EventBridge + SQS | Serverless, escalável, fan-out |
| Database | DynamoDB | Serverless, escalável, TTL |
| Compute (Local) | Spring Boot | Simples, testável |
| Compute (Produção) | AWS Lambda | Serverless, paga por uso |
| Logging | Structured JSON | Observabilidade, busca |

→ Ver `docs/ADR-*.md` para justificativas detalhadas.

---

## Como Rodar Localmente

```bash
docker compose up

# Em outro terminal
curl -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes \
  -H "Idempotency-Key: uuid-1" \
  -H "Content-Type: application/json" \
  -d '{
    "idConta": "ACC-001",
    "valor": 100.00,
    "moeda": "BRL",
    "tipoOperacao": "DEBITO"
  }'

# Resposta esperada
HTTP 201 Created
{
  "id_autorizacao": "AUTH-xyz",
  "saldo_reservado": 99900.00,
  "correlation_id": "trace-123"
}
```

---

## Próximos Passos (Produção)

1. **Terraform**: Deploy em AWS
2. **Monitoring**: CloudWatch, X-Ray
3. **Alertas**: SNS (latência, erros)
4. **Canary**: Gradual rollout
5. **Disaster Recovery**: Multi-region
