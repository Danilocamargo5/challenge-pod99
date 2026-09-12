# ==================================================================================
# API Gateway - HTTP API
# ==================================================================================

# API Gateway REST API
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
      "X-Correlation-ID",
      "X-Trace-ID"
    ]
    expose_headers = [
      "X-Correlation-ID",
      "X-Trace-ID",
      "X-Account-Id"
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
      accountId          = "$context.authorizer.accountId"
    })
  }

  tags = {
    Name = "${var.project}-${var.environment}"
  }
}

# ==================================================================================
# CloudWatch Logs
# ==================================================================================

resource "aws_cloudwatch_log_group" "api_gateway_logs" {
  name              = "/aws/apigateway/${var.project}-${var.environment}"
  retention_in_days = 7

  tags = {
    Name = "${var.project}-api-logs"
  }
}

# ==================================================================================
# API Gateway Integration (HTTP Backend)
# ==================================================================================

resource "aws_apigatewayv2_integration" "app_integration" {
  api_id           = aws_apigatewayv2_api.pod99_api.id
  integration_type = "HTTP_PROXY"

  # LOCAL: apontar pra app rodando em localhost:8080
  # via host.docker.internal (acesso de Docker pro host)
  integration_uri     = var.use_localstack ? "http://host.docker.internal:8080" : "http://localhost:8080"
  payload_format_version = "2.0"
  timeout_milliseconds   = 30000
}

# ==================================================================================
# API Gateway Routes
# ==================================================================================

# Health check (sem autenticação)
resource "aws_apigatewayv2_route" "health" {
  api_id    = aws_apigatewayv2_api.pod99_api.id
  route_key = "GET /health"
  target    = "integrations/${aws_apigatewayv2_integration.app_integration.id}"
}

# ℹ️ Routes estão em lambda-authorizer.tf (com autenticação ativa)

# ==================================================================================
# Outputs
# ==================================================================================

output "api_gateway_url" {
  description = "URL da API Gateway"
  value       = "${aws_apigatewayv2_stage.api_stage.invoke_url}"
}

output "api_gateway_id" {
  description = "ID da API Gateway"
  value       = aws_apigatewayv2_api.pod99_api.id
}

output "api_gateway_endpoint" {
  description = "Endpoint da API Gateway (completo)"
  value       = "${aws_apigatewayv2_stage.api_stage.invoke_url}/v1/contratos/{id_contrato}/autorizacoes"
}
