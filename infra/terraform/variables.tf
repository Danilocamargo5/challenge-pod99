variable "aws_region" {
  description = "Região AWS para deployment"
  type        = string
  default     = "us-east-1"

  validation {
    condition     = can(regex("^[a-z]{2}-[a-z]+-\\d{1}$", var.aws_region))
    error_message = "Região AWS deve estar em formato válido (ex: us-east-1)"
  }
}

variable "environment" {
  description = "Ambiente de deployment (dev, staging, prod)"
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment deve ser dev, staging ou prod"
  }
}

variable "app_name" {
  description = "Nome da aplicação (usado em nomes de recursos)"
  type        = string
  default     = "pod99"

  validation {
    condition     = can(regex("^[a-z0-9-]{1,32}$", var.app_name))
    error_message = "App name deve ser lowercase, números e hífens (max 32 chars)"
  }
}

# ============================================================================
# DynamoDB Configuration
# ============================================================================

variable "enable_pitr" {
  description = "Ativar Point-In-Time Recovery no DynamoDB"
  type        = bool
  default     = true
}

variable "dynamodb_billing_mode" {
  description = "Modo de billing do DynamoDB (PROVISIONED ou PAY_PER_REQUEST)"
  type        = string
  default     = "PAY_PER_REQUEST"

  validation {
    condition     = contains(["PROVISIONED", "PAY_PER_REQUEST"], var.dynamodb_billing_mode)
    error_message = "Billing mode deve ser PROVISIONED ou PAY_PER_REQUEST"
  }
}

# ============================================================================
# SQS Configuration
# ============================================================================

variable "sqs_message_retention_seconds" {
  description = "Tempo de retenção de mensagens em SQS (segundos)"
  type        = number
  default     = 86400  # 24 horas

  validation {
    condition     = var.sqs_message_retention_seconds >= 60 && var.sqs_message_retention_seconds <= 1209600
    error_message = "Message retention deve estar entre 60 e 1209600 segundos"
  }
}

variable "sqs_max_receive_count" {
  description = "Número máximo de vezes que uma mensagem é retentada antes de ir para DLQ"
  type        = number
  default     = 3

  validation {
    condition     = var.sqs_max_receive_count >= 1 && var.sqs_max_receive_count <= 10
    error_message = "Max receive count deve estar entre 1 e 10"
  }
}

# ============================================================================
# API Gateway Configuration
# ============================================================================

variable "api_rate_limit" {
  description = "Rate limit da API (requisições por segundo)"
  type        = number
  default     = 1000

  validation {
    condition     = var.api_rate_limit >= 1 && var.api_rate_limit <= 10000
    error_message = "Rate limit deve estar entre 1 e 10000"
  }
}

variable "api_burst_limit" {
  description = "Burst limit da API (requisições simultâneas)"
  type        = number
  default     = 100

  validation {
    condition     = var.api_burst_limit >= 1 && var.api_burst_limit <= 5000
    error_message = "Burst limit deve estar entre 1 e 5000"
  }
}

variable "api_quota_limit" {
  description = "Quota diária total de requisições"
  type        = number
  default     = 1000000

  validation {
    condition     = var.api_quota_limit >= 100 && var.api_quota_limit <= 10000000
    error_message = "Quota limit deve estar entre 100 e 10000000"
  }
}

# ============================================================================
# Authorization Configuration
# ============================================================================

variable "cognito_user_pool_id" {
  description = "ID do Cognito User Pool para OAuth2"
  type        = string
  default     = ""
  sensitive   = true
}

variable "cognito_client_id" {
  description = "Client ID do Cognito para OAuth2"
  type        = string
  default     = ""
  sensitive   = true
}

# ============================================================================
# Network Configuration
# ============================================================================

variable "nlb_dns_name" {
  description = "DNS name do Network Load Balancer (para integração com ALB)"
  type        = string
  default     = "localhost:8080"
}

# ============================================================================
# Monitoring & Logging
# ============================================================================

variable "enable_xray" {
  description = "Ativar AWS X-Ray tracing"
  type        = bool
  default     = true
}

variable "enable_detailed_monitoring" {
  description = "Ativar CloudWatch Detailed Monitoring"
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "Dias de retenção de logs no CloudWatch"
  type        = number
  default     = 7

  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1827, 3653], var.log_retention_days)
    error_message = "Log retention deve ser um valor válido do CloudWatch"
  }
}

variable "alarm_sns_topic_arn" {
  description = "ARN do SNS topic para notificações de alarmes"
  type        = string
  default     = ""
}

# ============================================================================
# Tags
# ============================================================================

variable "additional_tags" {
  description = "Tags adicionais a serem aplicadas em todos os recursos"
  type        = map(string)
  default = {
    CostCenter = "Engineering"
    Owner      = "POD99-Team"
  }
}
