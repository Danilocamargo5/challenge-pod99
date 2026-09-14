# 🧪 Testes - API Gateway Simulator

Todos os testes abaixo devem ser executados com o Simulator rodando na porta **8081**.

**Pré-requisitos:**
```bash
./scripts/start-local.sh
mvn spring-boot:run  # Em outro terminal
```

---

## ✅ 1. TESTE DE SUCESSO - 201 CREATED

Autorização válida, valor dentro do limite, primeiro acesso.

**Esperado:** `201 Created` com body contendo ID da autorização e saldo reservado

```bash
curl -i -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: teste-1" \
  -d '{"idConta":"ACC-001","valor":100.00,"moeda":"BRL","tipoOperacao":"DEBITO"}'
```

---

## 🔄 2. IDEMPOTÊNCIA - 201 (mesma resposta)

Mesma `Idempotency-Key` deve retornar exatamente o mesmo resultado.

**Esperado:** `201 Created` com **mesmo ID de autorização** que o teste anterior

```bash
curl -i -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: teste-1" \
  -d '{"idConta":"ACC-001","valor":100.00,"moeda":"BRL","tipoOperacao":"DEBITO"}'
```

---

## 🔐 3. TOKEN INVÁLIDO - 401 Unauthorized

Lambda Authorizer rejeita token inválido.

**Esperado:** `401 Unauthorized`

```bash
curl -i -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-INVALIDO" \
  -H "Idempotency-Key: teste-token-invalido" \
  -d '{"idConta":"ACC-001","valor":100.00,"moeda":"BRL","tipoOperacao":"DEBITO"}'
```

---

## ❌ 4. SEM AUTHORIZATION HEADER - 422 Unprocessable Entity

Header obrigatório não foi enviado.

**Esperado:** `422 Unprocessable Entity`

```bash
curl -i -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: teste-sem-auth" \
  -d '{"idConta":"ACC-001","valor":100.00,"moeda":"BRL","tipoOperacao":"DEBITO"}'
```

---

## 💳 5. SALDO INSUFICIENTE - 402 Payment Required

Valor solicitado (60000) > limite disponível (51000).

**Esperado:** `402 Payment Required`

```bash
curl -i -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: teste-saldo-insuficiente" \
  -d '{"idConta":"ACC-001","valor":60000.00,"moeda":"BRL","tipoOperacao":"DEBITO"}'
```

---

## 🔁 6. TRANSAÇÃO DUPLICADA - 409 Conflict

Mesma requisição (sem `Idempotency-Key` idêntica) em menos de 1 segundo.

**Esperado:** Primeira requisição `201`, segunda requisição `409 Conflict`

```bash
# Primeira requisição
curl -i -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: teste-duplicada" \
  -d '{"idConta":"ACC-001","valor":100.00,"moeda":"BRL","tipoOperacao":"DEBITO"}'

# Segunda requisição IDÊNTICA em menos de 1 segundo
curl -i -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: teste-duplicada" \
  -d '{"idConta":"ACC-001","valor":100.00,"moeda":"BRL","tipoOperacao":"DEBITO"}'
```

---

## ⚡ 7. RATE LIMIT - 429 Too Many Requests

15 requisições em sequência (limite configurado em `application.yml`).

**Esperado:** Primeiras requisições `201`, depois `429 Too Many Requests`

```bash
for i in {1..15}; do
  curl -s -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer jwt-ACC-001" \
    -H "Idempotency-Key: teste-rate-limit-$i" \
    -d '{"idConta":"ACC-001","valor":10.00,"moeda":"BRL","tipoOperacao":"DEBITO"}' | grep -o "HTTP/1.1 [0-9]*"
done
```

---

## 🚫 8. CONTRATO INVÁLIDO - 422 Unprocessable Entity

Contrato `CONTA-999` não existe (seed data cria apenas CONTA-001, 002, 003).

**Esperado:** `422 Unprocessable Entity`

```bash
curl -i -X POST http://localhost:8081/v1/contratos/CONTA-999/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: teste-contrato-invalido" \
  -d '{"idConta":"ACC-001","valor":100.00","moeda":"BRL","tipoOperacao":"DEBITO"}'
```

---

## 📋 9. SEM IDEMPOTENCY-KEY - 422 Unprocessable Entity

Header obrigatório não foi enviado.

**Esperado:** `422 Unprocessable Entity`

```bash
curl -i -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -d '{"idConta":"ACC-001","valor":100.00","moeda":"BRL","tipoOperacao":"DEBITO"}'
```

---

## 🔀 10. MÚLTIPLOS CONTRATOS - 201 (todos devem passar)

Mesmo account pode autorizar transações em contratos diferentes.

**Esperado:** Todas as três requisições retornam `201 Created`

```bash
# Contrato 001
curl -i -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: teste-contrato-1" \
  -d '{"idConta":"ACC-001","valor":100.00","moeda":"BRL","tipoOperacao":"DEBITO"}'

# Contrato 002
curl -i -X POST http://localhost:8081/v1/contratos/CONTA-002/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: teste-contrato-2" \
  -d '{"idConta":"ACC-001","valor":100.00","moeda":"BRL","tipoOperacao":"DEBITO"}'

# Contrato 003
curl -i -X POST http://localhost:8081/v1/contratos/CONTA-003/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: teste-contrato-3" \
  -d '{"idConta":"ACC-001","valor":100.00","moeda":"BRL","tipoOperacao":"DEBITO"}'
```

---

## 📊 Matriz de Cenários

| Teste | Status | Descrição |
|-------|--------|-----------|
| 1. Sucesso | 201 | Primeiro acesso válido |
| 2. Idempotência | 201 | Mesma chave retorna mesmo resultado |
| 3. Token Inválido | 401 | Lambda Authorizer rejeita |
| 4. Sem Auth Header | 422 | Header obrigatório falta |
| 5. Saldo Insuficiente | 402 | Valor > limite |
| 6. Transação Duplicada | 409 | Mesma transação < 1s |
| 7. Rate Limit | 429 | Limite de requisições excedido |
| 8. Contrato Inválido | 422 | Contrato não existe |
| 9. Sem Idempotency-Key | 422 | Header obrigatório falta |
| 10. Múltiplos Contratos | 201 | Mesmo account, contratos diferentes |

---

## 🔧 Troubleshooting

**Simulator não responde?**
```bash
docker logs api-gateway-simulator
```

**Lambda Authorizer erro?**
```bash
docker logs localstack | grep lambda
```

**Spring Boot não recebendo?**
```bash
# Verificar se está rodando
curl http://localhost:8080/actuator/health
```

---

## 📝 Notas

- Limite por contrato: **51000.00**
- Rate limit: **10 requisições/minuto** (configurável em `application.yml`)
- Todos os testes usam **conta ACC-001** com contrato ACC-001
- Seed data cria 3 contratos: CONTA-001, CONTA-002, CONTA-003
