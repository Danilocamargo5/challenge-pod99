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
# LAMBDA AUTHORIZER
# ==================================================================================

resource "aws_api_gateway_authorizer" "lambda_authorizer" {
  name        = "pod99-lambda-authorizer"
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id

  authorizer_uri = "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:000000000000:function:pod99-lambda-authorizer/invocations"

  identity_source = "method.request.header.Authorization"
}

# ==================================================================================
# MÉTODO 1
# POST /v1/contratos/{idContrato}/autorizacoes
# Com Lambda Authorizer
# ==================================================================================

resource "aws_api_gateway_method" "autorizar_transacao" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.autorizacoes.id

  http_method   = "POST"
  authorization = "CUSTOM"
  authorizer_id = aws_api_gateway_authorizer.lambda_authorizer.id

  request_parameters = {
    "method.request.path.idContrato" = true
  }
}

# ==================================================================================
# METHOD RESPONSE - 200
# ==================================================================================

resource "aws_api_gateway_method_response" "autorizar_transacao_200" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.autorizacoes.id

  http_method = aws_api_gateway_method.autorizar_transacao.http_method
  status_code = "200"

  depends_on = [
    aws_api_gateway_method.autorizar_transacao
  ]
}

# ==================================================================================
# INTEGRAÇÃO HTTP PROXY
# ==================================================================================

resource "aws_api_gateway_integration" "autorizar_transacao_integration" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.autorizacoes.id

  http_method = aws_api_gateway_method.autorizar_transacao.http_method

  type                    = "HTTP_PROXY"
  integration_http_method = "POST"

  uri = "http://host.docker.internal:8080/v1/contratos/{idContrato}/autorizacoes"

  request_parameters = {
    "integration.request.path.idContrato" = "method.request.path.idContrato"
  }

  depends_on = [
    aws_api_gateway_method.autorizar_transacao
  ]
}

# ==================================================================================
# INTEGRAÇÃO RESPONSE - 200
# ==================================================================================

resource "aws_api_gateway_integration_response" "autorizar_transacao_integration_response" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.autorizacoes.id

  http_method = aws_api_gateway_method.autorizar_transacao.http_method
  status_code = aws_api_gateway_method_response.autorizar_transacao_200.status_code

  depends_on = [
    aws_api_gateway_method_response.autorizar_transacao_200,
    aws_api_gateway_integration.autorizar_transacao_integration
  ]
}

# ==================================================================================
# MÉTODO 2
# POST /v1/contratos/authorize
# Sem Authorizer
# ==================================================================================

resource "aws_api_gateway_method" "lambda_authorizer_handler" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.authorize.id

  http_method   = "POST"
  authorization = "NONE"
}

# ==================================================================================
# METHOD RESPONSE - 200
# ==================================================================================

resource "aws_api_gateway_method_response" "lambda_authorizer_handler_200" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.authorize.id

  http_method = aws_api_gateway_method.lambda_authorizer_handler.http_method
  status_code = "200"

  depends_on = [
    aws_api_gateway_method.lambda_authorizer_handler
  ]
}

# ==================================================================================
# INTEGRAÇÃO HTTP PROXY
# ==================================================================================

resource "aws_api_gateway_integration" "lambda_authorizer_handler_integration" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.authorize.id

  http_method = aws_api_gateway_method.lambda_authorizer_handler.http_method

  type                    = "HTTP_PROXY"
  integration_http_method = "POST"

  uri = "http://host.docker.internal:8080/v1/contratos/authorize"

  depends_on = [
    aws_api_gateway_method.lambda_authorizer_handler
  ]
}

# ==================================================================================
# INTEGRAÇÃO RESPONSE - 200
# ==================================================================================

resource "aws_api_gateway_integration_response" "lambda_authorizer_handler_integration_response" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.authorize.id

  http_method = aws_api_gateway_method.lambda_authorizer_handler.http_method
  status_code = aws_api_gateway_method_response.lambda_authorizer_handler_200.status_code

  depends_on = [
    aws_api_gateway_method_response.lambda_authorizer_handler_200,
    aws_api_gateway_integration.lambda_authorizer_handler_integration
  ]
}

# ==================================================================================
# DEPLOYMENT
# ==================================================================================

resource "aws_api_gateway_deployment" "api_deployment" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id

  depends_on = [
    aws_api_gateway_method.autorizar_transacao,
    aws_api_gateway_method_response.autorizar_transacao_200,
    aws_api_gateway_integration.autorizar_transacao_integration,
    aws_api_gateway_integration_response.autorizar_transacao_integration_response,

    aws_api_gateway_method.lambda_authorizer_handler,
    aws_api_gateway_method_response.lambda_authorizer_handler_200,
    aws_api_gateway_integration.lambda_authorizer_handler_integration,
    aws_api_gateway_integration_response.lambda_authorizer_handler_integration_response
  ]
}

# ==================================================================================
# STAGE
# ==================================================================================

resource "aws_api_gateway_stage" "api_stage" {
  deployment_id = aws_api_gateway_deployment.api_deployment.id
  rest_api_id   = aws_api_gateway_rest_api.pod99_api.id

  stage_name = "local"
}

# ==================================================================================
# OUTPUTS
# ==================================================================================

output "api_gateway_url" {
  description = "URL base da API Gateway"

  value = aws_api_gateway_stage.api_stage.invoke_url
}

output "api_gateway_id" {
  description = "ID da API Gateway"

  value = aws_api_gateway_rest_api.pod99_api.id
}

output "authorize_url" {
  description = "URL do endpoint authorize"

  value = "${aws_api_gateway_stage.api_stage.invoke_url}/v1/contratos/authorize"
}

output "autorizacoes_url" {
  description = "URL para autorizar transações"

  value = "${aws_api_gateway_stage.api_stage.invoke_url}/v1/contratos/{idContrato}/autorizacoes"
}
