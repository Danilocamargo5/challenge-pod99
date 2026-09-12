# ⚡ COPYPASTE DIRETO NO CODESPACE

## TERMINAL 1: LocalStack (LIMPO - SEM criar nada!)
```bash
cd ~/challenge-pod99
docker-compose up
```
Aguardar:
- ✅ localstack listening on http://localhost:4566
- ✅ dynamodb-local healthcheck passed
- ✅ Ready to accept connections

**⚠️ IMPORTANTE: LocalStack sobe VAZIO agora!**
**Terraform vai criar todas as tabelas e filas!**

---

## TERMINAL 2: Terraform (CRIA TUDO!)
```bash
cd ~/challenge-pod99/infra/terraform
terraform init
terraform apply -var-file=local.tfvars
```
Responder: `yes`

Aguardar:
- ✅ Apply complete! Resources: XX added
- ✅ Outputs: 
  - dynamodb_limits_table = pod99-local-limits
  - sqs_accounting_queue_url = ...
  - api_gateway_invoke_url = ...

**Terraform vai criar:**
- ✅ 5 tabelas DynamoDB
- ✅ 2 filas SQS (FIFO + DLQ)
- ✅ EventBridge rule
- ✅ 300 registros de teste (100 contas × 3 contratos)

---

## TERMINAL 3: Spring Boot
```bash
cd ~/challenge-pod99
git pull origin develop
./mvnw spring-boot:run
```
Aguardar:
- ✅ Tomcat started on port 8080
- ✅ Started Pod99Application
- ✅ RateLimitInterceptor loaded
- ✅ JwtValidator loaded

---

## TERMINAL 4: TESTES
Executar UM POR UM (copiar e colar no terminal):

### TESTE 1: Token Válido (201 Created)
```bash
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: test-001" \
  -d '{"idConta":"ACC-001","valor":100.00,"moeda":"BRL","tipoOperacao":"DEBITO"}' | jq .
```
Esperado: **201 Created** ✅
```json
{
  "id_autorizacao": "...",
  "saldo_reservado": 50900.00,
  "repetition": false,
  "status": "APPROVED"
}
```

---

### TESTE 2: Idempotência (200 OK)
```bash
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: test-001" \
  -d '{"idConta":"ACC-001","valor":100.00,"moeda":"BRL","tipoOperacao":"DEBITO"}' | jq .
```
Esperado: **200 OK** (NÃO 201!) ✅
```json
{
  "id_autorizacao": "...",
  "saldo_reservado": 50900.00,
  "repetition": true,
  "status": "APPROVED"
}
```

---

### TESTE 3: Lambda Authorizer
```bash
curl -X POST http://localhost:8080/v1/contratos/authorize \
  -H "Content-Type: application/json" \
  -d '{"authorizationToken":"Bearer jwt-ACC-001","methodArn":"arn:aws:execute-api:us-east-1:123456789012:api-id/stage/POST/endpoint"}' | jq .
```
Esperado: **200 OK** com `"Effect": "Allow"` ✅
```json
{
  "principalId": "user-ACC-001",
  "policyDocument": {
    "Version": "2012-10-17",
    "Statement": [
      {
        "Action": "execute:Invoke",
        "Effect": "Allow",
        "Resource": "arn:..."
      }
    ]
  },
  "context": {
    "accountId": "ACC-001"
  }
}
```

---

### TESTE 4: Token Inválido (401)
```bash
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Authorization: Bearer invalid-token" \
  -d '{"idConta":"ACC-001","valor":100.00,"moeda":"BRL","tipoOperacao":"DEBITO"}' | jq .
```
Esperado: **401 Unauthorized** ✅
```json
{
  "type": "https://api.pod99.com/errors/unauthorized",
  "title": "Unauthorized",
  "status": 401
}
```

---

### TESTE 5: Limite Insuficiente (402)
```bash
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: test-big" \
  -d '{"idConta":"ACC-001","valor":999999999.00,"moeda":"BRL","tipoOperacao":"DEBITO"}' | jq .
```
Esperado: **402 Payment Required** ✅
```json
{
  "type": "https://api.pod99.com/errors/insufficient-limit",
  "title": "Payment Required",
  "status": 402
}
```

---

## ✅ Checklist de Sucesso

- [ ] Terminal 1: LocalStack UP (vazio)
- [ ] Terminal 2: Terraform apply OK (criar tabelas)
- [ ] Terminal 3: App rodando em 8080
- [ ] Teste 1: 201 Created ✅
- [ ] Teste 2: 200 OK (idempotência) ✅
- [ ] Teste 3: Lambda Authorizer com Allow ✅
- [ ] Teste 4: 401 Unauthorized ✅
- [ ] Teste 5: 402 Payment Required ✅

---

## 🐛 Troubleshooting

### LocalStack não sobe:
```bash
docker-compose down
docker-compose up
```

### Terraform falha (ver error específico):
```bash
terraform plan -var-file=local.tfvars
```

### App não inicia:
```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

### Ver tabelas criadas:
```bash
aws dynamodb list-tables --endpoint-url http://localhost:4566 --region us-east-1
```

### Ver filas criadas:
```bash
aws sqs list-queues --endpoint-url http://localhost:4566 --region us-east-1
```

---

**BOA SORTE! 🚀🔥**
