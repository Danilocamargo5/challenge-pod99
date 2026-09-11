# 🚀 Quick Start - POD99 Authorization Platform

## Pré-requisitos
- Java 21 LTS
- Maven 3.9+
- Docker + Docker Compose

## 1️⃣ Subir Dependências (LocalStack + DynamoDB)

```bash
docker-compose up
```

Aguarda até ver:
```
✅ dynamodb-local is healthy
✅ localstack is healthy
```

## 2️⃣ Rodar a Aplicação (em outro Terminal)

```bash
mvn spring-boot:run
```

Aguarda até ver:
```
Started Pod99Application in X seconds
```

> **Nota:** O profile `local` é ativado automaticamente pelo Maven (configurado no `pom.xml`)

## 3️⃣ Testar a API

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
```

## 4️⃣ Ver Documentação

```bash
http://localhost:8080/swagger-ui.html
```

## 5️⃣ Rodar Testes

```bash
mvn clean test
```

## 6️⃣ Gerar Relatório de Testes

```bash
mvn surefire-report:report
cd target/reports
python3 -m http.server 8000
```

Acessa: `http://localhost:8000/surefire.html`

---

**Pronto!** App rodando em `localhost:8080` com todas as dependências em containers.
