# Lambda Authorizer - Documentação

## 🔐 O que é?

Lambda Authorizer é um recurso do API Gateway que **valida requisições ANTES de chegar ao backend**.

Fluxo:
```
1. Cliente faz requisição com header "Authorization: Bearer jwt-ACC-001"
     ↓
2. API Gateway intercepta
     ↓
3. Chama Lambda Authorizer (endpoint /authorize)
     ↓
4. Lambda Authorizer valida JWT e retorna IAM Policy
     ↓
5. Se Allow → requisição segue pra app
   Se Deny  → API Gateway retorna 403 Forbidden
```

---

## 🏗️ Arquitetura

### Componentes

```
┌─────────────────┐
│   Cliente       │
│ (curl/browser)  │
└────────┬────────┘
         │ POST /v1/contratos/CONTA-001/autorizacoes
         │ Authorization: Bearer jwt-ACC-001
         │
         ▼
┌─────────────────────────────────────────┐
│       API Gateway (LocalStack)          │
├─────────────────────────────────────────┤
│ Recebe requisição                       │
│ ↓                                       │
│ Extrai header Authorization             │
│ ↓                                       │
│ Chama Lambda Authorizer                 │
└──────────┬────────────────────┬─────────┘
           │                    │
    POST /authorize      HTTP POST
    └──────────────┐      ↓
                   │    ┌──────────────────────────────┐
                   └───→│ App Spring Boot              │
                        ├──────────────────────────────┤
                        │ POST /v1/contratos/authorize │
                        │ (Lambda Authorizer Endpoint) │
                        │                              │
                        │ JwtValidator:                │
                        │  - Valida Bearer token       │
                        │  - Extrai account ID         │
                        │  - Retorna IAM Policy        │
                        └───────┬────────────────────┬──┘
                                │ ALLOW              │ DENY
                                ▼                    ▼
                          Segue pra app        403 Forbidden
```

---

## 📝 Endpoint do Lambda Authorizer

### URL
```
POST /v1/contratos/authorize
```

### Request (vem do API Gateway)
```json
{
  "authorizationToken": "Bearer jwt-ACC-001",
  "methodArn": "arn:aws:execute-api:us-east-1:account:api-id/stage/method/resource"
}
```

### Response (retorna policy)

**ALLOW:**
```json
{
  "principalId": "user-ACC-001",
  "policyDocument": {
    "Version": "2012-10-17",
    "Statement": [
      {
        "Action": "execute:Invoke",
        "Effect": "Allow",
        "Resource": "arn:aws:execute-api:us-east-1:account:api-id/stage/*/*"
      }
    ]
  },
  "context": {
    "accountId": "ACC-001"
  }
}
```

**DENY:**
```json
{
  "principalId": "user-unauthorized",
  "policyDocument": {
    "Version": "2012-10-17",
    "Statement": [
      {
        "Action": "execute:Invoke",
        "Effect": "Deny",
        "Resource": "arn:..."
      }
    ]
  },
  "context": {}
}
```

---

## 🚀 Como Funciona Local (com LocalStack)

### 1. LocalStack API Gateway intercepta
```
GET /v1/contratos/CONTA-001/autorizacoes
Authorization: Bearer jwt-ACC-001
```

### 2. API Gateway chama Lambda Authorizer
```bash
POST http://host.docker.internal:8080/v1/contratos/authorize
{
  "authorizationToken": "Bearer jwt-ACC-001",
  "methodArn": "arn:..."
}
```

### 3. JwtValidator no app
```java
String accountId = jwtValidator.validateAndExtractAccountId(
  "Bearer jwt-ACC-001"  // ✅ extrai "ACC-001"
);

// Retorna AuthorizerResponse.allow("ACC-001", methodArn)
```

### 4. API Gateway recebe response
```json
{
  "principalId": "user-ACC-001",
  "policyDocument": { "Statement": [{ "Effect": "Allow", ... }] },
  "context": { "accountId": "ACC-001" }
}
```

### 5. API Gateway permite requisição prosseguir
```
✅ Requisição vai pra app em http://host.docker.internal:8080/v1/contratos/...
```

---

## 🧪 Testes

### Teste VÁLIDO (token correto)
```bash
curl -X POST http://localhost:8081/v1/contratos/authorize \
  -H "Content-Type: application/json" \
  -d '{
    "authorizationToken": "Bearer jwt-ACC-001",
    "methodArn": "arn:aws:execute-api:us-east-1:123456789012:api/stage/POST/endpoint"
  }'

# Response: 200 OK
# {
#   "principalId": "user-ACC-001",
#   "policyDocument": { ... },
#   "context": { "accountId": "ACC-001" }
# }
```

### Teste INVÁLIDO (token ruim)
```bash
curl -X POST http://localhost:8081/v1/contratos/authorize \
  -H "Content-Type: application/json" \
  -d '{
    "authorizationToken": "Bearer invalid-token",
    "methodArn": "arn:..."
  }'

# Response: 200 OK (Lambda Authorizer sempre retorna 200!)
# {
#   "principalId": "user-unauthorized",
#   "policyDocument": { ... },  ← Effect: "Deny"
#   "context": {}
# }
```

---

## 📋 Cenários de Teste (via API Gateway)

### 1. Autorização com sucesso
```bash
curl -X POST http://APIGW_URL/v1/contratos/CONTA-001/autorizacoes \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: test-1" \
  -d '{"idConta": "ACC-001", "valor": 100, ...}'

# Response: 201 Created
# {
#   "id_autorizacao": "UUID",
#   "status": "APPROVED",
#   ...
# }
```

### 2. Token inválido
```bash
curl -X POST http://APIGW_URL/v1/contratos/CONTA-001/autorizacoes \
  -H "Authorization: Bearer invalid-token" \
  -d '...'

# Response: 403 Forbidden
# API Gateway bloqueia porque Lambda Authorizer retornou Deny
```

### 3. Sem Authorization header
```bash
curl -X POST http://APIGW_URL/v1/contratos/CONTA-001/autorizacoes \
  -d '...'

# Response: 401 Unauthorized
# API Gateway bloqueia porque Authorization está ausente
```

---

## 🔒 Segurança

### ⚠️ IMPORTANTE: JWT é STUB
Atual:
- ✅ Valida formato "Bearer jwt-ACC-001"
- ✅ Extrai account ID
- ❌ NÃO valida assinatura
- ❌ NÃO valida expiração
- ❌ NÃO valida issuer

**Para PRODUÇÃO:**
```java
// Usar biblioteca como:
// - io.jsonwebtoken:jjwt
// - com.auth0:java-jwt
// - org.springframework.security:spring-security-oauth2-jose

JwtDecoder decoder = JwtDecoders.fromIssuerLocation("https://keycloak.com/auth/realms/prod");
Jwt jwt = decoder.decode(token);  // ✅ Valida tudo
String accountId = jwt.getClaimAsString("account_id");
```

---

## 📚 Arquivos Principais

```
src/main/java/com/pod99/authorization/infrastructure/
├── AuthorizationController.java           (+ método /authorize)
├── AuthorizerEvent.java                   (evento do API Gateway)
├── AuthorizerResponse.java                (resposta com IAM Policy)
└── JwtValidator.java                      (valida JWT e extrai account ID)

infra/terraform/
├── lambda-authorizer.tf                   (configura authorizer)
└── api-gateway.tf                         (API Gateway routes)
```

---

## 🚀 Próximos Passos

- [ ] Rodar `terraform apply`
- [ ] Testar endpoint `/authorize` localmente
- [ ] Testar via API Gateway (LocalStack)
- [ ] Validar cenários (token válido, inválido, ausente)
- [ ] Estudar código para defesa

---

**Lambda Authorizer: Autenticação no gateway!** 🔐🚀
