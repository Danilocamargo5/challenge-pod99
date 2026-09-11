# POD99 Terraform IaC

Infrastructure as Code para deployment da plataforma POD99 em AWS.

## Arquivos

- `main.tf` - Recursos principais (DynamoDB, SQS, EventBridge, API Gateway)
- `variables.tf` - Definição de variáveis com validações
- `outputs.tf` - Saídas para usar recursos criados
- `terraform.tfvars.example` - Exemplo de valores

## Pré-requisitos

1. **Terraform** >= 1.5
   ```bash
   brew install terraform  # macOS
   # ou download em https://www.terraform.io/downloads
   ```

2. **AWS CLI** configurado
   ```bash
   aws configure
   # Insira: Access Key ID, Secret Access Key, Region, Output format
   ```

3. **Variáveis de ambiente** (opcional)
   ```bash
   export AWS_REGION=us-east-1
   export AWS_PROFILE=pod99
   ```

## Como Usar

### 1. Preparar variáveis

```bash
cd infra/terraform

# Copiar exemplo
cp terraform.tfvars.example terraform.tfvars

# Editar com seus valores
nano terraform.tfvars
```

**Valores importantes:**

| Variável | Descrição | Exemplo |
|----------|-----------|---------|
| `aws_region` | Região AWS | `us-east-1` |
| `environment` | Ambiente (dev/staging/prod) | `prod` |
| `api_rate_limit` | Requisições/segundo | `1000` |
| `api_burst_limit` | Burst simultâneo | `100` |
| `cognito_user_pool_id` | Cognito Pool | `us-east-1_XXXXXXXXX` |
| `nlb_dns_name` | NLB/ALB DNS | `pod99-nlb-xxx.elb.amazonaws.com` |

### 2. Inicializar Terraform

```bash
terraform init
```

Isso vai:
- Baixar providers (AWS)
- Criar `.terraform/` com lock files
- Configurar backend (local por padrão)

### 3. Validar configuração

```bash
terraform validate
terraform fmt -check .
```

### 4. Planejar deployment

```bash
terraform plan -out=tfplan

# Revisar mudanças
# Cada recurso será listado (adicionar, modificar, deletar)
```

### 5. Aplicar infraestrutura

```bash
terraform apply tfplan
```

Isso vai criar:
- 4 tabelas DynamoDB (limits, authorizations, accounting, locks)
- 4 filas SQS (accounting, fraud, notifications + DLQ)
- 1 EventBridge Event Bus + Rule
- 1 API Gateway REST API com rate limiting
- 1 Lambda Authorizer para OAuth2
- CloudWatch Log Groups e Alarms

**Tempo esperado**: ~2-3 minutos

### 6. Obter saídas

```bash
terraform output

# Saídas específicas
terraform output api_endpoint
terraform output api_key  # Sensível - usar com cuidado
terraform output dynamodb_tables
terraform output sqs_queue_urls
```

## Exemplo: Chamar API

```bash
API_ENDPOINT=$(terraform output -raw api_endpoint)
API_KEY=$(terraform output -raw api_key)

curl -X POST $API_ENDPOINT/v1/contratos/CONTA-001/autorizacoes \
  -H "Authorization: Bearer <token_oauth2>" \
  -H "x-api-key: $API_KEY" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "id_conta": "ACC-001",
    "valor": 100.00,
    "moeda": "BRL",
    "tipo_operacao": "DEBITO"
  }'
```

## Rate Limiting (API Gateway)

Configurado via Usage Plans:

| Configuração | Valor |
|---|---|
| **Rate Limit** | 1.000 req/segundo |
| **Burst** | 100 requisições simultâneas |
| **Quota** | 1.000.000 req/dia |

**Headers de resposta:**
```
X-RateLimit-Limit-Http-Request-Count: 1000
X-RateLimit-Limit-Http-Request-Sum-Bits: 10000000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1694455200
```

**HTTP 429 quando excedido:**
```json
{
  "message": "Rate limit exceeded"
}
```

## Authorization (OAuth2 / Lambda Authorizer)

Lambda Authorizer valida JWT token:

```bash
# Header obrigatório
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

# Lambda valida assinatura e retorna IAM policy
# Se válido: 200 OK + policy com permitido
# Se inválido: 401 Unauthorized
```

## Monitoramento

### CloudWatch Logs
```bash
aws logs tail /aws/apigateway/pod99 --follow
```

### CloudWatch Alarms
- `pod99-accounting-dlq-messages` - Alert se DLQ tem mensagens
- `pod99-api-5xx-errors` - Alert se há erros 5xx

### X-Ray Tracing
```bash
# Ver traces no AWS Console
# https://console.aws.amazon.com/xray/home
```

### EventBridge
```bash
# Listar eventos roteados
aws events list-rules --event-bus-name pod99-event-bus
aws events list-targets-by-rule --rule pod99-transacao-autorizada-rule
```

## Limpeza (Destruir recursos)

```bash
terraform destroy
```

Confirme com `yes` para destruir **todos** os recursos.

## Troubleshooting

### Erro: "InvalidUserID.Malformed"
**Causa**: Cognito User Pool ID está inválido
**Solução**: Verificar `terraform.tfvars` e usar ID correto

### Erro: "No valid credentials found"
**Causa**: AWS CLI não está configurado
**Solução**: `aws configure` e inserir credenciais

### Erro: "Conflicting configuration attributes"
**Causa**: Mistura de `assume_role_policy` com `principal`
**Solução**: Usar um ou outro, não ambos

### Timeout no deployment
**Causa**: Recursos levam tempo pra criar
**Solução**: Aguardar 5-10 minutos (normal) e verificar `terraform apply` novamente

## Cost Estimation

**Estimativa mensal** (100M requests/mês):

| Serviço | Custo | Observação |
|---------|-------|-----------|
| DynamoDB | ~$10 | On-Demand (PAY_PER_REQUEST) |
| SQS | ~$5 | 4 filas, ~10M mensagens/mês |
| EventBridge | ~$1 | 1 regra, <$1 por milhão eventos |
| API Gateway | ~$35 | 1M requisições = $3.50 |
| Lambda Authorizer | ~$20 | 100M invocações = $20 |
| CloudWatch | ~$5 | Logs, alarms, metrics |
| **TOTAL** | **~$76** | **Servidor: ~$150-300/mês** |

## Debugging

### Ver estado Terraform

```bash
terraform state list
terraform state show aws_dynamodb_table.limits
```

### Refrescar estado

```bash
terraform refresh
```

### Plan com verbose

```bash
terraform plan -var="environment=staging" -out=tfplan
```

## Referências

- [AWS Terraform Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [API Gateway Rate Limiting](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-request-throttling.html)
- [DynamoDB On-Demand](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/BillingMode.html)
- [EventBridge Rules](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-rules.html)
