# ==================================================================================
# API Gateway - HTTP API (Simplificado para LocalStack)
# ==================================================================================

# API Gateway REST API
resource "aws_apigatewayv2_api" "pod99_api" {
  name          = "pod99-authorization-api"
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
    Name = "pod99-api"
  }
}

# ==================================================================================
# API Gateway Stage
# ==================================================================================

resource "aws_apigatewayv2_stage" "api_stage" {
  api_id      = aws_apigatewayv2_api.pod99_api.id
  name        = "local"
  auto_deploy = true

  tags = {
    Name = "pod99-local"
  }
}

# ==================================================================================
# API Gateway Integration (HTTP Backend)
# ==================================================================================

resource "aws_apigatewayv2_integration" "app_integration" {
  api_id           = aws_apigatewayv2_api.pod99_api.id
  integration_type = "HTTP_PROXY"

  # LOCAL: apontar pra app rodando em localhost:8080
  integration_uri     = "http://host.docker.internal:8080"
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

# POST /v1/contratos/{id_contrato}/autorizacoes (SEM autenticação no API Gateway)
# Autenticação será feita no app via JwtAuthenticationFilter
resource "aws_apigatewayv2_route" "authorize_post" {
  api_id    = aws_apigatewayv2_api.pod99_api.id
  route_key = "POST /v1/contratos/{id_contrato}/autorizacoes"
  target    = "integrations/${aws_apigatewayv2_integration.app_integration.id}"
}

# POST /v1/contratos/authorize (Lambda Authorizer endpoint)
resource "aws_apigatewayv2_route" "authorize_endpoint" {
  api_id    = aws_apigatewayv2_api.pod99_api.id
  route_key = "POST /v1/contratos/authorize"
  target    = "integrations/${aws_apigatewayv2_integration.app_integration.id}"
}

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

# ==================================================================================
# CloudWatch Logs (para API Gateway)
# ==================================================================================

resource "aws_cloudwatch_log_group" "api_gateway_logs" {
  name              = "/aws/apigateway/pod99-local"
  retention_in_days = 7

  tags = {
    Name = "pod99-api-logs"
  }
}
