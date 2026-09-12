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
# RECURSOS E MÉTODOS
# ==================================================================================

# /v1
resource "aws_api_gateway_resource" "v1" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  parent_id   = aws_api_gateway_rest_api.pod99_api.root_resource_id
  path_part   = "v1"
}

# /v1/contratos
resource "aws_api_gateway_resource" "contratos" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  parent_id   = aws_api_gateway_resource.v1.id
  path_part   = "contratos"
}

# /v1/contratos/{idContrato}
resource "aws_api_gateway_resource" "contrato_id" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  parent_id   = aws_api_gateway_resource.contratos.id
  path_part   = "{idContrato}"
}

# /v1/contratos/{idContrato}/autorizacoes
resource "aws_api_gateway_resource" "autorizacoes" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  parent_id   = aws_api_gateway_resource.contrato_id.id
  path_part   = "autorizacoes"
}

# /v1/contratos/authorize (Lambda Authorizer Handler)
resource "aws_api_gateway_resource" "authorize" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  parent_id   = aws_api_gateway_resource.contratos.id
  path_part   = "authorize"
}

# ==================================================================================
# MÉTODOS COM LAMBDA AUTHORIZER
# ==================================================================================

# POST /v1/contratos/{idContrato}/autorizacoes
resource "aws_api_gateway_method" "autorizar_transacao" {
  rest_api_id      = aws_api_gateway_rest_api.pod99_api.id
  resource_id      = aws_api_gateway_resource.autorizacoes.id
  http_method      = "POST"
  authorization    = "CUSTOM"
  authorizer_id    = aws_api_gateway_authorizer.lambda_authorizer.id
}

# Integração HTTP para POST /v1/contratos/{idContrato}/autorizacoes
resource "aws_api_gateway_integration" "autorizar_transacao_integration" {
  rest_api_id      = aws_api_gateway_rest_api.pod99_api.id
  resource_id      = aws_api_gateway_resource.autorizacoes.id
  http_method      = aws_api_gateway_method.autorizar_transacao.http_method
  type             = "HTTP_PROXY"
  uri              = "http://host.docker.internal:8080/v1/contratos/{idContrato}/autorizacoes"
  request_parameters = {
    "integration.request.path.idContrato" = "method.request.path.idContrato"
  }
}

# POST /v1/contratos/authorize (Lambda Authorizer Handler - SEM authorizer)
resource "aws_api_gateway_method" "lambda_authorizer_handler" {
  rest_api_id      = aws_api_gateway_rest_api.pod99_api.id
  resource_id      = aws_api_gateway_resource.authorize.id
  http_method      = "POST"
  authorization    = "NONE"
}

# Integração HTTP para POST /v1/contratos/authorize
resource "aws_api_gateway_integration" "lambda_authorizer_handler_integration" {
  rest_api_id      = aws_api_gateway_rest_api.pod99_api.id
  resource_id      = aws_api_gateway_resource.authorize.id
  http_method      = aws_api_gateway_method.lambda_authorizer_handler.http_method
  type             = "HTTP_PROXY"
  uri              = "http://host.docker.internal:8080/v1/contratos/authorize"
}

# ==================================================================================
# LAMBDA AUTHORIZER
# ==================================================================================

resource "aws_api_gateway_authorizer" "lambda_authorizer" {
  name            = "pod99-lambda-authorizer"
  rest_api_id     = aws_api_gateway_rest_api.pod99_api.id
  authorizer_uri  = "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:000000000000:function:pod99-lambda-authorizer/invocations"
  identity_source = "method.request.header.Authorization"
}

# ==================================================================================
# DEPLOYMENT E STAGE
# ==================================================================================

resource "aws_api_gateway_deployment" "api_deployment" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id

  depends_on = [
    aws_api_gateway_integration.autorizar_transacao_integration,
    aws_api_gateway_integration.lambda_authorizer_handler_integration
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
