# ==================================================================================
# API Gateway - REST API com integração HTTP e Lambda Authorizer
# ==================================================================================

resource "aws_api_gateway_rest_api" "pod99_api" {
  name        = "pod99-authorization-api"
  description = "POD99 Transaction Authorization API"
  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

# ==================================================================================
# RECURSOS
# ==================================================================================

resource "aws_api_gateway_resource" "v1" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  parent_id   = aws_api_gateway_rest_api.pod99_api.root_resource_id
  path_part   = "v1"
}

resource "aws_api_gateway_resource" "contratos" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  parent_id   = aws_api_gateway_resource.v1.id
  path_part   = "contratos"
}

resource "aws_api_gateway_resource" "contrato_id" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  parent_id   = aws_api_gateway_resource.contratos.id
  path_part   = "{idContrato}"
}

resource "aws_api_gateway_resource" "autorizacoes" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  parent_id   = aws_api_gateway_resource.contrato_id.id
  path_part   = "autorizacoes"
}

resource "aws_api_gateway_resource" "authorize" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  parent_id   = aws_api_gateway_resource.contratos.id
  path_part   = "authorize"
}

# ==================================================================================
# LAMBDA AUTHORIZER (ANTES dos métodos que usam)
# ==================================================================================

resource "aws_api_gateway_authorizer" "lambda_authorizer" {
  name            = "pod99-lambda-authorizer"
  rest_api_id     = aws_api_gateway_rest_api.pod99_api.id
  authorizer_uri  = "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:000000000000:function:pod99-lambda-authorizer/invocations"
  identity_source = "method.request.header.Authorization"
}

# ==================================================================================
# MÉTODOS
# ==================================================================================

# POST /v1/contratos/{idContrato}/autorizacoes (com Lambda Authorizer)
resource "aws_api_gateway_method" "autorizar_transacao" {
  rest_api_id      = aws_api_gateway_rest_api.pod99_api.id
  resource_id      = aws_api_gateway_resource.autorizacoes.id
  http_method      = "POST"
  authorization    = "CUSTOM"
  authorizer_id    = aws_api_gateway_authorizer.lambda_authorizer.id
}

# POST /v1/contratos/authorize (Lambda Authorizer Handler, sem validação)
resource "aws_api_gateway_method" "lambda_authorizer_handler" {
  rest_api_id      = aws_api_gateway_rest_api.pod99_api.id
  resource_id      = aws_api_gateway_resource.authorize.id
  http_method      = "POST"
  authorization    = "NONE"
}

# ==================================================================================
# METHOD RESPONSES (obrigatório antes das integrações)
# ==================================================================================

resource "aws_api_gateway_method_response" "autorizar_transacao_200" {
  rest_api_id      = aws_api_gateway_rest_api.pod99_api.id
  resource_id      = aws_api_gateway_resource.autorizacoes.id
  http_method      = "POST"
  status_code      = "200"
}

resource "aws_api_gateway_method_response" "lambda_authorizer_handler_200" {
  rest_api_id      = aws_api_gateway_rest_api.pod99_api.id
  resource_id      = aws_api_gateway_resource.authorize.id
  http_method      = "POST"
  status_code      = "200"
}

# ==================================================================================
# INTEGRAÇÕES HTTP PROXY
# ==================================================================================

resource "aws_api_gateway_integration" "autorizar_transacao_integration" {
  rest_api_id      = aws_api_gateway_rest_api.pod99_api.id
  resource_id      = aws_api_gateway_resource.autorizacoes.id
  http_method      = "POST"
  type             = "HTTP_PROXY"
  uri              = "http://host.docker.internal:8080/v1/contratos/{idContrato}/autorizacoes"
  
  request_parameters = {
    "integration.request.path.idContrato" = "method.request.path.idContrato"
  }
}

resource "aws_api_gateway_integration" "lambda_authorizer_handler_integration" {
  rest_api_id      = aws_api_gateway_rest_api.pod99_api.id
  resource_id      = aws_api_gateway_resource.authorize.id
  http_method      = "POST"
  type             = "HTTP_PROXY"
  uri              = "http://host.docker.internal:8080/v1/contratos/authorize"
}

# ==================================================================================
# INTEGRATION RESPONSES
# ==================================================================================

resource "aws_api_gateway_integration_response" "autorizar_transacao_200" {
  rest_api_id      = aws_api_gateway_rest_api.pod99_api.id
  resource_id      = aws_api_gateway_resource.autorizacoes.id
  http_method      = "POST"
  status_code      = "200"
}

resource "aws_api_gateway_integration_response" "lambda_authorizer_handler_200" {
  rest_api_id      = aws_api_gateway_rest_api.pod99_api.id
  resource_id      = aws_api_gateway_resource.authorize.id
  http_method      = "POST"
  status_code      = "200"
}

# ==================================================================================
# DEPLOYMENT E STAGE
# ==================================================================================

resource "aws_api_gateway_deployment" "api_deployment" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id

  depends_on = [
    aws_api_gateway_method.autorizar_transacao,
    aws_api_gateway_method.lambda_authorizer_handler,
    aws_api_gateway_method_response.autorizar_transacao_200,
    aws_api_gateway_method_response.lambda_authorizer_handler_200,
    aws_api_gateway_integration.autorizar_transacao_integration,
    aws_api_gateway_integration.lambda_authorizer_handler_integration,
    aws_api_gateway_integration_response.autorizar_transacao_200,
    aws_api_gateway_integration_response.lambda_authorizer_handler_200
  ]
}

resource "aws_api_gateway_stage" "api_stage" {
  deployment_id = aws_api_gateway_deployment.api_deployment.id
  rest_api_id   = aws_api_gateway_rest_api.pod99_api.id
  stage_name    = "local"
}

# ==================================================================================
# OUTPUTS
# ==================================================================================

output "api_gateway_url" {
  description = "URL da API Gateway"
  value       = "${aws_api_gateway_stage.api_stage.invoke_url}/v1"
}

output "api_gateway_id" {
  description = "ID da API Gateway"
  value       = aws_api_gateway_rest_api.pod99_api.id
}

output "authorize_url" {
  description = "URL do Lambda Authorizer Handler"
  value       = "${aws_api_gateway_stage.api_stage.invoke_url}/v1/contratos/authorize"
}

output "autorizacoes_url" {
  description = "URL para autorizar transações"
  value       = "${aws_api_gateway_stage.api_stage.invoke_url}/v1/contratos/{idContrato}/autorizacoes"
}
