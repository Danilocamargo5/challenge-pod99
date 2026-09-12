# 🚀 POD99 - Setup Completo via Terraform

## 📋 Resumo

Infraestrutura **100% automatizada com Terraform**. LocalStack sobe VAZIO, Terraform cria TUDO!

---

## 🔧 **ORDEM EXATA:**

### **Terminal 1: Subir LocalStack (VAZIO!)**
```bash
cd ~/challenge-pod99
docker-compose up
```

**Aguardar até ver:**
```
✅ LocalStack listening on http://localhost:4566
✅ DynamoDB ready on http://localhost:8000
✅ Ready to accept connections
```

**⚠️ IMPORTANTE:** LocalStack sobe **SEM criar nada**! Terraform vai criar as tabelas.

---

### **Terminal 2: Deploy Terraform (CRIA TUDO!)**

Aguarde o Terminal 1 estar pronto, depois:

```bash
cd ~/challenge-pod99/infra/terraform

# Inicializar Terraform
terraform init

# Aplicar configuração (cria TUDO)
terraform apply -var-file=local.tfvars
```

Responder: `yes` quando pedir

**Terraform vai criar:**

```
✅ 5 tabelas DynamoDB:
   - pod99-local-limits (com 300 registros!)
   - pod99-local-authorizations
   - pod99-local-accounting
   - pod99-local-locks (com TTL 30s)
   - pod99-local-rate-limit (com TTL 1s)

✅ 2 filas SQS:
   - pod99-local-accounting-queue.fifo
   - pod99-local-accounting-dlq.fifo (Dead Letter Queue)

✅ EventBridge:
   - Rule: pod99-local-transacao-autorizada-rule
   - Target: SQS queue (com message group ID)

✅ API Gateway:
   - HTTP API: pod99-api-local
   - Stage: local
   - Lambda Authorizer: POST /v1/contratos/authorize
   - Routes: GET /health, POST /v1/contratos/{id}/autorizacoes (COM AUTH!)

✅ CloudWatch Logs:
   - Log group: /aws/apigateway/pod99-local
   - Retention: 7 dias
```

**Aguardar até ver:**
```
Apply complete! Resources: XX added

Outputs:
api_gateway_invoke_url = http://...
sqs_accounting_queue_url = http://...
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
cd ~/challenge-pod99

git pull origin develop

./mvnw spring-boot:run
```

**Aguardar até ver:**
```
✅ Tomcat started on port 8080
✅ Spring Boot application started
✅ JwtValidator loaded
✅ RateLimitInterceptor loaded
```

---

### **Terminal 4: Testar**

Aguarde o Terminal 3 estar pronto, depois copiar testes de `COPYPASTE-CODESPACE.md`

---

## 📊 Dados de Teste (Terraform cria automaticamente!)

**100 contas × 3 contratos = 300 limites**

```
ACC-001:
  ├─ CONTA-001: limite 51.000,00
  ├─ CONTA-002: limite 51.000,00
  └─ CONTA-003: limite 51.000,00

ACC-002:
  ├─ CONTA-004: limite 52.000,00
  ├─ CONTA-005: limite 52.000,00
  └─ CONTA-006: limite 52.000,00

...

ACC-100:
  ├─ CONTA-298: limite 150.000,00
  ├─ CONTA-299: limite 150.000,00
  └─ CONTA-300: limite 150.000,00
```

---

## 🔄 Fluxo de Requisição Completo

```
1. curl → POST /v1/contratos/CONTA-001/autorizacoes
   Authorization: Bearer jwt-ACC-001
   ↓
2. JwtAuthenticationFilter (local)
   - Valida Bearer token
   - Extrai account ID: ACC-001
   ↓
3. AuthorizationController
   - Cria AccountId (ACC-XXX) ✅
   - Cria ContractId (CONTA-XXX) ✅
   - Valida relação: CONTA-001 ∈ ACC-001? ✅
   ↓
4. AuthorizeTransactionUseCase
   - Adquire lock (DynamoDB)
   - Verifica idempotência
   - Valida limite
   - Reserva valor
   - Publica evento → EventBridge
   ↓
5. EventBridge → SQS
   - Envia pra fila accounting-queue
   ↓
6. AccountingEventListener (async)
   - Processa evento
   - Contabiliza transação
   ↓
7. Retorna 201 Created (ou 200 OK se repetição)
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
# Ver erro específico
```

### App não inicia:
```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

### Ver tabelas criadas:
```bash
aws dynamodb list-tables \
  --endpoint-url http://localhost:4566 \
  --region us-east-1
```

---

## 📋 Validações

### AccountId (Value Object)
- ✅ Formato: ACC-XXX
- ✅ Não vazio
- ✅ Imutável

### ContractId (Value Object)
- ✅ Formato: CONTA-XXX
- ✅ Não vazio
- ✅ Imutável

### AccountContractValidator
- ✅ Contrato existe no DynamoDB?
- ✅ Contrato pertence à conta?

### JwtValidator
- ✅ Formato: Bearer jwt-ACC-001
- ✅ Extrai account ID
- ⚠️ STUB: não valida assinatura (para produção, usar Auth0/Cognito/Keycloak)

---

## 🎯 Checklist Final

- [ ] LocalStack up (vazio)
- [ ] Terraform apply OK
- [ ] 5 tabelas criadas
- [ ] 300 registros populados
- [ ] SQS queues criadas
- [ ] EventBridge rule ativa
- [ ] API Gateway criada com Lambda Authorizer
- [ ] App Spring Boot up
- [ ] Teste 201 Created ✅
- [ ] Teste 200 OK (idempotência) ✅
- [ ] Teste 402 (limite insuficiente) ✅
- [ ] Teste 401 (sem token) ✅
- [ ] Teste 403 (token inválido) ✅

---

**PRONTO! Infra completa via Terraform!** 🚀
