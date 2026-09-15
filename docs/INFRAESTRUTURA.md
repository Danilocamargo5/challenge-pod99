# 🚀 INFRAESTRUTURA - POD99

## Status: PRONTO PARA SUBIR ✅

Guia completo para fazer a infraestrutura funcionar no Codespace.

---

## 📋 ÍNDICE

1. [Validação Rápida](#validação-rápida)
2. [Subir Infraestrutura (Hoje)](#subir-infraestrutura-hoje)
3. [Subir Microsserviços (Hoje)](#subir-microsserviços-hoje)
4. [Testar End-to-End (Amanhã)](#testar-end-to-end-amanhã)
5. [Troubleshooting](#troubleshooting)

---

## ✅ Validação Rápida

Antes de começar, valide se tudo está no lugar:

```bash
# No diretório raiz do projeto:
chmod +x scripts/validate-infra.sh
./scripts/validate-infra.sh
```

**Esperado:**
- ✅ DynamoDB Local: detectável (não precisa estar rodando YET)
- ✅ LocalStack: detectável
- ✅ Pom.xml (parent): multi-module com 4 módulos
- ✅ 4 módulos (common-lib, authorization-service, limits-service, accounting-service): encontrados
- ✅ Portas configuradas: 8080, 8082 (SQS listener, sem porta HTTP)
- ✅ Scripts: todos executáveis

---

## 🐳 Subir Infraestrutura (Hoje)

### Passo 1: Clonar o Repositório (se não tiver)

```bash
cd /workspaces
git clone https://github.com/Danilocamargo5/challenge-pod99.git
cd challenge-pod99
```

### Passo 2: Garantir que está no Commit Certo

```bash
git checkout develop
git pull origin develop
```

**Esperado:**
```
HEAD is now at f63e7a7: fix: Limpar estrutura de módulos - remover duplicação
```

### Passo 3: Iniciar Docker Compose + Terraform + EventBridge Setup

```bash
./scripts/start-local.sh
```

**O que acontece (leva ~30-45 segundos):**
1. Docker inicia: dynamodb-local (8000), localstack (4566), api-gateway-simulator (8081)
2. Terraform aplica infraestrutura:
   - Cria 5 tabelas DynamoDB
   - Cria 2 filas SQS FIFO (+ DLQ)
   - Cria EventBridge rule + Lambda Authorizer
   - Popula 300 registros de teste
3. EventBridge → SQS Target é criado

**Logs esperados:**
```
🚀 Iniciando ambiente local POD99...
🐳 Subindo LocalStack e DynamoDB...
🔧 Configurando rede Docker...
🏗️ Aplicando infraestrutura Terraform...
...
Apply complete! Resources: 15 added, 0 changed, 0 destroyed.
🔌 Configurando EventBridge → SQS Target...
...
✅ EventBridge → SQS CONFIGURADO!
🌐 Iniciando API Gateway Simulator...
============================================================
✅ Ambiente local POD99 iniciado com sucesso!
============================================================
```

### Passo 4: Validar Containers (em outro terminal)

```bash
docker ps

# Esperado:
CONTAINER ID   NAMES                      STATUS
xxx            api-gateway-simulator      Up 2 minutes
xxx            localstack                 Up 3 minutes
xxx            dynamodb-local             Up 3 minutes
```

### Passo 5: Testar LocalStack + DynamoDB + EventBridge

```bash
# Testar DynamoDB
curl -X POST http://localhost:8000/ \
  -H "Content-Type: application/x-amz-json-1.0" \
  -H "X-Amz-Target: DynamoDB_20120810.ListTables" \
  -d '{}' | jq '.TableNames[]'

# Esperado: pod99-accounting, pod99-authorizations, pod99-limits, etc.

# Testar LocalStack / EventBridge
curl -s http://localhost:4566/_localstack/health | jq '.services'

# Esperado: {"apigateway": "running", "events": "running", "sqs": "running", ...}
```

✅ Se tudo passou: **Infraestrutura está 100% pronta!**

---

## 🚀 Subir Microsserviços (Hoje)

### Terminal 1: Authorization-Service (porta 8080)

```bash
mvn -pl common-lib install -DskipTests   # 1ª vez apenas
mvn -pl authorization-service spring-boot:run
```

**Logs esperados:**
```
[main] INFO com.pod99.AuthorizationServiceApplication
✅ POD99 Authorization Service started successfully on port 8080
Started AuthorizationServiceApplication in 8.234 seconds
```

### Terminal 2: Limits-Service (porta 8082)

```bash
mvn -pl limits-service spring-boot:run
```

**Logs esperados:**
```
[main] INFO com.pod99.LimitsServiceApplication
✅ POD99 Limits Service started successfully on port 8082
Started LimitsServiceApplication in 7.891 seconds
```

### Terminal 3: Accounting-Service (listener SQS)

```bash
mvn -pl accounting-service spring-boot:run
```

**Logs esperados:**
```
[main] INFO com.pod99.AccountingServiceApplication
✅ POD99 Accounting Service started successfully (SQS Listener active)
Container io.awspring.cloud.sqs.sqsListenerEndpointContainer#0 started
Started AccountingServiceApplication in 8.456 seconds
```

### Terminal 4: Validar Serviços Rodando

```bash
# Health checks
curl -s http://localhost:8080/actuator/health | jq .
curl -s http://localhost:8082/v1/limits/health | jq .

# Esperado:
{
  "service": "authorization-service",
  "status": "UP",
  "port": 8080
}
```

✅ Se todos os 3 subiram com ✅: **Microsserviços estão 100% pronto!**

---

## 🧪 Testar End-to-End (Amanhã)

### Transação de Teste

```bash
curl -s -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: teste-$(date +%s%N)" \
  -d '{
    "idConta":"ACC-001",
    "valor":999.0,
    "moeda":"BRL",
    "tipoOperacao":"DEBITO"
  }' | jq .
```

**Esperado (201 Created):**
```json
{
  "idAutorizacao": "9eea7909-...",
  "status": "APPROVED",
  "saldoReservado": 49118.0,
  "timestamp": "2026-09-14T21:00:57Z",
  "repetition": false
}
```

### Validar Fluxo Completo nos Logs

**Terminal 1 (Authorization):**
```
═══════════════════════════════════════════════════════════════
🔵 [AUTHORIZATION] ENTRADA - Recebendo requisição
   Conta: ACC-001 | Contrato: CONTA-001 | Valor: 999.0 BRL
   ...

🟢 [AUTHORIZATION] SAÍDA - Autorização aprovada
   ID Autorização: 9eea7909-... | Status: APPROVED
   ...
```

**Terminal 2 (Limits):**
```
═══════════════════════════════════════════════════════════════
🔵 [LIMITS] ENTRADA - Requisição de reserva
   ID Autorização: 9eea7909-... | Contrato: CONTA-001
   ...

🟢 [LIMITS] SAÍDA - Limite validado
   ID Autorização: 9eea7909-... | Status: APPROVED
   ...
```

**Terminal 3 (Accounting):**
```
═══════════════════════════════════════════════════════════════
🔵 [ACCOUNTING] ENTRADA - Recebido evento SQS
   Tamanho: 607 bytes
   ID Autorização: 9eea7909-...
   ...

🟢 [ACCOUNTING] SAÍDA - Contabilização salva
   ID Autorização: 9eea7909-... | Status: RECORDED
   ...
```

✅ Se viu os 3 logs em sequência: **End-to-end funcionando!**

---

## 🔧 Troubleshooting

### ❌ "LocalStack não está respondendo"

```bash
# Verificar status
docker logs localstack | tail -20

# Reiniciar
docker stop localstack
docker rm localstack
docker compose up -d localstack

# Aguarde ~30 segundos + health check
curl http://localhost:4566/_localstack/health
```

### ❌ "DynamoDB vazio - sem tabelas"

```bash
# Terraform pode não ter criado
cd infra/terraform
terraform apply -auto-approve

# Validar
curl -X POST http://localhost:8000/ \
  -H "Content-Type: application/x-amz-json-1.0" \
  -H "X-Amz-Target: DynamoDB_20120810.ListTables" \
  -d '{}' | jq '.TableNames'
```

### ❌ "EventBridge → SQS não funciona"

```bash
# Setup manual
./scripts/validate-eventbridge-sqs.sh

# Validar
./scripts/validate-eventbridge-sqs.sh
```

### ❌ "Microsserviço não compila - 'common-lib não encontrado'"

```bash
# Instalar common-lib no repositório local Maven
mvn -pl common-lib install -DskipTests

# Depois rodar o serviço
mvn -pl authorization-service spring-boot:run
```

### ❌ "Porta 8080/8082 já em uso"

```bash
# Encontrar processo
lsof -i :8080
lsof -i :8082

# Matar
kill -9 <PID>

# Ou mude a porta em application-local.yml
```

---

## 📊 Checklist Final

Antes de considerar pronto:

- [ ] `./scripts/validate-infra.sh` retorna ✅ em tudo
- [ ] Docker ps mostra 3 containers rodando
- [ ] `curl http://localhost:8000/` retorna HTTP 200
- [ ] `curl http://localhost:4566/_localstack/health` retorna OK
- [ ] `curl http://localhost:8081/` retorna HTTP 200
- [ ] Terminal 1: Authorization subiu com ✅
- [ ] Terminal 2: Limits subiu com ✅
- [ ] Terminal 3: Accounting subiu com ✅
- [ ] `curl http://localhost:8080/actuator/health` retorna UP
- [ ] `curl http://localhost:8082/v1/limits/health` retorna UP

---

## 🎯 Resumo

| Etapa | O que Faz | Tempo | Status |
|-------|-----------|-------|--------|
| 1. Validação | Checa pré-requisitos | 10s | ✅ |
| 2. Docker | Inicia containers | 30s | ✅ |
| 3. Terraform | Cria AWS resources | 15s | ✅ |
| 4. EventBridge | Conecta rule → SQS | 5s | ✅ |
| 5. Auth-Service | Porta 8080 | 10s | ✅ |
| 6. Limits-Service | Porta 8082 | 10s | ✅ |
| 7. Accounting-Service | Listener SQS | 10s | ✅ |
| **TOTAL** | **Infraestrutura 100% pronta** | **~1min 20s** | **✅** |

---

**🚀 Agora é só subir no Codespace e deixar rodando! Testes amanhã!**
