# ==================================================================================
# API Gateway - REST API (Mínimo para funcionar)
# ==================================================================================

resource "aws_api_gateway_rest_api" "pod99_api" {
  name        = "pod99-authorization-api"
  description = "POD99 Transaction Authorization API"
}

# Recurso raiz
resource "aws_api_gateway_resource" "api_root" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  parent_id   = aws_api_gateway_rest_api.pod99_api.root_resource_id
  path_part   = "v1"
}

# Método GET /v1 (placeholder)
resource "aws_api_gateway_method" "api_method" {
  rest_api_id      = aws_api_gateway_rest_api.pod99_api.id
  resource_id      = aws_api_gateway_resource.api_root.id
  http_method      = "GET"
  authorization    = "NONE"
}

# Integração com Mock
resource "aws_api_gateway_integration" "api_integration" {
  rest_api_id      = aws_api_gateway_rest_api.pod99_api.id
  resource_id      = aws_api_gateway_resource.api_root.id
  http_method      = aws_api_gateway_method.api_method.http_method
  type             = "MOCK"
  request_templates = {
    "application/json" = "{\"statusCode\": 200}"
  }
}

# Deployment
resource "aws_api_gateway_deployment" "api_deployment" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id

  depends_on = [
    aws_api_gateway_integration.api_integration
  ]
}

# Stage
resource "aws_api_gateway_stage" "api_stage" {
  deployment_id = aws_api_gateway_deployment.api_deployment.id
  rest_api_id   = aws_api_gateway_rest_api.pod99_api.id
  stage_name    = "local"
}

output "api_gateway_url" {
  description = "URL da API Gateway"
  value       = aws_api_gateway_stage.api_stage.invoke_url
}

output "api_gateway_id" {
  description = "ID da API Gateway"
  value       = aws_api_gateway_rest_api.pod99_api.id
}
