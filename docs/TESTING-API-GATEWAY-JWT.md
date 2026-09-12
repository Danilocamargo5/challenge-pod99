# Testing API Gateway + JWT no LocalStack

## 🎯 Overview

Este guia mostra como testar a API com autenticação JWT usando:
1. **LocalStack** (emulação AWS local)
2. **API Gateway** (proxy com Lambda Authorizer)
3. **JWT** (validação de tokens)
4. **Lambda Authorizer** (validação de autenticação)

---

## 📋 Pré-requisitos

- Docker + Docker Compose
- Java 21
- curl (ou Postman)

---

## 🚀 Setup Local

### 1️⃣ Subir LocalStack + App

```bash
# Terminal 1: Subir containers (LocalStack + DynamoDB)
docker-compose up

# Terminal 2: Rodar app Spring Boot
git pull origin develop
./mvnw spring-boot:run
```

**Esperado:**
```
✅ LocalStack pronto em http://localhost:4566
✅ App rodando em http://localhost:8080
✅ Tabelas DynamoDB criadas
✅ API Gateway criado no LocalStack
```

### 2️⃣ Criar API Gateway no LocalStack (via Terraform)

```bash
cd infra/terraform

# Inicializar Terraform
terraform init

# Aplicar com variáveis LOCAL
terraform apply -var-file=local.tfvars

# Outputs:
# - api_gateway_url = ...
# - authorizer_function_name = ...
```

**Resultado:**
```
✅ API Gateway criado em LocalStack
✅ Lambda Authorizer criado
✅ Rotas POST /v1/contratos/{id_contrato}/autorizacoes criada
```

---

## 🔐 Testando com JWT

### Cenário 1: SEM Token (deve retornar 401)

```bash
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-123" \
  -d '{
    "idConta": "ACC-001",
    "valor": 100.00,
    "moeda": "BRL",
    "tipoOperacao": "DEBITO"
  }'

# Resposta esperada: 401 Unauthorized
# {
#   "type": "https://api.pod99.com/errors/unauthorized",
#   "title": "Unauthorized",
#   "status": 401,
#   "detail": "Header Authorization obrigatório (Bearer <token>)",
#   ...
# }
```

### Cenário 2: COM Token Válido (deve retornar 201)

```bash
# Token válido: Bearer jwt-CONTA-001
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-CONTA-001" \
  -H "Idempotency-Key: test-456" \
  -d '{
    "idConta": "ACC-001",
    "valor": 100.00,
    "moeda": "BRL",
    "tipoOperacao": "DEBITO"
  }'

# Resposta esperada: 201 Created
# {
#   "id_autorizacao": "UUID",
#   "saldo_reservado": 99900.00,
#   "correlation_id": "UUID",
#   "status": "APPROVED",
#   "timestamp": "2024-01-01T12:00:00Z"
# }
```

### Cenário 3: Repetição (Idempotência) = 200 OK

```bash
# Repetir MESMA requisição com MESMO Idempotency-Key
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-CONTA-001" \
  -H "Idempotency-Key: test-456" \
  -d '{
    "idConta": "ACC-001",
    "valor": 100.00,
    "moeda": "BRL",
    "tipoOperacao": "DEBITO"
  }'

# Resposta esperada: 200 OK (mesmo corpo, mas status diferente)
```

### Cenário 4: Token Inválido (deve retornar 403)

```bash
# Token inválido: "invalid-xyz"
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer invalid-xyz" \
  -H "Idempotency-Key: test-789" \
  -d '{
    "idConta": "ACC-001",
    "valor": 100.00,
    "moeda": "BRL",
    "tipoOperacao": "DEBITO"
  }'

# Resposta esperada: 403 Forbidden
# {
#   "type": "https://api.pod99.com/errors/forbidden",
#   "title": "Forbidden",
#   "status": 403,
#   "detail": "Token expirado ou inválido",
#   ...
# }
```

---

## 📡 Testando com Lambda Authorizer (via API Gateway)

### Setup API Gateway no LocalStack

```bash
# Criar API Gateway via AWS CLI (LocalStack)
awslocal apigatewayv2 create-api \
  --name pod99-api \
  --protocol-type HTTP \
  --region us-east-1

# Outputs:
# ApiId = ...
# ApiEndpoint = http://localhost:4566/...
```

### Fazer requisição via API Gateway

```bash
# Com token válido
curl -X POST http://localhost:4566/restapis/API_ID/stage/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-CONTA-001" \
  -H "Idempotency-Key: test-123" \
  -d '{...}'

# Fluxo:
# 1. API Gateway recebe request
# 2. Invoca Lambda Authorizer
# 3. Lambda valida token: "Bearer jwt-CONTA-001" ✅
# 4. Lambda retorna IAM Policy (Allow)
# 5. API Gateway passa request pro app
# 6. App processa e retorna 201 Created
```

---

## 🧪 Testes de RFC 7807 (Problem Details)

### Teste: Limite Insuficiente (402 Payment Required)

```bash
# Tentarvalor maior que o limite disponível
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-CONTA-001" \
  -H "Idempotency-Key: test-402" \
  -d '{
    "idConta": "ACC-001",
    "valor": 999999999.00,  # ← Muito grande!
    "moeda": "BRL",
    "tipoOperacao": "DEBITO"
  }'

# Resposta esperada: 402 Payment Required
# RFC 7807 Format:
# {
#   "type": "https://api.pod99.com/errors/insufficient-limit",
#   "title": "Insufficient Limit",
#   "status": 402,
#   "detail": "Limite disponível insuficiente para autorizar esta transação",
#   "instance": "/v1/contratos/CONTA-001/autorizacoes",
#   "correlationId": "UUID",
#   "timestamp": "ISO 8601"
# }
```

### Teste: Validação (422 Unprocessable Entity)

```bash
# JSON inválido ou campos obrigatórios ausentes
curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-CONTA-001" \
  -H "Idempotency-Key: test-422" \
  -d '{
    "idConta": "ACC-001",
    # "valor": FALTANDO!
    "moeda": "BRL"
  }'

# Resposta esperada: 422 Unprocessable Entity
# RFC 7807 Format
```

### Teste: Rate Limit (429 Too Many Requests)

```bash
# Enviar > 100 requisições/segundo
for i in {1..150}; do
  curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer jwt-CONTA-001-$i" \
    -H "Idempotency-Key: test-$i" \
    -d '{...}' &
done
wait

# Algumas respostas esperadas: 429 Too Many Requests
# RFC 7807 Format:
# {
#   "type": "https://api.pod99.com/errors/rate-limit-exceeded",
#   "title": "Rate Limit Exceeded",
#   "status": 429,
#   "detail": "Rate limit excedido: máximo 100 requisições por segundo",
#   ...
# }
```

---

## 📊 Verificar Logs

### LocalStack Logs

```bash
# Ver logs do LocalStack
docker logs challenge-pod99-localstack-1 | tail -100

# Ver logs do API Gateway
awslocal logs tail /aws/apigateway/pod99-local
```

### App Logs

```bash
# Ver logs do Spring Boot
# Já aparecerão no terminal onde rodou mvn spring-boot:run

# Filtrar por autenticação
grep -i "authorization\|jwt\|token" <logfile>
```

### DynamoDB Logs

```bash
# Verificar dados em DynamoDB
awslocal dynamodb scan --table-name pod99-authorizations --region us-east-1

# Verificar rate limiting
awslocal dynamodb scan --table-name pod99-rate-limit --region us-east-1
```

---

## 🔄 Fluxo Completo (Passo a Passo)

```
1. Cliente envia POST /v1/contratos/CONTA-001/autorizacoes
   com Authorization: Bearer jwt-CONTA-001

2. (Local) JwtAuthenticationFilter intercepta request
   → Valida token
   → Extrai account ID
   → Passa pra controller

3. Controller chama AuthorizeTransactionUseCase
   → Adquire locks
   → Verifica idempotência
   → Valida limite
   → Reserva valor
   → Cria autorização
   → Publica evento

4. Controller retorna 201 Created com RFC 7807 format

5. (Production) API Gateway intercepta request
   → Invoca Lambda Authorizer
   → Lambda valida JWT
   → Retorna IAM Policy (Allow/Deny)
   → Se Allow: passa pro controller
   → Se Deny: retorna 403 Forbidden

6. EventBridge recebe evento
   → SNS fan-out
   → SQS accounting-queue recebe
   → ContabilidadeListener processa async
```

---

## 📝 Scripts Prontos

### test-jwt.sh
```bash
#!/bin/bash
# Testar com token válido
ENDPOINT="http://localhost:8080/v1/contratos/CONTA-001/autorizacoes"
TOKEN="Bearer jwt-CONTA-001"
IDEMPOTENCY_KEY="test-$(date +%s)"

curl -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -H "Authorization: $TOKEN" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "idConta": "ACC-001",
    "valor": 100.00,
    "moeda": "BRL",
    "tipoOperacao": "DEBITO"
  }' | jq .
```

### test-no-jwt.sh
```bash
#!/bin/bash
# Testar SEM token (deve retornar 401)
ENDPOINT="http://localhost:8080/v1/contratos/CONTA-001/autorizacoes"

curl -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-123" \
  -d '{
    "idConta": "ACC-001",
    "valor": 100.00,
    "moeda": "BRL",
    "tipoOperacao": "DEBITO"
  }' | jq .
```

---

## ✅ Checklist de Testes

- [ ] Sem token → 401
- [ ] Token válido → 201
- [ ] Repetição → 200
- [ ] Token inválido → 403
- [ ] Limite insuficiente → 402 RFC 7807
- [ ] Rate limit → 429 RFC 7807
- [ ] Validação → 422 RFC 7807
- [ ] Events publicados → SQS recebe
- [ ] Logs aparecem → LocalStack logs OK

---

## 🚀 Deploy em AWS Real

Quando pronto pra produção:

```bash
# Usar prod.tfvars
cd infra/terraform
terraform apply -var-file=prod.tfvars

# Remover JwtAuthenticationFilter (API Gateway cuida)
# Ativar Lambda Authorizer real
# Usar Cognito em vez de JWT simples
```

---

**Pronto pra testar!** 🎉
