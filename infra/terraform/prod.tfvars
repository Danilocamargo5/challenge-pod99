# ==================================================================================
# Terraform Variables - PRODUCTION (AWS Real)
# ==================================================================================

# Use AWS Real (NOT LocalStack)
use_localstack = false

# AWS Configuration
# ⚠️ IMPORTANTE: Use environment variables ou AWS Profile
# export AWS_ACCESS_KEY_ID="..."
# export AWS_SECRET_ACCESS_KEY="..."
# OU: aws-vault exec profile-name -- terraform apply -var-file=prod.tfvars
aws_region = "us-east-1"

# Environment
environment = "prod"
project     = "pod99"

# DynamoDB
dynamodb_billing_mode           = "PROVISIONED"  # Use PROVISIONED em prod para melhor controle
dynamodb_point_in_time_recovery = true           # ⚠️ Ativar backup automático em prod

# SQS
sqs_message_retention_seconds   = 1209600        # 14 days (máximo)
sqs_visibility_timeout_seconds  = 300            # 5 minutes

# CloudWatch
cloudwatch_log_retention_days = 30  # Maior retenção em prod

# API Gateway
enable_api_gateway_logging = true

# Tags
tags = {
  Environment = "production"
  CostCenter  = "Engineering"
  ManagedBy   = "Terraform"
  Criticality = "High"
}
