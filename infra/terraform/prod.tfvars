# Terraform Variables para Produção (AWS Real)
# ⚠️ Credenciais devem vir de variáveis de ambiente ou AWS Profile
use_localstack      = false
aws_region          = "us-east-1"
environment         = "prod"
project             = "pod99"

# Credenciais: usar AWS_ACCESS_KEY_ID e AWS_SECRET_ACCESS_KEY env vars
# OU usar AWS Profile: aws-vault exec profile-name -- terraform apply
