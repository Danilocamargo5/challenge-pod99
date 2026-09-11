# POD99 — Plataforma de Autorização de Transações

[![Build](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/Danilocamargo5/challenge-pod99)
[![Java](https://img.shields.io/badge/java-17-blue)](https://www.oracle.com/java/technologies/javase/jdk17-archive.html)
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

- **Linguagem**: Java 17
- **Framework**: Spring Boot 3.1.5
- **Banco de Dados**: DynamoDB (AWS Local)
- **Message Broker**: EventBridge + SQS (LocalStack)
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

- Docker & Docker Compose
- Java 17
- Maven 3.9 (ou usar wrapper: `./mvnw`)

### Local (Recomendado)

**1. Subir infraestrutura**

```bash
docker-compose up
```

Aguarde logs:
```
✅ DynamoDB Local pronto
✅ LocalStack pronto
✅ Pod99 app pronto (localhost:8080)
```

**2. Em outro terminal, testar a API**

```bash
# Autorizar uma transação
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "id_conta": "ACC-001",
    "valor": 100.00,
    "moeda": "BRL",
    "tipo_operacao": "DEBITO"
  }'

# Resposta esperada
HTTP 201 Created
{
  "id_autorizacao": "AUTH-550e8400-e29b-41d4-a716-446655440000",
  "saldo_reservado": 99900.00,
  "correlation_id": "trace-550e8400-e29b-41d4-a716-446655440001",
  "status": "APPROVED"
}
```

**3. Ver logs estruturados**

```bash
docker-compose logs -f pod99-app

# Output esperado
✅ Autorização aprovada: id=AUTH-xyz
💰 Lançamento contábil registrado: evento=xyz, valor=100.00
```

**4. Testar idempotência**

```bash
# Mesma requisição (mesmo Idempotency-Key)
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Idempotency-Key: uuid-1" \
  -H "Content-Type: application/json" \
  -d '{
    "id_conta": "ACC-001",
    "valor": 100.00,
    "moeda": "BRL",
    "tipo_operacao": "DEBITO"
  }'

# HTTP 200 OK (retorna do cache, não 201)
```

**5. Parar infraestrutura**

```bash
docker-compose down
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
│   ├── localstack-init.sh (cria tabelas/filas)
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
  "id_conta": "ACC-001",
  "valor": 100.00,
  "moeda": "BRL",
  "tipo_operacao": "DEBITO",
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

## Troubleshooting

| Problema | Solução |
|----------|---------|
| `docker-compose: command not found` | Instalar Docker Desktop |
| `Connection refused: localhost:8000` | `docker-compose up` (aguardar DynamoDB) |
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
