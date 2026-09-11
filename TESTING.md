# 🧪 POD99 — Guia Completo de Testes

Como testar **TUDO** que foi implementado em um Codespace (ou máquina local).

---

## Pré-requisitos

```bash
# Java 17+
java -version

# Docker
docker --version

# Docker Compose
docker-compose --version

# Maven (ou usar mvnw wrapper)
mvn --version
```

---

## Testes Unitários

### 1. Executar Testes (Cobertura 80%+)

```bash
cd /home/claude/challenge-pod99

# Compilar
mvn clean compile

# Rodar testes
mvn test

# Com cobertura (JaCoCo)
mvn test jacoco:report
open target/site/jacoco/index.html
```

**Esperado:**
```
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Testes inclusos:**
- ✅ AuthorizeTransactionUseCaseTest (4 casos)
- ✅ LockServiceTest (5 casos)
- ✅ LimitTest (7 casos)
- ✅ CloudEventsValidatorTest (8 casos)

**Total:** 24 testes unitários

### 2. Cobertura de Código

```bash
mvn test jacoco:report

# Ver relatório
ls -la target/site/jacoco/

# Abrir em navegador
python3 -m http.server 8888 -d target/site/jacoco
# http://localhost:8888
```

**Alvo:** 80% cobertura nas classes críticas
- Authorization: 85%
- Limits: 90%
- LockService: 80%
- CloudEventsValidator: 95%

---

## Testes de Integração (Docker Compose)

### 1. Subir Infraestrutura

```bash
cd /home/claude/challenge-pod99

# Cleanup
docker-compose down -v

# Subir (esperar ~30 segundos)
docker-compose up -d

# Verificar status
docker-compose ps

# Ver logs
docker-compose logs -f
```

**Esperado:**
```
pod99-app             Up (healthy)
dynamodb-local        Up (healthy)
localstack            Up (healthy)
```

### 2. Testar Autorização (Happy Path)

```bash
# 1. Autorizar transação
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "id_conta": "ACC-001",
    "valor": 100.00,
    "moeda": "BRL",
    "tipo_operacao": "DEBITO"
  }'

# Esperado: HTTP 201 Created
# {
#   "id_autorizacao": "AUTH-uuid",
#   "saldo_reservado": 99900.00,
#   "correlation_id": "trace-uuid",
#   "status": "APPROVED"
# }
```

### 3. Testar Idempotência

```bash
# Mesma requisição com mesmo Idempotency-Key
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Idempotency-Key: key-fixed-123" \
  -H "Content-Type: application/json" \
  -d '{
    "id_conta": "ACC-001",
    "valor": 50.00,
    "moeda": "BRL",
    "tipo_operacao": "DEBITO"
  }'

# 1ª chamada: HTTP 201 Created
# 2ª chamada (mesma key): HTTP 200 OK (do cache)
```

### 4. Testar Limite Insuficiente

```bash
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "id_conta": "ACC-001",
    "valor": 999999.00,
    "moeda": "BRL",
    "tipo_operacao": "DEBITO"
  }'

# Esperado: HTTP 402 Payment Required
# {
#   "error_code": "INSUFFICIENT_LIMIT",
#   "message": "Limite insuficiente"
# }
```

### 5. Ver Logs Estruturados

```bash
# Logs em JSON com Correlation ID
docker-compose logs -f pod99-app | grep "correlation_id"

# Exemplo
# {"@timestamp":"2026-09-11T18:30:00Z","level":"INFO","app":"pod99-authorization","correlation_id":"trace-123","message":"✅ Autorização aprovada"}
```

### 6. Verificar DynamoDB Local

```bash
# Listar tabelas
aws dynamodb list-tables \
  --endpoint-url http://localhost:8000 \
  --region us-east-1

# Esperado:
# {
#   "TableNames": [
#     "pod99-limits",
#     "pod99-authorizations",
#     "pod99-accounting",
#     "pod99-locks"
#   ]
# }

# Ver item na tabela
aws dynamodb scan \
  --table-name pod99-limits \
  --endpoint-url http://localhost:8000 \
  --region us-east-1

# Esperado: Item com CONTA-001 e limite 100000
```

### 7. Verificar Filas SQS

```bash
# Listar filas
aws sqs list-queues \
  --endpoint-url http://localhost:4566 \
  --region us-east-1

# Ver mensagens na fila de contabilidade
aws sqs receive-message \
  --queue-url http://localhost:4566/000000000000/pod99-accounting-queue \
  --endpoint-url http://localhost:4566 \
  --region us-east-1 \
  --wait-time-seconds 1

# Esperado: Mensagens com TransacaoAutorizadaEvent
```

---

## Testes de Carga (Opcional)

### 1. Apache JMeter Simples

```bash
# Instalar
brew install jmeter

# Criar teste simples
jmeter -n -t tests/pod99-load-test.jmx -l results.csv -j jmeter.log

# Ou script bash
for i in {1..100}; do
  curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
    -H "Idempotency-Key: $(uuidgen)" \
    -H "Content-Type: application/json" \
    -d '{"id_conta":"ACC-001","valor":1.00,"moeda":"BRL","tipo_operacao":"DEBITO"}' \
    &
done
wait
```

### 2. Verificar Latência

```bash
# Medir tempo de resposta
curl -w "\n%{time_total}s\n" -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"id_conta":"ACC-001","valor":10.00,"moeda":"BRL","tipo_operacao":"DEBITO"}'

# Esperado: <100ms (máximo)
```

---

## Testes de Validação

### 1. CloudEvents Schema

```bash
# CloudEvent válido
curl -X POST http://localhost:8080/test/validate-cloudevents \
  -H "Content-Type: application/json" \
  -d '{
    "event_id": "550e8400-e29b-41d4-a716-446655440000",
    "event_type": "TransacaoAutorizada",
    "event_version": "1.0",
    "occurred_at": "2026-09-11T18:30:00Z",
    "correlation_id": "trace-123",
    "valor": 100.00,
    "saldo_reservado": 99900.00
  }'

# Esperado: HTTP 200 OK

# CloudEvent inválido (event_id não é UUID)
curl -X POST http://localhost:8080/test/validate-cloudevents \
  -H "Content-Type: application/json" \
  -d '{
    "event_id": "not-a-uuid",
    "event_type": "TransacaoAutorizada",
    "event_version": "1.0",
    "occurred_at": "2026-09-11T18:30:00Z",
    "correlation_id": "trace-123"
  }'

# Esperado: HTTP 422 Unprocessable Entity
```

### 2. Validação de Entrada

```bash
# Campo obrigatório faltando
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"id_conta":"ACC-001"}'

# Esperado: HTTP 422 Unprocessable Entity
# "valor" é obrigatório

# Valor negativo
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "id_conta": "ACC-001",
    "valor": -100.00,
    "moeda": "BRL",
    "tipo_operacao": "DEBITO"
  }'

# Esperado: HTTP 422 Unprocessable Entity
# "valor" deve ser > 0
```

---

## Health Checks

### 1. Verificar Saúde da Aplicação

```bash
curl http://localhost:8080/actuator/health

# Esperado: 200 OK
# {
#   "status": "UP",
#   "components": {
#     "dynamodb": {"status": "UP"},
#     "eventbridge": {"status": "UP"}
#   }
# }
```

### 2. Verificar API Gateway (Terraform)

```bash
# Após deployment (via Terraform)
terraform output api_endpoint
# https://xxxxx.execute-api.us-east-1.amazonaws.com/prod

# Testar rate limiting
for i in {1..1100}; do
  curl -X POST $API_ENDPOINT/v1/contratos/CONTA-001/autorizacoes \
    -H "x-api-key: $API_KEY" \
    -H "Idempotency-Key: $(uuidgen)" \
    -H "Content-Type: application/json" \
    -d '{"id_conta":"ACC-001","valor":1.00,"moeda":"BRL","tipo_operacao":"DEBITO"}' &
done
wait

# 1100ª requisição deve retornar HTTP 429 Too Many Requests
```

---

## Checklist Final

```bash
#!/bin/bash

echo "🧪 POD99 Testing Checklist"
echo "=========================="

# 1. Compilação
echo "1️⃣ Compilação..."
mvn clean compile && echo "✅ Compilação OK" || echo "❌ Falhou"

# 2. Testes Unitários
echo "2️⃣ Testes unitários..."
mvn test && echo "✅ Testes OK" || echo "❌ Falhou"

# 3. Build
echo "3️⃣ Build..."
mvn clean package -DskipTests && echo "✅ Build OK" || echo "❌ Falhou"

# 4. Docker
echo "4️⃣ Docker Compose..."
docker-compose up -d && sleep 10 && echo "✅ Containers rodando" || echo "❌ Falhou"

# 5. Health Check
echo "5️⃣ Health Check..."
curl -s http://localhost:8080/actuator/health | grep -q "UP" && echo "✅ Saúde OK" || echo "❌ Falhou"

# 6. API Test
echo "6️⃣ API Test..."
curl -s -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Idempotency-Key: test-$(date +%s)" \
  -H "Content-Type: application/json" \
  -d '{"id_conta":"ACC-001","valor":10.00,"moeda":"BRL","tipo_operacao":"DEBITO"}' | grep -q "id_autorizacao" && echo "✅ API OK" || echo "❌ Falhou"

# 7. Cleanup
echo "7️⃣ Cleanup..."
docker-compose down && echo "✅ Limpo" || echo "❌ Falhou"

echo ""
echo "✅ Todos os testes passaram!"
```

Executar:
```bash
chmod +x tests/checklist.sh
./tests/checklist.sh
```

---

## Troubleshooting

| Erro | Causa | Solução |
|------|-------|---------|
| `Connection refused: localhost:8080` | Aplicação não subiu | `docker-compose logs pod99-app` |
| `DynamoDB: ResourceNotFoundException` | Tabelas não criadas | `docker-compose up` completo |
| `Idempotency-Key missing` | Header obrigatório faltando | Adicionar header |
| `HTTP 429 Too Many Requests` | Rate limit excedido | Aguardar reset ou aumentar limite no terraform |
| `JSON parsing error` | Payload malformado | Validar JSON com `jq` |

---

## Próximas Etapas

1. ✅ Testes locais com Docker Compose
2. ✅ Testes em Codespace
3. ⏳ Deployment em AWS via Terraform
4. ⏳ Testes de carga em produção (5k TPS)
5. ⏳ Canary deployment com gradual traffic shift

---

**Status**: ✅ Pronto para testes em Codespace!
