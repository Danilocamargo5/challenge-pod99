# POD99 — Arquitetura C4

Visualização em 4 níveis da arquitetura de sistema.

---

## Level 1: System Context

```mermaid
graph TB
    subgraph External["Sistemas Externos"]
        Core["Core Banking<br/>(Itaú Legacy)"]
        Cognito["AWS Cognito<br/>(OAuth2)"]
    end
    
    subgraph Users["Atores"]
        Client["Cliente Fintech<br/>(App Mobile/Web)"]
        Admin["Admin<br/>(Operações)"]
    end
    
    subgraph POD99["POD99 Platform"]
        API["REST API<br/>(API Gateway)"]
    end
    
    subgraph Consumers["Consumidores (Async)"]
        Accounting["Sistema de<br/>Contabilidade"]
        Fraud["Antifraude<br/>(Risk)"]
        Notifications["Notificações"]
    end
    
    Client -->|Autoriza transações| API
    Admin -->|Monitora| API
    Cognito -->|Valida OAuth2| API
    Core -->|Sincroniza limites| API
    API -->|Publica eventos| Accounting
    API -->|Publica eventos| Fraud
    API -->|Publica eventos| Notifications
    
    classDef external fill:#ffcccc
    classDef internal fill:#ccffcc
    classDef consumer fill:#ccccff
    
    class External,Cognito external
    class POD99 internal
    class Consumers consumer
```

---

## Level 2: Container

```mermaid
graph TB
    subgraph AWS["AWS Cloud"]
        subgraph APIGateway["API Gateway<br/>(REST + Rate Limiting)"]
            RateLimit["Rate Limit: 1k req/s<br/>Burst: 100 concurrent"]
            Auth["Lambda Authorizer<br/>(OAuth2 Validation)"]
        end
        
        subgraph Compute["Compute"]
            Lambda["Lambda Function<br/>(Spring Boot Native)"]
            ALB["Network Load Balancer<br/>(Optional for ECS)"]
        end
        
        subgraph DataLayer["Data Layer"]
            DDB["DynamoDB<br/>- limits<br/>- authorizations<br/>- accounting<br/>- locks"]
        end
        
        subgraph AsyncLayer["Event Processing"]
            EB["EventBridge<br/>(Routing Rules)"]
            SQS["SQS Queues<br/>- accounting<br/>- fraud<br/>- notifications"]
        end
        
        subgraph Monitoring["Observability"]
            CW["CloudWatch<br/>- Logs<br/>- Metrics<br/>- Alarms"]
            XRay["X-Ray<br/>(Distributed Tracing)"]
        end
    end
    
    subgraph External["External"]
        Cognito["Cognito<br/>(JWT Validation)"]
    end
    
    Client -->|POST /v1/contratos/.../autorizacoes<br/>Idempotency-Key| RateLimit
    RateLimit -->|Check Rate Limit| RateLimit
    RateLimit -->|Bearer Token| Auth
    Auth -->|Validate JWT| Cognito
    Auth -->|Forward| Lambda
    Lambda -->|Atomic Update| DDB
    Lambda -->|Publish Event| EB
    EB -->|Route| SQS
    Lambda -->|Emit Logs| CW
    Lambda -->|Trace| XRay
    
    classDef aws fill:#ff9900,color:#fff
    classDef data fill:#0099ff,color:#fff
    classDef event fill:#00cc00,color:#fff
    
    class AWS,APIGateway,Compute,DataLayer,AsyncLayer,Monitoring aws
    class DDB data
    class EB,SQS event
```

---

## Level 3: Component (Bounded Contexts)

```mermaid
graph TB
    subgraph API["API Layer"]
        Controller["AuthorizationController<br/>@PostMapping /v1/contratos/{id}/autorizacoes"]
    end
    
    subgraph Authorization["📌 Authorization Context"]
        AuthDomain["Domain<br/>- Authorization (Aggregate)<br/>- AuthorizationStatus<br/>- TransacaoAutorizadaEvent"]
        AuthApp["Application<br/>- AuthorizeTransactionUseCase<br/>- Request/Response DTOs"]
        AuthInfra["Infrastructure<br/>- DynamoDBAuthorizationRepository<br/>- EventBridgePublisher"]
    end
    
    subgraph Limits["📌 Limits Context"]
        LimitDomain["Domain<br/>- Limit (Aggregate)<br/>- LimitRepository (Port)"]
        LimitApp["Application<br/>- ReserveLimitUseCase"]
        LimitInfra["Infrastructure<br/>- DynamoDBLimitRepository"]
    end
    
    subgraph Accounting["📌 Accounting Context (Async)"]
        AccDomain["Domain<br/>- AccountingEntry (Aggregate)<br/>- AccountingRepository (Port)"]
        AccApp["Application<br/>- RecordTransactionUseCase"]
        AccListener["Infrastructure<br/>- AccountingEventListener (SQS)<br/>- DynamoDBAccountingRepository"]
    end
    
    subgraph Common["Common"]
        LockService["LockService<br/>(Distributed Locks)"]
        CloudEvents["CloudEventsValidator<br/>(Schema Validation)"]
    end
    
    Controller -->|inject| AuthApp
    AuthApp -->|uses| AuthDomain
    AuthDomain -->|implements| AuthInfra
    
    AuthApp -->|queries| LimitApp
    LimitApp -->|uses| LimitDomain
    LimitDomain -->|implements| LimitInfra
    
    AuthInfra -->|publishes| CloudEvents
    CloudEvents -->|validates schema| AuthInfra
    
    AuthApp -->|acquires| LockService
    
    AuthInfra -->|routes to| AccListener
    AccListener -->|uses| AccDomain
    AccApp -->|implements| AccInfra
    
    classDef context fill:#ffcc99
    classDef domain fill:#99ccff
    classDef app fill:#99ff99
    classDef infra fill:#ff99cc
    
    class Authorization,Limits,Accounting context
    class AuthDomain,LimitDomain,AccDomain domain
    class AuthApp,LimitApp,AccApp app
    class AuthInfra,LimitInfra,AccListener infra
```

---

## Level 4: Code (Class Diagram - Authorization Context)

```mermaid
classDiagram
    class Authorization {
        - idAutorizacao: String
        - idContrato: String
        - valor: BigDecimal
        - status: AuthorizationStatus
        + criar(...)
    }
    
    class AuthorizationStatus {
        APPROVED
        REJECTED
        PENDING
    }
    
    class TransacaoAutorizadaEvent {
        - event_id: String
        - event_type: String
        - occurred_at: Instant
        - saldo_reservado: BigDecimal
        + from(auth, traceId)
    }
    
    class AuthorizeTransactionUseCase {
        - authRepository: AuthorizationRepository
        - limitRepository: LimitRepository
        - eventPublisher: EventBridgePublisher
        + execute(idContrato, request, key)
    }
    
    class AuthorizationRepository {
        <<interface>>
        + save(auth)
        + findById(id)
        + findByIdempotencyKey(key)
    }
    
    class DynamoDBAuthorizationRepository {
        + save(auth)
        + findById(id)
        + findByIdempotencyKey(key)
    }
    
    class LockService {
        + acquireTransactionLocks(auth, contract)
        + releaseLocks(locks)
    }
    
    class CloudEventsValidator {
        + validate(eventJson)
        - validateRequiredFields(event)
        - validateFormats(event)
    }
    
    Authorization --> AuthorizationStatus
    Authorization --> TransacaoAutorizadaEvent
    AuthorizeTransactionUseCase --> AuthorizationRepository
    AuthorizeTransactionUseCase --> LockService
    AuthorizeTransactionUseCase --> CloudEventsValidator
    DynamoDBAuthorizationRepository --|> AuthorizationRepository
```

---

## Fluxo de Autorização (Sequência)

```mermaid
sequenceDiagram
    participant Client
    participant APIGateway as API Gateway
    participant Lambda as Lambda Authorizer
    participant AuthService as Authorization Service
    participant LockSvc as LockService
    participant DDB as DynamoDB
    participant EB as EventBridge
    participant SQS as SQS Queues
    
    Client->>APIGateway: POST /v1/contratos/{id}/autorizacoes<br/>Idempotency-Key: uuid-1<br/>Authorization: Bearer token
    
    APIGateway->>APIGateway: Rate Limit Check
    
    APIGateway->>Lambda: Validate Token
    Lambda->>Lambda: JWT Signature Validate
    Lambda-->>APIGateway: Allow + IAM Policy
    
    APIGateway->>AuthService: Forward Request
    
    AuthService->>AuthService: Extract Correlation ID
    AuthService->>DDB: Check Idempotency (findByKey)
    
    alt Idempotent (found)
        DDB-->>AuthService: Existing Authorization
        AuthService-->>APIGateway: 200 OK
    else First Time
        AuthService->>LockSvc: acquireTransactionLocks(auth, contract)
        LockSvc->>DDB: PutItem + ConditionExpression (lock_key)
        DDB-->>LockSvc: ✅ Lock Acquired
        
        AuthService->>DDB: Query Limit
        DDB-->>AuthService: Limit Available
        
        AuthService->>AuthService: Validate >= 0
        
        AuthService->>DDB: Atomic Update Limit<br/>Conditional Update: available >= value
        DDB-->>AuthService: ✅ Updated
        
        AuthService->>AuthService: Create Authorization
        AuthService->>DDB: Save Authorization
        DDB-->>AuthService: ✅ Saved
        
        AuthService->>AuthService: Create CloudEvent
        AuthService->>AuthService: Validate Schema
        AuthService->>EB: Publish Event
        EB-->>AuthService: ✅ Routed
        
        EB->>SQS: Fan-out to 3 queues
        SQS-->>EB: ✅ Queued
        
        LockSvc->>DDB: Delete Locks (LIFO order)
        DDB-->>LockSvc: ✅ Released
        
        AuthService-->>APIGateway: 201 Created
    end
    
    APIGateway-->>Client: HTTP 201/200
```

---

## Arquitetura de Persistência (Database-per-Context)

```
POD99 Platform
│
├─ Authorization Context
│  └─ pod99-authorizations (DynamoDB)
│     ├─ PK: id_autorizacao (String)
│     ├─ GSI: id_contrato (for queries by contract)
│     ├─ TTL: expiry_time (7 days)
│     └─ Replication: Multi-region optional
│
├─ Limits Context
│  └─ pod99-limits (DynamoDB)
│     ├─ PK: id_contrato (String)
│     ├─ Attributes: limite, disponivel, reservado, version
│     └─ Optimistic Locking: version field
│
├─ Accounting Context (Async)
│  └─ pod99-accounting (DynamoDB)
│     ├─ PK: event_id (String)
│     ├─ GSI: id_autorizacao (for queries)
│     ├─ TTL: expiry_time (30 days for audit)
│     └─ Immutable: records never updated
│
└─ Infrastructure
   └─ pod99-locks (DynamoDB)
      ├─ PK: lock_key (String)
      ├─ TTL: expiry_time (30 seconds, auto cleanup)
      └─ Transient: data not persisted long-term
```

---

## Escalabilidade: Throughput by Layer

```
5.000 TPS (Target)
│
├─ API Gateway
│  ├─ Rate Limit: 1.000 req/s (configurable)
│  ├─ Burst: 100 concurrent
│  └─ Exceeds? → HTTP 429 Too Many Requests
│
├─ Authorization Service (Compute)
│  ├─ Lambda: 1.000 reserved concurrency
│  ├─ ECS Fargate: 100 tasks × 50 TPS = 5.000 TPS
│  └─ Scale strategy: Target tracking (70% CPU)
│
├─ DynamoDB (Data Layer)
│  ├─ Billing Mode: PAY_PER_REQUEST (auto-scale)
│  ├─ Partition Key: id_contrato (avoid hotspot)
│  ├─ Write Capacity: ~15.000 WCU/s (5k × 3 operations)
│  └─ Scaling: Automatic (milliseconds to scale)
│
└─ EventBridge + SQS (Events)
   ├─ EventBridge: Unlimited
   ├─ SQS: 300.000 messages/s per queue
   └─ Consumers: Auto-scale pollers
```

---

## Resiliência & Fallback

```mermaid
graph TB
    A["Request"]
    
    A -->|Healthy| B["Process"]
    A -->|Rate Limited| C["HTTP 429"]
    A -->|Auth Failed| D["HTTP 401"]
    
    B -->|DynamoDB Timeout| E["Retry 3×"]
    B -->|Lock Acquisition Fails| F["HTTP 409"]
    B -->|Insufficient Limit| G["HTTP 402"]
    
    E -->|Still Failing| H["HTTP 503<br/>Circuit Breaker"]
    
    B -->|EventBridge Fails| I["Event to DLQ<br/>Retry Policy"]
    
    I -->|Max Retries| J["CloudWatch Alert<br/>Manual Investigation"]
    
    classDef success fill:#99ff99
    classDef error fill:#ff9999
    classDef warning fill:#ffff99
    
    class B success
    class C,D,F,G,H error
    class I,J warning
```
