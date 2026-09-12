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
   - Routes: GET /health, POST /v1/contratos/{id}/autorizacoes

✅ CloudWatch Logs:
   - Log group para API Gateway
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
git pull origin develop

./mvnw spring-boot:run
```

**Aguardar até ver:**
```
✅ Tomcat started on port 8080
✅ Spring Boot application started
✅ RateLimitInterceptor loaded
✅ JwtAuthenticationFilter loaded
```

---

### **Terminal 4: Testar**

Aguarde o Terminal 3 estar pronto, depois:

```bash
# Teste básico
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
#   "saldo_reservado": 51000.00,  (51000 - 100)
#   "correlation_id": "UUID",
#   "status": "APPROVED",
#   "timestamp": "ISO 8601"
# }
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

## 🧪 Teste Local vs API Gateway

### Teste LOCAL (direto no App, sem API Gateway):
```bash
curl http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Authorization: Bearer jwt-ACC-001" \
  ...
```

### Teste via API Gateway (LocalStack):
```bash
# URL do API Gateway aparece no output do Terraform
curl http://API_GATEWAY_URL/v1/contratos/CONTA-001/autorizacoes \
  -H "Authorization: Bearer jwt-ACC-001" \
  ...
```

---

## 🔄 Fluxo de Requisição

```
1. curl → POST /v1/contratos/CONTA-001/autorizacoes
   ↓
2. JwtAuthenticationFilter (local)
   - Extrai: Authorization: Bearer jwt-ACC-001
   - Valida: formato "Bearer ..."
   - Extrai account ID: ACC-001
   - Adiciona no request
   ↓
3. AuthorizationController
   - Cria: AccountId("ACC-001") ✅ formato válido
   - Cria: ContractId("CONTA-001") ✅ formato válido
   - Valida: CONTA-001 ∈ ACC-001? ✅ SIM
   ↓
4. AuthorizeTransactionUseCase
   - Adquire locks
   - Verifica idempotência
   - Valida limite
   - Reserva valor
   - Publica evento → EventBridge
   ↓
5. EventBridge
   - Recebe TransacaoAutorizada
   - Envia pra SQS accounting-queue
   ↓
6. AccountingEventListener (async)
   - Processa evento
   - Contabiliza transação
   ↓
7. Retorna 201 Created (ou 200 OK se repetição)
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

---

## 🛑 Se algo der errado:

### LocalStack não sobe:
```bash
# Check logs
docker logs challenge-pod99-localstack-1

# Restart
docker-compose down
docker-compose up
```

### Terraform falha:
```bash
# Ver erros detalhados
terraform apply -var-file=local.tfvars -var-file=debug

# Destruir e recomeçar (cuidado - deleta dados!)
terraform destroy -var-file=local.tfvars
terraform apply -var-file=local.tfvars
```

### App não inicia:
```bash
# Clean build
./mvnw clean package -DskipTests

# Run
./mvnw spring-boot:run
```

---

## 📊 Verificar Status

### DynamoDB (tabelas + dados):
```bash
awslocal dynamodb list-tables --region us-east-1
awslocal dynamodb scan --table-name pod99-limits --region us-east-1 | head -20
```

### SQS (filas):
```bash
awslocal sqs list-queues --region us-east-1
```

### API Gateway:
```bash
awslocal apigatewayv2 get-apis --region us-east-1
```

### Terraform (outputs):
```bash
cd infra/terraform
terraform output
```

---

## 🎯 Checklist de Testes

- [ ] LocalStack up
- [ ] Terraform apply OK (0 errors)
- [ ] 5 tabelas criadas
- [ ] 300 registros em pod99-limits
- [ ] SQS queues criadas
- [ ] EventBridge rule ativa
- [ ] API Gateway criada
- [ ] App Spring Boot up
- [ ] Teste 201 Created
- [ ] Teste 200 OK (idempotência)
- [ ] Teste 402 (limite insuficiente)
- [ ] Teste 403 (sem token)
- [ ] Load test 5k TPS

---

**PRONTO! Amanhã é só testar!** 🚀
