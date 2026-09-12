terraform {
  required_version = ">= 1.0"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    null = {
      source  = "hashicorp/null"
      version = "~> 3.0"
    }
  }
}

# ==================================================================================
# Provider AWS Configuration
# ==================================================================================
provider "aws" {
  region     = var.aws_region
  access_key = var.aws_access_key_id
  secret_key = var.aws_secret_access_key

  # 🔧 Apontar pro LocalStack quando estiver em ambiente local
  dynamic "endpoints" {
    for_each = var.use_localstack ? [1] : []
    content {
      apigateway   = "http://localhost:4566"
      lambda       = "http://localhost:4566"
      sqs          = "http://localhost:4566"
      dynamodb     = "http://localhost:4566"
      events       = "http://localhost:4566"
      cloudwatch   = "http://localhost:4566"
      logs         = "http://localhost:4566"
      iam          = "http://localhost:4566"
    }
  }

  # Desabilitar validação de credenciais quando usar LocalStack
  skip_credentials_validation = var.use_localstack
  skip_requesting_account_id  = var.use_localstack
  skip_region_validation      = var.use_localstack

  default_tags {
    tags = local.common_tags
  }
}

# ==================================================================================
# Data Sources
# ==================================================================================

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

# ==================================================================================
# Local Values
# ==================================================================================

locals {
  # Naming convention
  name_prefix = "${var.project}-${var.environment}"
  
  # Table names (sem referência circular - valores literais)
  table_limits         = "pod99-limits"
  table_authorizations = "pod99-authorizations"
  table_accounting     = "pod99-accounting"
  table_locks          = "pod99-locks"
  table_rate_limit     = "pod99-rate-limit"
  
  # Queue names
  queue_accounting     = "pod99-accounting-queue"
  queue_accounting_dlq = "pod99-accounting-dlq"
  
  # Rule names
  rule_transacao_autorizada = "pod99-transacao-autorizada-rule"
  
  # Common tags
  common_tags = {
    Environment = var.environment
    Project     = var.project
    ManagedBy   = "Terraform"
  }
}
