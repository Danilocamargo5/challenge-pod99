# Terraform Configuration - POD99

Infraestrutura como código (IaC) usando Terraform para provisionar recursos AWS (ou LocalStack local).

---

## 📁 Arquivos

```
infra/terraform/
├── main.tf                  # Provider e data sources
├── variables.tf             # Definição de variáveis centralizadas
├── dynamodb.tf              # 5 tabelas DynamoDB
├── sqs.tf                   # Filas SQS + DLQ
├── eventbridge.tf           # EventBridge rules e targets
├── api-gateway.tf           # HTTP API Gateway
├── seed-data.tf             # Provisioner para popular dados
├── outputs.tf               # Outputs centralizados
├── local.tfvars             # Valores para ambiente LOCAL
├── prod.tfvars              # Valores para ambiente PROD
└── .gitignore               # Arquivos a ignorar
```

---

## 🔧 Uso

### **LOCAL (com LocalStack)**

```bash
# 1. Inicializar Terraform (primeira vez)
terraform init

# 2. Preview das mudanças
terraform plan -var-file=local.tfvars

# 3. Aplicar configuração
terraform apply -var-file=local.tfvars
```

**Cria:**
- ✅ 5 tabelas DynamoDB
- ✅ 2 filas SQS (FIFO + DLQ)
- ✅ EventBridge rule
- ✅ HTTP API Gateway
- ✅ CloudWatch logs
- ✅ 300 registros de teste (via script)

---

### **PRODUÇÃO (AWS Real)**

```bash
# 1. Configurar credenciais
export AWS_ACCESS_KEY_ID="sua-chave"
export AWS_SECRET_ACCESS_KEY="sua-secret"

# 2. Aplicar configuração
terraform apply -var-file=prod.tfvars
```

---

## 🧹 Limpeza

```bash
terraform destroy -var-file=local.tfvars
```

---

**Pronto! Infra as Code!** 🚀
