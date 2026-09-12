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

  default_tags {
    tags = {
      Environment = var.environment
      Project     = var.project
      ManagedBy   = "Terraform"
    }
  }
}

# ==================================================================================
# Variables
# ==================================================================================

variable "use_localstack" {
  description = "Usar LocalStack (local) ou AWS real (prod)"
  type        = bool
  default     = true
}

variable "aws_region" {
  description = "AWS Region"
  type        = string
  default     = "us-east-1"
}

variable "aws_access_key_id" {
  description = "AWS Access Key (local: test, prod: from env)"
  type        = string
  default     = "test"
  sensitive   = true
}

variable "aws_secret_access_key" {
  description = "AWS Secret Key (local: test, prod: from env)"
  type        = string
  default     = "test"
  sensitive   = true
}

variable "environment" {
  description = "Environment (local, dev, prod)"
  type        = string
  default     = "local"
}

variable "project" {
  description = "Project name"
  type        = string
  default     = "pod99"
}

variable "dynamodb_billing_mode" {
  description = "DynamoDB billing mode"
  type        = string
  default     = "PAY_PER_REQUEST"
}

# ==================================================================================
# Data Sources
# ==================================================================================

data "aws_caller_identity" "current" {}

# ==================================================================================
# Local values
# ==================================================================================

locals {
  # Nomes das tabelas
  table_limits          = "${var.project}-limits"
  table_authorizations  = "${var.project}-authorizations"
  table_accounting      = "${var.project}-accounting"
  table_locks           = "${var.project}-locks"
  table_rate_limit      = "${var.project}-rate-limit"
  
  # Nomes das filas
  queue_accounting      = "${var.project}-accounting-queue"
  queue_accounting_dlq  = "${var.project}-accounting-dlq"
  
  # Nomes das rules
  rule_transacao_autorizada = "${var.project}-transacao-autorizada-rule"
}
