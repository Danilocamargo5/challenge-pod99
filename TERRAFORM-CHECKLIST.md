# ✅ Terraform - Checklist Completo

## 📁 Arquivos Criados/Atualizados

### Variables
- ✅ `infra/terraform/variables.tf` - Todas as variáveis centralizadas
- ✅ `infra/terraform/local.tfvars` - Valores para LOCAL (com comentários)
- ✅ `infra/terraform/prod.tfvars` - Valores para PROD (com comentários)

### Infrastructure as Code
- ✅ `infra/terraform/main.tf` - Provider AWS + data sources
- ✅ `infra/terraform/dynamodb.tf` - 5 tabelas DynamoDB
- ✅ `infra/terraform/sqs.tf` - 2 filas SQS (FIFO + DLQ)
- ✅ `infra/terraform/eventbridge.tf` - EventBridge rules + IAM roles
- ✅ `infra/terraform/api-gateway.tf` - HTTP API Gateway
- ✅ `infra/terraform/seed-data.tf` - Provisioner para dados de teste
- ✅ `infra/terraform/outputs.tf` - Todos os outputs centralizados

### Documentation
- ✅ `infra/terraform/README.md` - Guia de uso do Terraform
- ✅ `infra/terraform/.gitignore` - Arquivos a ignorar no git
- ✅ `SETUP.md` - Instruções completas de setup

---

## 🔧 Estrutura de Variáveis

### variables.tf - Todas as variáveis:
```hcl
use_localstack                    # boolean (default: true)
aws_region                        # string (default: us-east-1)
aws_access_key_id                 # string sensitive
aws_secret_access_key             # string sensitive
environment                       # local, dev, prod
project                           # string (default: pod99)
dynamodb_billing_mode             # PAY_PER_REQUEST ou PROVISIONED
dynamodb_point_in_time_recovery   # boolean
sqs_message_retention_seconds     # number
sqs_visibility_timeout_seconds    # number
cloudwatch_log_retention_days     # number
enable_api_gateway_logging        # boolean
tags                              # map(string)
```

### local.tfvars - Ambiente LOCAL:
```hcl
use_localstack = true
aws_region = "us-east-1"
aws_access_key_id = "test"
aws_secret_access_key = "test"
environment = "local"
project = "pod99"
```

### prod.tfvars - Ambiente PROD:
```hcl
use_localstack = false
aws_region = "us-east-1"
environment = "prod"
project = "pod99"
# ⚠️ Credenciais via env vars: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY
```

---

## 📊 Terraform Workflow

### 1️⃣ Inicializar
```bash
cd infra/terraform
terraform init
```

### 2️⃣ Verificar
```bash
terraform plan -var-file=local.tfvars
```

### 3️⃣ Aplicar
```bash
terraform apply -var-file=local.tfvars
```

### 4️⃣ Ver outputs
```bash
terraform output
```

### 5️⃣ Destruir (se necessário)
```bash
terraform destroy -var-file=local.tfvars
```

---

## 🏗️ Recursos Criados

| Recurso | Nome | Descrição |
|---------|------|-----------|
| DynamoDB Table | pod99-local-limits | Limites por contrato (300 registros) |
| DynamoDB Table | pod99-local-authorizations | Autorizações de transações |
| DynamoDB Table | pod99-local-accounting | Contabilização de eventos |
| DynamoDB Table | pod99-local-locks | Locks distribuídos (TTL: 30s) |
| DynamoDB Table | pod99-local-rate-limit | Rate limiting (TTL: 1s) |
| SQS Queue | pod99-local-accounting-queue.fifo | Fila FIFO para contabilização |
| SQS Queue | pod99-local-accounting-dlq.fifo | Dead Letter Queue |
| EventBridge Rule | pod99-local-transacao-autorizada-rule | Rule para eventos |
| HTTP API | pod99-api-local | API Gateway HTTP API |
| CloudWatch Log Group | /aws/apigateway/pod99-local | Logs de requisições |

---

## 📤 Outputs Disponíveis

Após `terraform apply`, estes outputs estarão disponíveis:

```bash
terraform output environment_info           # Info do ambiente
terraform output dynamodb_tables            # Nomes das tabelas
terraform output sqs_queues                 # URLs das filas
terraform output eventbridge                # EventBridge rule
terraform output api_gateway                # API Gateway details
terraform output test_data                  # Dados de teste
terraform output connection_info            # Endpoints
terraform output example_curl_authorize     # Exemplo de curl
terraform output setup_status               # Status do setup
```

---

## 🔐 Segurança

### Credenciais

**LOCAL:**
- ✅ Hardcoded: "test" / "test" (seguro para LocalStack)

**PROD:**
- ✅ Environment variables: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`
- ✅ AWS Profile: `aws-vault exec profile -- terraform apply`
- ✅ Nunca fazer commit de credenciais!

### State File
- ✅ `.gitignore` já inclui `*.tfstate`
- ⚠️ Em PROD, usar: `terraform { backend "s3" { ... } }`

---

## 🧪 Testes

```bash
# Validar sintaxe
terraform validate

# Formatter
terraform fmt -recursive

# Linting (com tflint)
tflint

# Planejar sem aplicar
terraform plan -var-file=local.tfvars -out=plan.tfplan
```

---

## 📝 Exemplos de Uso

### Criar infraestrutura LOCAL:
```bash
cd infra/terraform
terraform init
terraform apply -var-file=local.tfvars
```

### Atualizar tags:
```bash
terraform apply -var-file=local.tfvars \
  -var='tags={"Environment":"local","Owner":"DevOps"}'
```

### Destruir tudo:
```bash
terraform destroy -var-file=local.tfvars
```

---

## 🎯 Próximos Passos

- [ ] Rodar `terraform init`
- [ ] Rodar `terraform plan -var-file=local.tfvars`
- [ ] Rodar `terraform apply -var-file=local.tfvars`
- [ ] Verificar `terraform output`
- [ ] Testar com curl
- [ ] Verificar CloudWatch logs

---

**PRONTO! Terraform 100% configurado e documentado!** 🚀

---

## 🔐 LAMBDA AUTHORIZER (NOVO!)

### Arquivo
- ✅ `infra/terraform/lambda-authorizer.tf` - Configura o authorizer

### Configuração

O Lambda Authorizer:
- ✅ Tipo: HTTP (aponta pra endpoint do app)
- ✅ Endpoint: `POST /v1/contratos/authorize`
- ✅ Cache: 5 minutos (300 segundos)
- ✅ Identity source: `$request.header.Authorization`

### Routes com Autenticação

| Rota | Método | Autenticação | Target |
|------|--------|--------------|--------|
| /v1/contratos/{id}/autorizacoes | POST | ✅ Lambda Authorizer | App |
| /v1/contratos/authorize | POST | ❌ Nenhuma (é o authorizer!) | App |
| /health | GET | ❌ Nenhuma | App |

### Fluxo

```
Cliente (Authorization: Bearer jwt-ACC-001)
    ↓
API Gateway (intercepta)
    ↓
Lambda Authorizer (POST /v1/contratos/authorize)
    ↓
JwtValidator.validateAndExtractAccountId()
    ↓
AuthorizerResponse.allow() ou deny()
    ↓
Se Allow: prossegue pra app
Se Deny: 403 Forbidden
```

### DTOs

- ✅ `AuthorizerEvent.java` - Evento recebido do API Gateway
- ✅ `AuthorizerResponse.java` - Resposta com IAM Policy
- ✅ `JwtValidator.java` - Validação de JWT (STUB)

### Controller Update

- ✅ `AuthorizationController.java` - Adicionalado endpoint POST /authorize

---

## 📊 Todas as Variáveis Terraform

### Infraestrutura
```hcl
use_localstack = true/false
aws_region = "us-east-1"
aws_access_key_id = "test" ou env var
aws_secret_access_key = "test" ou env var
```

### Ambiente
```hcl
environment = "local" | "dev" | "prod"
project = "pod99"
```

### DynamoDB
```hcl
dynamodb_billing_mode = "PAY_PER_REQUEST"
dynamodb_point_in_time_recovery = false (true em prod)
```

### SQS
```hcl
sqs_message_retention_seconds = 86400 (1 dia)
sqs_visibility_timeout_seconds = 300 (5 min)
```

### Logs
```hcl
cloudwatch_log_retention_days = 7 (30 em prod)
enable_api_gateway_logging = true
```

### Tags
```hcl
tags = {
  Environment = "local"
  ManagedBy = "Terraform"
}
```

---

## 🚀 Terraform Workflow COMPLETO

```bash
# 1. Init
cd infra/terraform
terraform init

# 2. Plan
terraform plan -var-file=local.tfvars

# 3. Apply (cria TUDO!)
terraform apply -var-file=local.tfvars

# 4. Outputs
terraform output

# 5. Destroy (se necessário)
terraform destroy -var-file=local.tfvars
```

---

**PRONTO! Lambda Authorizer + Terraform = Autenticação no gateway!** 🔐🚀
