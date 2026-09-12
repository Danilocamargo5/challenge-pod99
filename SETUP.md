# 🚀 POD99 - Setup Completo via Terraform

## 📋 Resumo

Infraestrutura 100% automatizada com Terraform. Tudo sobe com 3 comandos!

---

## 🔧 **ORDEM EXATA PARA AMANHÃ:**

### **Terminal 1: Subir LocalStack**
```bash
docker-compose up
```

**Aguardar até ver:**
```
✅ LocalStack pronto em http://localhost:4566
✅ DynamoDB pronto em http://localhost:8000
```

---

### **Terminal 2: Deploy Terraform**

Aguarde o Terminal 1 estar pronto, depois:

```bash
cd infra/terraform

# Inicializar Terraform (primeira vez)
terraform init

# Aplicar configuração (cria TUDO)
terraform apply -var-file=local.tfvars
```

**O que Terraform vai criar:**

```
✅ 5 tabelas DynamoDB:
   - pod99-limits (com 300 registros)
   - pod99-authorizations
   - pod99-accounting
   - pod99-locks
   - pod99-rate-limit

✅ 2 filas SQS:
   - pod99-accounting-queue.fifo
   - pod99-accounting-dlq.fifo

✅ EventBridge:
   - Rule: pod99-transacao-autorizada-rule
   - Target: SQS queue

✅ API Gateway:
   - HTTP API
   - Stage: local
   - Lambda Authorizer (POST /v1/contratos/authorize)
   - Routes: GET /health, POST /v1/contratos/{id}/autorizacoes (COM AUTENTICAÇÃO!)

✅ CloudWatch Logs:
   - Log group para API Gateway
```

**Aguardar até ver:**
```
Apply complete! Resources: XX added

Outputs:
api_gateway_invoke_url = http://...
lambda_authorizer_uri = http://...
test_data_info = {
  accounts  = "100 (ACC-001 até ACC-100)"
  contracts = "300 (CONTA-001 até CONTA-300)"
  ...
}
```

---

### **Terminal 3: Subir App Spring Boot**

Aguarde o Terminal 2 estar pronto, depois:

```bash
git pull origin develop

./mvnw spring-boot:run
```

**Aguardar até ver:**
```
✅ Tomcat started on port 8080
✅ Spring Boot application started
✅ JwtValidator loaded
✅ AuthorizationController loaded
```

---

### **Terminal 4: Testar**

Aguarde o Terminal 3 estar pronto, depois:

---

## 🧪 Testes

### 1️⃣ Teste LOCAL (direto no app, sem API Gateway)

**Token válido (ACC-001):**
```bash
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: test-123" \
  -d '{
    "idConta": "ACC-001",
    "valor": 100.00,
    "moeda": "BRL",
    "tipoOperacao": "DEBITO"
  }'

# Resposta esperada: 201 Created
# {
#   "id_autorizacao": "UUID",
#   "saldo_reservado": 50900.00,
#   "repetition": false,
#   "timestamp": "ISO 8601"
# }
```

**Token inválido:**
```bash
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Authorization: Bearer invalid-token" \
  -d '...'

# Resposta esperada: 401 Unauthorized
```

---

### 2️⃣ Teste Lambda Authorizer (endpoint /authorize)

**Validar token:**
```bash
curl -X POST http://localhost:8080/v1/contratos/authorize \
  -H "Content-Type: application/json" \
  -d '{
    "authorizationToken": "Bearer jwt-ACC-001",
    "methodArn": "arn:aws:execute-api:us-east-1:123456789012:api/stage/POST/endpoint"
  }'

# Resposta: 200 OK
# {
#   "principalId": "user-ACC-001",
#   "policyDocument": {
#     "Version": "2012-10-17",
#     "Statement": [
#       {
#         "Action": "execute:Invoke",
#         "Effect": "Allow",
#         "Resource": "arn:..."
#       }
#     ]
#   },
#   "context": {
#     "accountId": "ACC-001"
#   }
# }
```

---

### 3️⃣ Teste via API Gateway (LocalStack)

Primeiro, obter URL da API Gateway:
```bash
cd infra/terraform
terraform output api_gateway_invoke_url
# http://localhost:4566/restapis/API_ID/local/
```

**Com autenticação (token válido):**
```bash
curl -X POST http://APIGW_URL/v1/contratos/CONTA-001/autorizacoes \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: test-1" \
  -d '{"idConta": "ACC-001", "valor": 100, ...}'

# Response: 201 Created (API Gateway → Lambda Authorizer → App)
```

**Sem autenticação:**
```bash
curl -X POST http://APIGW_URL/v1/contratos/CONTA-001/autorizacoes \
  -d '...'

# Response: 401 Unauthorized (API Gateway bloqueia)
```

**Token inválido:**
```bash
curl -X POST http://APIGW_URL/v1/contratos/CONTA-001/autorizacoes \
  -H "Authorization: Bearer invalid-token" \
  -d '...'

# Response: 403 Forbidden (Lambda Authorizer retorna Deny)
```

---

## 📊 Dados de Teste

**100 contas × 3 contratos = 300 limites**

```
ACC-001:
  └─ CONTA-001: limite 51.000,00
  └─ CONTA-002: limite 51.000,00
  └─ CONTA-003: limite 51.000,00

ACC-002:
  └─ CONTA-004: limite 52.000,00
  └─ CONTA-005: limite 52.000,00
  └─ CONTA-006: limite 52.000,00

...

ACC-100:
  └─ CONTA-298: limite 150.000,00
  └─ CONTA-299: limite 150.000,00
  └─ CONTA-300: limite 150.000,00
```

---

## 🔄 Fluxo de Requisição Completo

```
1. curl → POST /v1/contratos/CONTA-001/autorizacoes
   Authorization: Bearer jwt-ACC-001
   ↓
2. API Gateway (LocalStack 4566)
   - Intercepta requisição
   - Extrai header Authorization
   ↓
3. Lambda Authorizer
   - Chama: POST /v1/contratos/authorize
   - JwtValidator valida JWT
   - Retorna IAM Policy (Allow/Deny)
   ↓
4. Se Allow → API Gateway passa pra app
   ↓
5. App Spring Boot (8080)
   - RequestContext configura account ID
   - AuthorizationController processa
   - AuthorizeTransactionUseCase:
     * Valida AccountId (ACC-XXX)
     * Valida ContractId (CONTA-XXX)
     * Valida relação Account-Contract
     * Adquire locks
     * Verifica idempotência
     * Valida limite
     * Reserva valor
     * Publica evento → EventBridge
   ↓
6. EventBridge
   - Recebe TransacaoAutorizada
   - Envia pra SQS accounting-queue
   ↓
7. AccountingEventListener (async)
   - Processa evento
   - Contabiliza transação
   ↓
8. Retorna 201 Created (ou 200 OK se repetição)
```

---

## 🛑 Se algo der errado:

### LocalStack não sobe:
```bash
docker-compose down
docker-compose up
```

### Terraform falha:
```bash
terraform plan -var-file=local.tfvars
```

### App não inicia:
```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

---

## 📝 Validações

### AccountId (Value Object)
- ✅ Formato: ACC-XXX
- ✅ Não vazio
- ✅ Imutável

### ContractId (Value Object)
- ✅ Formato: CONTA-XXX
- ✅ Não vazio
- ✅ Imutável

### AccountContractValidator
- ✅ Contrato existe?
- ✅ Contrato pertence à conta?

### JwtValidator
- ✅ Formato: Bearer jwt-ACC-001
- ✅ Extrai account ID
- ⚠️ STUB: não valida assinatura (para produção, usar Auth0/Cognito/Keycloak)

---

## 🎯 Checklist de Testes

- [ ] LocalStack up
- [ ] Terraform apply OK (0 errors)
- [ ] 5 tabelas criadas
- [ ] 300 registros em pod99-limits
- [ ] SQS queues criadas
- [ ] EventBridge rule ativa
- [ ] API Gateway criada com Lambda Authorizer
- [ ] App Spring Boot up
- [ ] Teste 201 Created (token válido)
- [ ] Teste 200 OK (idempotência)
- [ ] Teste 402 (limite insuficiente)
- [ ] Teste 401 (sem token)
- [ ] Teste 403 (token inválido)
- [ ] Load test 5k TPS

---

**PRONTO! Amanhã é só testar!** 🚀
