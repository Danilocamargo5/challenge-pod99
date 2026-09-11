terraform {
  required_version = ">= 1.5"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # backend "s3" {
  #   bucket         = "pod99-terraform-state"
  #   key            = "prod/terraform.tfstate"
  #   region         = "us-east-1"
  #   encrypt        = true
  #   dynamodb_table = "terraform-locks"
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "POD99"
      Environment = var.environment
      ManagedBy   = "Terraform"
      CreatedAt   = timestamp()
    }
  }
}

# ============================================================================
# DynamoDB Tables
# ============================================================================

# Tabela: Limites por Contrato
resource "aws_dynamodb_table" "limits" {
  name           = "${var.app_name}-limits"
  billing_mode   = "PAY_PER_REQUEST"  # Autoscale automático
  hash_key       = "id_contrato"
  
  attribute {
    name = "id_contrato"
    type = "S"
  }

  ttl {
    attribute_name = "expiry_time"
    enabled        = true
  }

  point_in_time_recovery {
    enabled = var.enable_pitr
  }

  tags = {
    Name        = "POD99 Limits Table"
    Description = "Gerencia limites de crédito por contrato"
  }
}

# Tabela: Autorizações
resource "aws_dynamodb_table" "authorizations" {
  name           = "${var.app_name}-authorizations"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "id_autorizacao"
  
  attribute {
    name = "id_autorizacao"
    type = "S"
  }

  attribute {
    name = "id_contrato"
    type = "S"
  }

  # GSI para queries por contrato
  global_secondary_index {
    name            = "IdContratoIndex"
    hash_key        = "id_contrato"
    projection_type = "ALL"
  }

  ttl {
    attribute_name = "expiry_time"
    enabled        = true
  }

  point_in_time_recovery {
    enabled = var.enable_pitr
  }

  tags = {
    Name        = "POD99 Authorizations Table"
    Description = "Histórico de autorizações"
  }
}

# Tabela: Lançamentos Contábeis
resource "aws_dynamodb_table" "accounting" {
  name           = "${var.app_name}-accounting"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "event_id"
  
  attribute {
    name = "event_id"
    type = "S"
  }

  attribute {
    name = "id_autorizacao"
    type = "S"
  }

  # GSI para queries por autorização
  global_secondary_index {
    name            = "IdAutorizacaoIndex"
    hash_key        = "id_autorizacao"
    projection_type = "ALL"
  }

  ttl {
    attribute_name = "expiry_time"
    enabled        = true
  }

  point_in_time_recovery {
    enabled = var.enable_pitr
  }

  tags = {
    Name        = "POD99 Accounting Table"
    Description = "Lançamentos contábeis desacoplados"
  }
}

# Tabela: Distributed Locks
resource "aws_dynamodb_table" "locks" {
  name           = "${var.app_name}-locks"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "lock_key"
  
  attribute {
    name = "lock_key"
    type = "S"
  }

  ttl {
    attribute_name = "expiry_time"
    enabled        = true
  }

  tags = {
    Name        = "POD99 Locks Table"
    Description = "Locks distribuídos para concorrência"
  }
}

# ============================================================================
# SQS Queues
# ============================================================================

# DLQ para contabilidade
resource "aws_sqs_queue" "accounting_dlq" {
  name                      = "${var.app_name}-accounting-dlq"
  message_retention_seconds = 1209600  # 14 dias
  
  tags = {
    Name        = "POD99 Accounting DLQ"
    Description = "Dead Letter Queue para eventos de contabilidade"
  }
}

# Fila principal: Contabilidade
resource "aws_sqs_queue" "accounting" {
  name                       = "${var.app_name}-accounting-queue"
  visibility_timeout_seconds = 300  # 5 minutos
  message_retention_seconds  = 86400  # 24 horas
  
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.accounting_dlq.arn
    maxReceiveCount     = 3
  })

  tags = {
    Name        = "POD99 Accounting Queue"
    Description = "Fila de eventos de autorização para contabilidade"
  }
}

# Fila: Antifraude
resource "aws_sqs_queue" "fraud" {
  name                       = "${var.app_name}-fraud-queue"
  visibility_timeout_seconds = 300
  message_retention_seconds  = 86400

  tags = {
    Name        = "POD99 Fraud Queue"
    Description = "Fila para análise de antifraude"
  }
}

# Fila: Notificações
resource "aws_sqs_queue" "notifications" {
  name                       = "${var.app_name}-notifications-queue"
  visibility_timeout_seconds = 60
  message_retention_seconds  = 3600  # 1 hora

  tags = {
    Name        = "POD99 Notifications Queue"
    Description = "Fila para envio de notificações"
  }
}

# ============================================================================
# EventBridge Rule
# ============================================================================

# Event Bus (default)
resource "aws_cloudwatch_event_bus" "pod99" {
  name = "${var.app_name}-event-bus"

  tags = {
    Name = "POD99 Event Bus"
  }
}

# Rule: Roteia eventos de autorização
resource "aws_cloudwatch_event_rule" "transacao_autorizada" {
  name           = "${var.app_name}-transacao-autorizada-rule"
  event_bus_name = aws_cloudwatch_event_bus.pod99.name
  
  event_pattern = jsonencode({
    source      = ["pod99.authorization"]
    detail-type = ["TransacaoAutorizada"]
  })

  tags = {
    Name = "POD99 Transação Autorizada Rule"
  }
}

# Target: Accounting Queue
resource "aws_cloudwatch_event_target" "accounting_target" {
  rule           = aws_cloudwatch_event_rule.transacao_autorizada.name
  event_bus_name = aws_cloudwatch_event_bus.pod99.name
  target_id      = "AccountingQueue"
  arn            = aws_sqs_queue.accounting.arn

  input_transformer {
    input_paths = {
      event_id = "$.detail.event_id"
    }
    input_template = jsonencode({
      event_id = "<event_id>"
      detail   = "$.detail"
    })
  }
}

# Target: Fraud Queue
resource "aws_cloudwatch_event_target" "fraud_target" {
  rule           = aws_cloudwatch_event_rule.transacao_autorizada.name
  event_bus_name = aws_cloudwatch_event_bus.pod99.name
  target_id      = "FraudQueue"
  arn            = aws_sqs_queue.fraud.arn
}

# Target: Notifications Queue
resource "aws_cloudwatch_event_target" "notifications_target" {
  rule           = aws_cloudwatch_event_rule.transacao_autorizada.name
  event_bus_name = aws_cloudwatch_event_bus.pod99.name
  target_id      = "NotificationsQueue"
  arn            = aws_sqs_queue.notifications.arn
}

# ============================================================================
# API Gateway (REST API)
# ============================================================================

resource "aws_api_gateway_rest_api" "pod99" {
  name        = "${var.app_name}-api"
  description = "POD99 Authorization API"

  endpoint_configuration {
    types = ["REGIONAL"]
  }

  tags = {
    Name = "POD99 REST API"
  }
}

# Resource: /v1
resource "aws_api_gateway_resource" "v1" {
  rest_api_id = aws_api_gateway_rest_api.pod99.id
  parent_id   = aws_api_gateway_rest_api.pod99.root_resource_id
  path_part   = "v1"
}

# Resource: /v1/contratos
resource "aws_api_gateway_resource" "contratos" {
  rest_api_id = aws_api_gateway_rest_api.pod99.id
  parent_id   = aws_api_gateway_resource.v1.id
  path_part   = "contratos"
}

# Resource: /v1/contratos/{id_contrato}
resource "aws_api_gateway_resource" "contrato_id" {
  rest_api_id = aws_api_gateway_rest_api.pod99.id
  parent_id   = aws_api_gateway_resource.contratos.id
  path_part   = "{id_contrato}"
}

# Resource: /v1/contratos/{id_contrato}/autorizacoes
resource "aws_api_gateway_resource" "autorizacoes" {
  rest_api_id = aws_api_gateway_rest_api.pod99.id
  parent_id   = aws_api_gateway_resource.contrato_id.id
  path_part   = "autorizacoes"
}

# ============================================================================
# API Gateway: Usage Plan & API Key (Rate Limiting)
# ============================================================================

# API Key
resource "aws_api_gateway_api_key" "pod99_client" {
  name        = "${var.app_name}-api-key"
  description = "POD99 Client API Key"
  enabled     = true

  tags = {
    Name = "POD99 API Key"
  }
}

# Usage Plan (Rate Limiting)
resource "aws_api_gateway_usage_plan" "pod99" {
  name        = "${var.app_name}-usage-plan"
  description = "POD99 Standard Usage Plan"

  api_stages {
    api_id      = aws_api_gateway_rest_api.pod99.id
    stage_name  = aws_api_gateway_stage.prod.stage_name
  }

  throttle_settings {
    burst_limit = var.api_burst_limit
    rate_limit  = var.api_rate_limit
  }

  quota_settings {
    limit  = var.api_quota_limit
    period = "DAY"
  }

  tags = {
    Name = "POD99 Usage Plan"
  }
}

# Associar API Key ao Usage Plan
resource "aws_api_gateway_usage_plan_key" "pod99" {
  key_id        = aws_api_gateway_api_key.pod99_client.id
  key_type      = "API_KEY"
  usage_plan_id = aws_api_gateway_usage_plan.pod99.id
}

# ============================================================================
# Lambda Authorizer (OAuth2 / JWT validation)
# ============================================================================

# IAM Role para Lambda Authorizer
resource "aws_iam_role" "lambda_authorizer_role" {
  name = "${var.app_name}-lambda-authorizer-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

# Policy para Lambda Authorizer
resource "aws_iam_role_policy" "lambda_authorizer_policy" {
  name = "${var.app_name}-lambda-authorizer-policy"
  role = aws_iam_role.lambda_authorizer_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:${var.aws_region}:*:*"
      }
    ]
  })
}

# Lambda Authorizer Function
resource "aws_lambda_function" "authorizer" {
  filename      = "lambda_authorizer.zip"
  function_name = "${var.app_name}-authorizer"
  role          = aws_iam_role.lambda_authorizer_role.arn
  handler       = "index.handler"
  runtime       = "nodejs18.x"
  timeout       = 5

  environment {
    variables = {
      COGNITO_USER_POOL_ID = var.cognito_user_pool_id
      COGNITO_CLIENT_ID    = var.cognito_client_id
    }
  }

  tags = {
    Name = "POD99 Lambda Authorizer"
  }
}

# Autorizer no API Gateway
resource "aws_api_gateway_authorizer" "pod99" {
  name                   = "${var.app_name}-authorizer"
  rest_api_id            = aws_api_gateway_rest_api.pod99.id
  authorizer_uri         = aws_lambda_function.authorizer.invoke_arn
  authorizer_credentials = aws_iam_role.authorizer_invocation_role.arn
  type                   = "TOKEN"
  identity_source        = "method.request.header.Authorization"
  authorizer_result_ttl_in_seconds = 300

  depends_on = [aws_api_gateway_rest_api.pod99]
}

# IAM Role para invocar Lambda Authorizer
resource "aws_iam_role" "authorizer_invocation_role" {
  name = "${var.app_name}-api-gateway-authorizer-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "apigateway.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "authorizer_invocation_policy" {
  name = "${var.app_name}-api-gateway-authorizer-policy"
  role = aws_iam_role.authorizer_invocation_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "lambda:InvokeFunction"
        ]
        Resource = aws_lambda_function.authorizer.arn
      }
    ]
  })
}

# Lambda permission para API Gateway invocar
resource "aws_lambda_permission" "api_gateway_authorizer" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.authorizer.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.pod99.execution_arn}/authorizers/*"
}

# ============================================================================
# API Gateway: Deployment & Stage
# ============================================================================

# Method: POST /v1/contratos/{id_contrato}/autorizacoes
resource "aws_api_gateway_method" "authorize" {
  rest_api_id      = aws_api_gateway_rest_api.pod99.id
  resource_id      = aws_api_gateway_resource.autorizacoes.id
  http_method      = "POST"
  authorization    = "CUSTOM"
  authorizer_id    = aws_api_gateway_authorizer.pod99.id
  api_key_required = true

  request_parameters = {
    "method.request.header.Idempotency-Key"     = true
    "method.request.header.X-Trace-ID"          = false
    "method.request.header.X-Correlation-ID"    = false
  }
}

# Integration: POST → ALB/Lambda
resource "aws_api_gateway_integration" "authorize" {
  rest_api_id      = aws_api_gateway_rest_api.pod99.id
  resource_id      = aws_api_gateway_resource.autorizacoes.id
  http_method      = aws_api_gateway_method.authorize.http_method
  type             = "HTTP_PROXY"
  uri              = "http://${var.nlb_dns_name}/v1/contratos/{id_contrato}/autorizacoes"
  integration_http_method = "POST"

  request_parameters = {
    "integration.request.path.id_contrato" = "method.request.path.id_contrato"
  }
}

# Deployment
resource "aws_api_gateway_deployment" "pod99" {
  rest_api_id = aws_api_gateway_rest_api.pod99.id
  depends_on  = [aws_api_gateway_integration.authorize]
}

# Stage: Produção
resource "aws_api_gateway_stage" "prod" {
  deployment_id = aws_api_gateway_deployment.pod99.id
  rest_api_id   = aws_api_gateway_rest_api.pod99.id
  stage_name    = var.environment

  variables = {
    environment = var.environment
  }

  access_log_settings {
    cloudwatch_log_group_arn = aws_cloudwatch_log_group.api_gateway.arn
    format = "$context.requestId $context.extendedRequestId $context.identity.sourceIp $context.requestTime $context.routeKey $context.status $context.error.message $context.error.messageString"
  }

  xray_tracing_enabled = true

  depends_on = [aws_cloudwatch_log_group.api_gateway]
}

# CloudWatch Log Group para API Gateway
resource "aws_cloudwatch_log_group" "api_gateway" {
  name              = "/aws/apigateway/${var.app_name}"
  retention_in_days = 7

  tags = {
    Name = "POD99 API Gateway Logs"
  }
}

# ============================================================================
# CloudWatch Alarms
# ============================================================================

# Alarme: DLQ messages
resource "aws_cloudwatch_metric_alarm" "accounting_dlq_messages" {
  alarm_name          = "${var.app_name}-accounting-dlq-messages"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "1"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = "300"
  statistic           = "Average"
  threshold           = "10"
  alarm_description   = "Alerta quando há mensagens em DLQ"
  treat_missing_data  = "notBreaching"

  dimensions = {
    QueueName = aws_sqs_queue.accounting_dlq.name
  }

  alarm_actions = var.alarm_sns_topic_arn != "" ? [var.alarm_sns_topic_arn] : []
}

# Alarme: API Gateway 5xx errors
resource "aws_cloudwatch_metric_alarm" "api_5xx_errors" {
  alarm_name          = "${var.app_name}-api-5xx-errors"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "2"
  metric_name         = "5XXError"
  namespace           = "AWS/ApiGateway"
  period              = "60"
  statistic           = "Sum"
  threshold           = "10"
  alarm_description   = "Alerta quando há erros 5xx na API"
  treat_missing_data  = "notBreaching"

  dimensions = {
    ApiName = aws_api_gateway_rest_api.pod99.name
    Stage   = aws_api_gateway_stage.prod.stage_name
  }

  alarm_actions = var.alarm_sns_topic_arn != "" ? [var.alarm_sns_topic_arn] : []
}

# ============================================================================
# Outputs
# ============================================================================

output "api_endpoint" {
  description = "API Gateway endpoint URL"
  value       = aws_api_gateway_stage.prod.invoke_url
}

output "api_key" {
  description = "API Key para chamadas"
  value       = aws_api_gateway_api_key.pod99_client.value
  sensitive   = true
}

output "dynamodb_tables" {
  description = "DynamoDB tables criadas"
  value = {
    limits        = aws_dynamodb_table.limits.name
    authorizations = aws_dynamodb_table.authorizations.name
    accounting    = aws_dynamodb_table.accounting.name
    locks         = aws_dynamodb_table.locks.name
  }
}

output "sqs_queues" {
  description = "SQS queues criadas"
  value = {
    accounting = aws_sqs_queue.accounting.url
    fraud      = aws_sqs_queue.fraud.url
    notifications = aws_sqs_queue.notifications.url
  }
}

output "event_bus" {
  description = "EventBridge Event Bus"
  value       = aws_cloudwatch_event_bus.pod99.name
}
