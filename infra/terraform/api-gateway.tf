# ==================================================================================
# API Gateway - REST API (v1)
# ==================================================================================

resource "aws_api_gateway_rest_api" "pod99_api" {
  name        = "pod99-authorization-api"
  description = "POD99 Transaction Authorization API"
  
  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

resource "aws_api_gateway_deployment" "api_deployment" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  stage_name  = "local"
  
  depends_on = [
    aws_api_gateway_rest_api.pod99_api
  ]
}

output "api_gateway_url" {
  description = "URL da API Gateway"
  value       = "https://${aws_api_gateway_rest_api.pod99_api.id}.execute-api.${var.aws_region}.amazonaws.com/local"
}

output "api_gateway_id" {
  description = "ID da API Gateway"
  value       = aws_api_gateway_rest_api.pod99_api.id
}
