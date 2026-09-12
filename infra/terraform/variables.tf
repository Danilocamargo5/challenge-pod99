# ==================================================================================
# Terraform Variables
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
  description = "AWS Access Key ID (local: 'test', prod: from AWS_ACCESS_KEY_ID env var)"
  type        = string
  default     = "test"
  sensitive   = true
}

variable "aws_secret_access_key" {
  description = "AWS Secret Access Key (local: 'test', prod: from AWS_SECRET_ACCESS_KEY env var)"
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
  description = "DynamoDB billing mode (PAY_PER_REQUEST or PROVISIONED)"
  type        = string
  default     = "PAY_PER_REQUEST"
}

variable "dynamodb_point_in_time_recovery" {
  description = "Enable Point-in-Time Recovery para DynamoDB"
  type        = bool
  default     = false
}

variable "sqs_message_retention_seconds" {
  description = "SQS message retention in seconds"
  type        = number
  default     = 86400
}

variable "sqs_visibility_timeout_seconds" {
  description = "SQS visibility timeout in seconds"
  type        = number
  default     = 300
}

variable "cloudwatch_log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 7
}

variable "enable_api_gateway_logging" {
  description = "Enable detailed API Gateway logging"
  type        = bool
  default     = true
}

variable "tags" {
  description = "Tags adicionais para todos os recursos"
  type        = map(string)
  default     = {}
}
