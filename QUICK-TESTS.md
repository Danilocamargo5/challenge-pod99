# 🧪 Guia de Testes - POD99

## ✅ Verificações Pré-Teste (Todos OK!)

```
✅ AuthorizerEvent.java              - SYNTAX OK
✅ AuthorizerResponse.java            - SYNTAX OK
✅ JwtValidator.java                  - SYNTAX OK
✅ AuthorizationController.java       - AMBOS OS MÉTODOS implementados:
   - POST /contratos/{id}/autorizacoes (original)
   - POST /contratos/authorize (novo - Lambda Authorizer)
✅ lambda-authorizer.tf               - SYNTAX OK
✅ variables.tf, local.tfvars         - SYNTAX OK
```

---

## 🚀 **4 TERMINAIS - ORDEM EXATA**

### **Terminal 1: LocalStack**
```bash
docker-compose up

# Aguardar até ver:
# ✅ Ready to accept connections
```

---

### **Terminal 2: Terraform**
```bash
cd infra/terraform
terraform init
terraform apply -var-file=local.tfvars

# Responder 'yes' quando pedir

# Aguardar até ver:
# Apply complete! Resources: XX added
```

---

### **Terminal 3: Spring Boot**
```bash
git pull origin develop
./mvnw spring-boot:run

# Aguardar até ver:
# ✅ Tomcat started on port 8080
# ✅ Started Pod99Application
```

---

### **Terminal 4: TESTES**

#### **Teste 1: Token Válido (201 Created)**
```bash
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: test-001" \
  -d '{
    "idConta": "ACC-001",
    "valor": 100.00,
    "moeda": "BRL",
    "tipoOperacao": "DEBITO"
  }' | jq .

# Esperado: 201 Created
# {
#   "id_autorizacao": "...",
#   "saldo_reservado": 50900.00,
#   "repetition": false,
#   "status": "APPROVED",
#   "timestamp": "..."
# }
```

#### **Teste 2: Idempotência (200 OK)**
```bash
# Roda a MESMA requisição (mesmo Idempotency-Key)
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: test-001" \
  -d '{
    "idConta": "ACC-001",
    "valor": 100.00,
    "moeda": "BRL",
    "tipoOperacao": "DEBITO"
  }' | jq .

# Esperado: 200 OK (NÃO 201!)
# {
#   "id_autorizacao": "...",
#   "saldo_reservado": 50900.00,
#   "repetition": true,  ← VIRA TRUE
#   ...
# }
```

#### **Teste 3: Lambda Authorizer (direto)**
```bash
curl -X POST http://localhost:8080/v1/contratos/authorize \
  -H "Content-Type: application/json" \
  -d '{
    "authorizationToken": "Bearer jwt-ACC-001",
    "methodArn": "arn:aws:execute-api:us-east-1:123456789012:api-id/stage/POST/endpoint"
  }' | jq .

# Esperado: 200 OK
# {
#   "principalId": "user-ACC-001",
#   "policyDocument": {
#     "Version": "2012-10-17",
#     "Statement": [
#       {
#         "Action": "execute:Invoke",
#         "Effect": "Allow",  ← ALLOW!
#         "Resource": "arn:..."
#       }
#     ]
#   },
#   "context": {
#     "accountId": "ACC-001"
#   }
# }
```

#### **Teste 4: Token Inválido (401/403)**
```bash
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Authorization: Bearer invalid-token" \
  -d '...'

# Esperado: 401 Unauthorized
# {
#   "type": "https://api.pod99.com/errors/unauthorized",
#   "title": "Unauthorized",
#   "status": 401,
#   "detail": "..."
# }
```

#### **Teste 5: Limite Insuficiente (402)**
```bash
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: test-big" \
  -d '{
    "idConta": "ACC-001",
    "valor": 99999999.00,  ← MUITO GRANDE!
    "moeda": "BRL",
    "tipoOperacao": "DEBITO"
  }'

# Esperado: 402 Payment Required
# {
#   "type": "https://api.pod99.com/errors/insufficient-limit",
#   "status": 402,
#   "detail": "Saldo insuficiente..."
# }
```

---

## 🎯 **Checklist de Testes**

- [ ] Terminal 1: LocalStack up (sem erros)
- [ ] Terminal 2: Terraform apply (Resources added)
- [ ] Terminal 3: App iniciou na porta 8080
- [ ] Teste 1: Token válido → 201 Created ✅
- [ ] Teste 2: Idempotência → 200 OK ✅
- [ ] Teste 3: Lambda Authorizer → 200 com Allow ✅
- [ ] Teste 4: Token inválido → 401 ✅
- [ ] Teste 5: Limite insuficiente → 402 ✅

---

## 🐛 **Troubleshooting**

### App não sobe
```bash
cd /home/claude/challenge-pod99
git pull origin develop
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

### Terraform falha
```bash
terraform plan -var-file=local.tfvars
# Ver erro específico
```

### LocalStack não conecta
```bash
docker-compose down
docker-compose up
# Esperar completamente
```

### Erro de compilação Maven
```bash
# Limpar cache
./mvnw clean
./mvnw compile
```

---

## 📊 **Dados de Teste**

Após terraform apply, você tem:
- **100 contas**: ACC-001 até ACC-100
- **300 contratos**: CONTA-001 até CONTA-300
- **Limites**: 51.000 até 150.000 (varia por conta)

Exemplo: `ACC-001` tem 3 contratos:
- CONTA-001: limite 51.000,00
- CONTA-002: limite 51.000,00
- CONTA-003: limite 51.000,00

---

**TUDO PRONTO! Boa sorte amanhã!** 🚀🔥
