# ⚡ COPYPASTE DIRETO NO CODESPACE

## TERMINAL 1: LocalStack
```bash
cd ~/challenge-pod99
docker-compose up
```
Aguardar:
- ✅ localstack listening on http://localhost:4566
- ✅ Ready to accept connections

---

## TERMINAL 2: Terraform
```bash
cd ~/challenge-pod99/infra/terraform
terraform init
terraform apply -var-file=local.tfvars
```
Responder: `yes`

Aguardar:
- ✅ Apply complete! Resources: XX added
- ✅ Outputs: api_gateway_invoke_url = ...

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

---

## TERMINAL 4: Testes
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

---

### TESTE 2: Idempotência (200 OK)
```bash
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: test-001" \
  -d '{"idConta":"ACC-001","valor":100.00,"moeda":"BRL","tipoOperacao":"DEBITO"}' | jq .
```
Esperado: **200 OK** (NOT 201!) com `"repetition": true` ✅

---

### TESTE 3: Lambda Authorizer
```bash
curl -X POST http://localhost:8080/v1/contratos/authorize \
  -H "Content-Type: application/json" \
  -d '{"authorizationToken":"Bearer jwt-ACC-001","methodArn":"arn:aws:execute-api:us-east-1:123456789012:api-id/stage/POST/endpoint"}' | jq .
```
Esperado: **200 OK** com `"Effect": "Allow"` ✅

---

### TESTE 4: Token Inválido (401)
```bash
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Authorization: Bearer invalid-token" \
  -d '{"idConta":"ACC-001","valor":100.00,"moeda":"BRL","tipoOperacao":"DEBITO"}' | jq .
```
Esperado: **401 Unauthorized** ✅

---

### TESTE 5: Limite Insuficiente (402)
```bash
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: test-big" \
  -d '{"idConta":"ACC-001","valor":999999999.00,"moeda":"BRL","tipoOperacao":"DEBITO"}' | jq .
```
Esperado: **402 Payment Required** ✅

---

## ✅ Checklist de Sucesso

- [ ] Terminal 1: LocalStack UP
- [ ] Terminal 2: Terraform apply OK
- [ ] Terminal 3: App rodando em 8080
- [ ] Teste 1: 201 Created ✅
- [ ] Teste 2: 200 OK (idempotência) ✅
- [ ] Teste 3: Lambda Authorizer com Allow ✅
- [ ] Teste 4: 401 Unauthorized ✅
- [ ] Teste 5: 402 Payment Required ✅

---

## 🎯 Se algo quebrar:

**LocalStack não sobe:**
```bash
docker-compose down
docker-compose up
```

**Terraform falha:**
```bash
terraform plan -var-file=local.tfvars
```

**App não inicia:**
```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

---

**BOA SORTE! 🚀**
