# ==================================================================================
# API Gateway Configuration - LocalStack (Local Development)
# ==================================================================================
# 🔧 IMPORTANTE: Este arquivo configura API Gateway no LocalStack
# Para rodar LOCALMENTE: terraform apply -var-file=local.tfvars
# Para rodar em AWS REAL: terraform apply -var-file=prod.tfvars

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region     = var.aws_region
  access_key = var.aws_access_key_id
  secret_key = var.aws_secret_access_key

  # 🔧 LocalStack Endpoints (quando rodar local)
  # Comentar para produção
  dynamic "endpoints" {
    for_each = var.use_localstack ? [1] : []
    content {
      apigateway    = "http://localhost:4566"
      lambda        = "http://localhost:4566"
      iam           = "http://localhost:4566"
      cloudwatch    = "http://localhost:4566"
      logs          = "http://localhost:4566"
      dynamodb      = "http://localhost:4566"
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
  description = "AWS Access Key"
  type        = string
  default     = "test"
  sensitive   = true
}

variable "aws_secret_access_key" {
  description = "AWS Secret Key"
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

# ==================================================================================
# Role para Lambda Execution (Lambda Authorizer)
# ==================================================================================
resource "aws_iam_role" "lambda_execution_role" {
  name = "${var.project}-lambda-execution-role-${var.environment}"

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

  tags = {
    Name = "${var.project}-lambda-execution-role"
  }
}

# Attach basic Lambda execution policy
resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  role       = aws_iam_role.lambda_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# ==================================================================================
# API Gateway - HTTP API
# ==================================================================================
resource "aws_apigatewayv2_api" "pod99_api" {
  name          = "${var.project}-api-${var.environment}"
  protocol_type = "HTTP"
  description   = "POD99 Transaction Authorization API"

  cors_configuration {
    allow_origins = ["*"]
    allow_methods = ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
    allow_headers = [
      "Content-Type",
      "Authorization",
      "Idempotency-Key",
      "X-Correlation-ID"
    ]
    expose_headers = [
      "X-Correlation-ID",
      "X-Trace-ID"
    ]
    max_age = 300
  }

  tags = {
    Name = "${var.project}-api"
  }
}

# ==================================================================================
# API Gateway Stage
# ==================================================================================
resource "aws_apigatewayv2_stage" "api_stage" {
  api_id      = aws_apigatewayv2_api.pod99_api.id
  name        = var.environment
  auto_deploy = true

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_gateway_logs.arn
    format = jsonencode({
      requestId          = "$context.requestId"
      ip                 = "$context.identity.sourceIp"
      requestTime        = "$context.requestTime"
      httpMethod         = "$context.httpMethod"
      resourcePath       = "$context.resourcePath"
      status             = "$context.status"
      responseLength     = "$context.responseLength"
      integrationLatency = "$context.integration.latency"
      error              = "$context.error.message"
    })
  }

  tags = {
    Name = "${var.project}-${var.environment}"
  }
}

# ==================================================================================
# CloudWatch Logs para API Gateway
# ==================================================================================
resource "aws_cloudwatch_log_group" "api_gateway_logs" {
  name              = "/aws/apigateway/${var.project}-${var.environment}"
  retention_in_days = 7

  tags = {
    Name = "${var.project}-api-logs"
  }
}

# ==================================================================================
# Lambda Authorizer (JWT Validation)
# ==================================================================================
# ⚠️ Para LocalStack, usar código inline simples
# Para produção, fazer build/deploy do JAR compilado
resource "aws_lambda_function" "authorizer" {
  filename      = "lambda-authorizer.zip"
  function_name = "${var.project}-authorizer-${var.environment}"
  role          = aws_iam_role.lambda_execution_role.arn
  handler       = "com.pod99.config.LambdaAuthorizerHandler::handleRequest"
  runtime       = "java21"
  timeout       = 30
  memory_size   = 256

  environment {
    variables = {
      ENVIRONMENT = var.environment
      LOG_LEVEL   = "INFO"
    }
  }

  tags = {
    Name = "${var.project}-authorizer"
  }
}

# Permission para API Gateway invocar Lambda
resource "aws_lambda_permission" "api_gateway_authorizer" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.authorizer.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.pod99_api.execution_arn}/*/*"
}

# ==================================================================================
# API Gateway Integration (Lambda HTTP Backend)
# ==================================================================================
# 🔧 IMPORTANTE: Integration aponta pro app rodando LOCAL (localhost:8080)
resource "aws_apigatewayv2_integration" "app_integration" {
  api_id           = aws_apigatewayv2_api.pod99_api.id
  integration_type = "HTTP_PROXY"

  # 🔧 LOCAL: apontar pra app rodando em localhost:8080
  # Em PRODUÇÃO: apontar pra Lambda ou ECS
  integration_uri = var.use_localstack ? "http://host.docker.internal:8080" : "arn:aws:lambda:${var.aws_region}:${data.aws_caller_identity.current.account_id}:function:pod99-app"

  payload_format_version = "2.0"
  timeout_milliseconds   = 30000
}

# ==================================================================================
# API Gateway Routes
# ==================================================================================

# Rota: POST /v1/contratos/{id_contrato}/autorizacoes
resource "aws_apigatewayv2_route" "authorize_post" {
  api_id    = aws_apigatewayv2_api.pod99_api.id
  route_key = "POST /v1/contratos/{id_contrato}/autorizacoes"
  target    = "integrations/${aws_apigatewayv2_integration.app_integration.id}"

  # ⚠️ Autorização via Lambda Authorizer (comentado por enquanto)
  # Usar JWT Authentication Filter local em vez disso
  # authorization_type = "CUSTOM"
  # authorizer_id      = aws_apigatewayv2_authorizer.jwt.id
}

# Health check (sem autenticação)
resource "aws_apigatewayv2_route" "health" {
  api_id    = aws_apigatewayv2_api.pod99_api.id
  route_key = "GET /health"
  target    = "integrations/${aws_apigatewayv2_integration.app_integration.id}"
}

# ==================================================================================
# Data sources
# ==================================================================================
data "aws_caller_identity" "current" {}

# ==================================================================================
# Outputs
# ==================================================================================
output "api_gateway_url" {
  description = "URL da API Gateway"
  value       = var.use_localstack ? "http://localhost:4566/restapis/${aws_apigatewayv2_api.pod99_api.id}/${var.environment}" : "${aws_apigatewayv2_stage.api_stage.invoke_url}"
}

output "api_gateway_id" {
  description = "ID da API Gateway"
  value       = aws_apigatewayv2_api.pod99_api.id
}

output "authorizer_function_arn" {
  description = "ARN da Lambda Authorizer"
  value       = aws_lambda_function.authorizer.arn
}

output "authorizer_function_name" {
  description = "Nome da Lambda Authorizer"
  value       = aws_lambda_function.authorizer.function_name
}

