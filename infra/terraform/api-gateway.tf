# ==================================================================================
# API Gateway Configuration
# ==================================================================================
# Role para Lambda Execution (Lambda Authorizer)
resource "aws_iam_role" "lambda_execution_role" {
  name = "pod99-lambda-execution-role"

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
    Name        = "pod99-lambda-execution-role"
    Environment = var.environment
    Project     = var.project
  }
}

# Attach basic Lambda execution policy
resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  role       = aws_iam_role.lambda_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# ==================================================================================
# API Gateway - REST API
# ==================================================================================
resource "aws_apigatewayv2_api" "pod99_api" {
  name          = "${var.project}-api"
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
    Name        = "${var.project}-api"
    Environment = var.environment
    Project     = var.project
  }
}

# ==================================================================================
# API Gateway Stage
# ==================================================================================
resource "aws_apigatewayv2_stage" "prod" {
  api_id      = aws_apigatewayv2_api.pod99_api.id
  name        = var.environment
  auto_deploy = true

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_gateway_logs.arn
    format = jsonencode({
      requestId      = "$context.requestId"
      ip             = "$context.identity.sourceIp"
      requestTime    = "$context.requestTime"
      httpMethod     = "$context.httpMethod"
      resourcePath   = "$context.resourcePath"
      status         = "$context.status"
      protocol       = "$context.protocol"
      responseLength = "$context.responseLength"
      integrationLatency = "$context.integration.latency"
      error          = "$context.error.message"
      errorType      = "$context.error.messageString"
    })
  }

  tags = {
    Name        = "${var.project}-${var.environment}"
    Environment = var.environment
    Project     = var.project
  }
}

# ==================================================================================
# CloudWatch Logs para API Gateway
# ==================================================================================
resource "aws_cloudwatch_log_group" "api_gateway_logs" {
  name              = "/aws/apigateway/${var.project}-${var.environment}"
  retention_in_days = 7

  tags = {
    Name        = "${var.project}-api-logs"
    Environment = var.environment
    Project     = var.project
  }
}

# ==================================================================================
# Lambda Authorizer (para JWT validation)
# ==================================================================================
# Nota: Em produção, fazer build/deploy do JAR compilado
# Para MVP: usar uma função placeholder que retorna Always Allow
resource "aws_lambda_function" "authorizer" {
  filename      = "lambda-authorizer.zip"
  function_name = "${var.project}-authorizer"
  role          = aws_iam_role.lambda_execution_role.arn
  handler       = "com.pod99.config.LambdaAuthorizerHandler::handleRequest"
  runtime       = "java21"
  timeout       = 30

  environment {
    variables = {
      ENVIRONMENT = var.environment
      LOG_LEVEL   = "INFO"
    }
  }

  tags = {
    Name        = "${var.project}-authorizer"
    Environment = var.environment
    Project     = var.project
  }
}

# Permission para API Gateway invocar Lambda Authorizer
resource "aws_lambda_permission" "api_gateway_authorizer" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.authorizer.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.pod99_api.execution_arn}/*/*"
}

# ==================================================================================
# API Gateway Integration (Lambda Authorizer)
# ==================================================================================
# ⚠️ NOTA: HTTP API Gateway (v2) usa um modelo de autorizador diferente de REST API (v1)
# Para JWT simples, usar autorização nativa do API Gateway ou custom authorizer

# ==================================================================================
# API Gateway - Rotas
# ==================================================================================
# Rota para POST /v1/contratos/{id_contrato}/autorizacoes
resource "aws_apigatewayv2_integration" "app_integration" {
  api_id           = aws_apigatewayv2_api.pod99_api.id
  integration_type = "HTTP_PROXY"
  
  # ⚠️ Em produção: apontar para Lambda function ARN ou ECS task
  # Para MVP: apontar para localhost ou HTTP backend
  integration_uri = "http://localhost:8080"
  
  payload_format_version = "2.0"
  timeout_milliseconds   = 30000
}

# Rota POST
resource "aws_apigatewayv2_route" "authorize_post" {
  api_id    = aws_apigatewayv2_api.pod99_api.id
  route_key = "POST /v1/contratos/{id_contrato}/autorizacoes"
  target    = "integrations/${aws_apigatewayv2_integration.app_integration.id}"

  # ⚠️ Adicionar autorização se implementar authorizer
  # authorization_type = "CUSTOM"
  # authorizer_id      = aws_apigatewayv2_authorizer.jwt.id
}

# ==================================================================================
# API Gateway - Outputs
# ==================================================================================
output "api_gateway_url" {
  description = "URL da API Gateway"
  value       = "${aws_apigatewayv2_stage.prod.invoke_url}"
}

output "api_gateway_id" {
  description = "ID da API Gateway"
  value       = aws_apigatewayv2_api.pod99_api.id
}

output "authorizer_function_arn" {
  description = "ARN da Lambda Authorizer"
  value       = aws_lambda_function.authorizer.arn
}
