# ==================================================================================
# Lambda Authorizer for API Gateway
# ==================================================================================

# 🔐 Authorizer que aponta pra endpoint HTTP do app
# POST /authorize
resource "aws_apigatewayv2_authorizer" "jwt_authorizer" {
  api_id           = aws_apigatewayv2_api.pod99_api.id
  authorizer_type  = "HTTP"
  name             = "${var.project}-jwt-authorizer-${var.environment}"
  identity_sources = ["$request.header.Authorization"]
  
  # Apontar pra app Spring Boot (local: host.docker.internal:8080)
  authorizer_uri = var.use_localstack ? "http://host.docker.internal:8080/v1/contratos/authorize" : "http://localhost:8080/v1/contratos/authorize"
  
  # Cache de autorização (5 minutos)
  authorizer_result_ttl_in_seconds = 300
  
  # Ativar logs de autorização
  enable_simple_responses = false
}

# ==================================================================================
# Atualizar routes pra usar o authorizer
# ==================================================================================

# Route: POST /v1/contratos/{id_contrato}/autorizacoes (COM AUTENTICAÇÃO)
resource "aws_apigatewayv2_route" "authorize_post_secured" {
  api_id             = aws_apigatewayv2_api.pod99_api.id
  route_key          = "POST /v1/contratos/{id_contrato}/autorizacoes"
  target             = "integrations/${aws_apigatewayv2_integration.app_integration.id}"
  authorization_type = "CUSTOM"
  authorizer_id      = aws_apigatewayv2_authorizer.jwt_authorizer.id
}

# Route: POST /v1/contratos/authorize (SEM AUTENTICAÇÃO - é o próprio authorizer!)
resource "aws_apigatewayv2_route" "authorize_endpoint" {
  api_id    = aws_apigatewayv2_api.pod99_api.id
  route_key = "POST /v1/contratos/authorize"
  target    = "integrations/${aws_apigatewayv2_integration.app_integration.id}"
}

# ==================================================================================
# Outputs
# ==================================================================================

output "lambda_authorizer_id" {
  description = "ID do Lambda Authorizer"
  value       = aws_apigatewayv2_authorizer.jwt_authorizer.id
}

output "lambda_authorizer_uri" {
  description = "URI do Lambda Authorizer"
  value       = aws_apigatewayv2_authorizer.jwt_authorizer.authorizer_uri
}

output "api_gateway_authorize_url" {
  description = "URL do endpoint de autorização"
  value       = "${aws_apigatewayv2_stage.api_stage.invoke_url}/v1/contratos/authorize"
}
