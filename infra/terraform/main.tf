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
    tags = local.common_tags
  }
}

# ==================================================================================
# Data Sources
# ==================================================================================

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}
