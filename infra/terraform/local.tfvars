# ==================================================================================
# Terraform Variables - LOCAL (LocalStack)
# ==================================================================================

# Use LocalStack (local development)
use_localstack = true

# AWS Configuration
aws_region            = "us-east-1"
aws_access_key_id     = "test"
aws_secret_access_key = "test"

# Environment
environment = "local"
project     = "pod99"

# DynamoDB
dynamodb_billing_mode           = "PAY_PER_REQUEST"
dynamodb_point_in_time_recovery = false

# SQS
sqs_message_retention_seconds   = 86400  # 1 day
sqs_visibility_timeout_seconds  = 300    # 5 minutes

# CloudWatch
cloudwatch_log_retention_days = 7

# API Gateway
enable_api_gateway_logging = true

# Tags
tags = {
  Environment = "local"
  ManagedBy   = "Terraform"
  Purpose     = "Development"
}
