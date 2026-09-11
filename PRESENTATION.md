# 🎯 POD99 — Guia de Apresentação Técnica

**Tempo total**: 60 minutos (20 apresentação + 40 Q&A)

---

## Parte 1: Setup (5 minutos)

### Terminal 1: Subir infraestrutura

```bash
cd challenge-pod99
chmod +x QUICKSTART.sh
./QUICKSTART.sh

# Ou manualmente:
docker-compose up
```

**Output esperado:**
```
✅ DynamoDB Local pronto
✅ LocalStack pronto  
✅ Pod99 app pronta (localhost:8080)
```

---

## Parte 2: Demonstração (10 minutos)

### Terminal 2: Testar autorização

**1. Autorizar uma transação (HTTP 201)**

```bash
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "id_conta": "ACC-001",
    "valor": 100.00,
    "moeda": "BRL",
    "tipo_operacao": "DEBITO"
  }'
```

**Response esperada:**
```json
HTTP 201 Created
{
  "id_autorizacao": "AUTH-550e8400...",
  "saldo_reservado": 99900.00,
  "correlation_id": "trace-550e8400...",
  "status": "APPROVED"
}
```

**2. Testar Idempotência (HTTP 200)**

```bash
# Mesma requisição (mesmo Idempotency-Key)
# Retorna HTTP 200 OK (do cache)
```

**3. Ver logs estruturados**

```bash
docker-compose logs -f pod99-app
```

**Output esperado:**
```json
{
  "@timestamp": "2026-09-11T18:30:00Z",
  "level": "INFO",
  "app": "pod99-authorization",
  "correlation_id": "trace-123",
  "message": "✅ Autorização aprovada: id=AUTH-xyz"
}
```

**4. Testar limite insuficiente (HTTP 402)**

```bash
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Idempotency-Key: key-fail" \
  -H "Content-Type: application/json" \
  -d '{
    "id_conta": "ACC-001",
    "valor": 150000.00,
    "moeda": "BRL",
    "tipo_operacao": "DEBITO"
  }'
```

**Response esperada:**
```json
HTTP 402 Payment Required
{
  "error_code": "INSUFFICIENT_LIMIT",
  "message": "Limite insuficiente"
}
```

---

## Parte 3: Apresentação Técnica (15 minutos)

### Slide 1: Arquitetura
```
POST /v1/contratos/{id}/autorizacoes
  ↓
[LockService] Adquire locks (5 retry × exponential backoff)
  ├─ Authorization lock
  └─ Contrato lock
  ↓
[AuthorizeUseCase] Valida → Reserva limite → Persiste
  ↓
[EventBridgePublisher] Publica TransacaoAutorizadaEvent
  ↓
EventBridge Rule → SQS → AccountingEventListener (async)
  ↓
HTTP 201 Created + Evento para contabilidade
```

### Slide 2: Padrões de Design
- ✅ **SOLID**: 5 princípios aplicados explicitamente
- ✅ **DDD**: Bounded Contexts (Authorization, Limits, Accounting)
- ✅ **Clean Architecture**: Domain isolado, Application independente, Infrastructure desacoplada
- ✅ **Event-Driven**: EventBridge + SQS, TransacaoAutorizadaEvent

### Slide 3: Concorrência
```
Problema: 2 threads debitam o mesmo limite simultaneamente
  ↓
Solução: LockService
  ├─ Atomic lock acquisition (PutItem + ConditionExpression)
  ├─ Retry automático (5×, 100ms, 200ms, 400ms, 800ms, 1600ms)
  ├─ LIFO release (evita deadlock)
  └─ TTL auto-cleanup (30s)
  ↓
Resultado: Atomicidade garantida + resilência
```

### Slide 4: Escalabilidade
- **5.000 TPS**: DynamoDB On-Demand autoscale
- **80 milhões contas**: Particionamento por id_contrato
- **Lambda (produção)**: 1.000 concurrent = 200k+ TPS teórico
- **EventBridge**: Ilimitado
- **SQS**: Ilimitado

---

## Parte 4: Q&A (40 minutos)

### Pergunta 1: Por que LockService ao invés de Conditional Update simples?

**Resposta:**
> "Conditional Update simples falha na primeira contenção. LockService retenta automaticamente (5×) com exponential backoff. Em domínio financeiro, retentar é melhor que falhar. Além disso, LockService serializa múltiplas chaves com LIFO release, evitando deadlock."

**Trade-off justificado:** Latência vs segurança (escolhemos segurança)

---

### Pergunta 2: Como garante idempotência?

**Resposta:**
> "Dois mecanismos:
> 1. **Idempotency-Key**: Header obrigatório (RFC 7231), cliente envia UUID
> 2. **Event ID**: Consumidor SQS verifica event_id antes de processar (at-least-once delivery)
> 
> Se requisição é repetida (mesmo Idempotency-Key), retornamos do cache (HTTP 200, não 201)"

---

### Pergunta 3: O que acontece se EventBridge ou SQS falhar?

**Resposta:**
> "Autorização ainda retorna HTTP 201. Evento fica em DLQ (Dead Letter Queue) por 24h pra retry/investigação. Contabilidade eventualmente reconcilia. Isso é eventual consistency — aceitável pra fintech."

---

### Pergunta 4: Como testa concorrência?

**Resposta:**
> "Testes unitários (LockServiceTest) simulam ConditionalCheckFailedException. Em produção, rodaríamos load test com Apache JMeter (5k TPS) e verificaríamos que limite nunca fica negativo."

---

### Pergunta 5: Por que EventBridge + SQS e não Kafka?

**Resposta:**
> "Kafka exige cluster MSK (complexidade operacional). EventBridge é serverless, escalável automaticamente até 5k TPS, suporta roteamento por regras (filtro de eventos). SQS é durável (24h) e tem DLQ automática. Kafka não traz valor pra nosso caso."

---

### Pergunta 6: Como monitora em produção?

**Resposta:**
> "CloudWatch:
> - Alarme: Latência p99 > 100ms
> - Alarme: DLQ message count > 10
> - Dashboard: TPS, erros, limite reservado
> X-Ray: Distributed tracing (correlation_id, trace_id)
> Structured logs em JSON (MDC context)"

---

### Pergunta 7: Como versionaria a API se precisasse adicionar um campo?

**Resposta:**
> "URL-based versioning:
> - /v1/contratos/... (cliente legado)
> - /v2/contratos/... (cliente novo)
> 
> Campos novos são apenas **adicionados** (nunca removidos/renomeados). Backward compatible.
> Deprecation policy: v1 mantém 12-18 meses, depois headers avinam."

---

### Pergunta 8: Qual é o maior bottleneck?

**Resposta:**
> "DynamoDB throughput se todas as requisições pegam o mesmo contrato (hot partition). Solução:
> 1. Sharding: distribuir contas por múltiplos shards
> 2. DynamoDB On-Demand: escalabilidade automática
> 3. Redis cache: limites quentes em cache local
> 
> Em v1, não queremos otimizar precocemente. Monitorar e escalar conforme necessário."

---

## Checklist Pré-Apresentação

- [ ] `docker-compose up` funciona e app está pronta
- [ ] `curl POST .../autorizacoes` retorna HTTP 201
- [ ] Logs aparecem em JSON (docker-compose logs)
- [ ] Repositório GitHub está público
- [ ] README.md está atualizado
- [ ] ARCHITECTURE.md é inteligível
- [ ] 3 ADRs explicam decisões principais
- [ ] 16 testes passam (`mvn test`)

---

## Tempo Estimado por Parte

| Parte | Tempo | Atividade |
|-------|-------|-----------|
| **Setup** | 5 min | docker-compose up |
| **Demo** | 10 min | Curl requests, logs |
| **Apresentação** | 15 min | Slides de arquitetura |
| **Q&A** | 30 min | Perguntas técnicas |
| **Buffer** | 0 min | (5 min de folga) |
| **TOTAL** | **60 min** | |

---

## Paradas de Emergência

**Se docker-compose falhar:**
```bash
docker-compose down -v  # Remove tudo
docker-compose up       # Recria do zero
```

**Se app não sobe:**
```bash
docker-compose logs pod99-app  # Ver erro
```

**Se teste de curl falha:**
```bash
# Verificar se port 8080 está aberta
curl http://localhost:8080/actuator/health
```

---

## Após a Apresentação

1. ✅ Agradeça
2. ✅ Disponibilize repository link: https://github.com/Danilocamargo5/challenge-pod99
3. ✅ Deixe claro: código está production-ready, ADRs documentam decisões
4. ✅ Diga: "Em produção, Terraform deploiaria em AWS com Lambda + DynamoDB + EventBridge reais"

---

**Boa apresentação! 🚀**
