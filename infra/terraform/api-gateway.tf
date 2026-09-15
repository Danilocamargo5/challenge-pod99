# ==================================================================================
# API Gateway - REST API
# Integração HTTP + Lambda Authorizer
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

# Endpoint usado pelo fluxo de autorização
resource "aws_api_gateway_resource" "authorize" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  parent_id   = aws_api_gateway_resource.contratos.id
  path_part   = "authorize"
}

# ==================================================================================
# LAMBDA AUTHORIZER
# ==================================================================================
#
# API Gateway recebe:
#
# Authorization: Bearer jwt-ACC-001
#
# e envia para a Lambda:
#
# {
#   "type": "TOKEN",
#   "authorizationToken": "Bearer jwt-ACC-001",
#   "methodArn": "arn:aws:execute-api:..."
# }
#
# ==================================================================================

resource "aws_api_gateway_authorizer" "lambda_authorizer" {
  name        = "pod99-lambda-authorizer"
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id

  type = "TOKEN"

  authorizer_uri = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${aws_lambda_function.lambda_authorizer.arn}/invocations"

  identity_source = "method.request.header.Authorization"

  # Para desenvolvimento local queremos executar o Authorizer
  # em todas as requisições.
  authorizer_result_ttl_in_seconds = 0

  depends_on = [
    aws_lambda_function.lambda_authorizer,
    aws_lambda_permission.api_gateway_authorizer
  ]
}

# ==================================================================================
# MÉTODO 1
# POST /v1/contratos/{idContrato}/autorizacoes
#
# Protegido pelo Lambda Authorizer
# ==================================================================================

resource "aws_api_gateway_method" "autorizar_transacao" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.autorizacoes.id

  http_method   = "POST"
  authorization = "CUSTOM"
  authorizer_id = aws_api_gateway_authorizer.lambda_authorizer.id

  # Path parameter e headers obrigatórios
  request_parameters = {
    "method.request.path.idContrato"      = true
    "method.request.header.Authorization" = true
  }
}

# ==================================================================================
# METHOD RESPONSE - 200
# ==================================================================================

resource "aws_api_gateway_method_response" "autorizar_transacao_200" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.autorizacoes.id

  http_method = "POST"
  status_code = "200"

  depends_on = [
    aws_api_gateway_method.autorizar_transacao
  ]
}

# ==================================================================================
# METHOD RESPONSE - 201
# ==================================================================================

resource "aws_api_gateway_method_response" "autorizar_transacao_201" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.autorizacoes.id

  http_method = "POST"
  status_code = "201"

  depends_on = [
    aws_api_gateway_method.autorizar_transacao
  ]
}

# ==================================================================================
# METHOD RESPONSE - 402
# ==================================================================================

resource "aws_api_gateway_method_response" "autorizar_transacao_402" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.autorizacoes.id

  http_method = "POST"
  status_code = "402"

  depends_on = [
    aws_api_gateway_method.autorizar_transacao
  ]
}

# ==================================================================================
# METHOD RESPONSE - 409
# ==================================================================================

resource "aws_api_gateway_method_response" "autorizar_transacao_409" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.autorizacoes.id

  http_method = "POST"
  status_code = "409"

  depends_on = [
    aws_api_gateway_method.autorizar_transacao
  ]
}

# ==================================================================================
# METHOD RESPONSE - 422
# ==================================================================================

resource "aws_api_gateway_method_response" "autorizar_transacao_422" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.autorizacoes.id

  http_method = "POST"
  status_code = "422"

  depends_on = [
    aws_api_gateway_method.autorizar_transacao
  ]
}

# ==================================================================================
# METHOD RESPONSE - 429
# ==================================================================================

resource "aws_api_gateway_method_response" "autorizar_transacao_429" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.autorizacoes.id

  http_method = "POST"
  status_code = "429"

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

  http_method             = "POST"
  type                    = "HTTP_PROXY"
  integration_http_method = "POST"

  uri = "http://host.docker.internal:8080/v1/contratos/{idContrato}/autorizacoes"

  request_parameters = {
    "integration.request.path.idContrato"      = "method.request.path.idContrato"
    "integration.request.header.Authorization" = "method.request.header.Authorization"
  }

  request_templates = {
    "application/json" = "$input.body"
  }

  passthrough_behavior = "WHEN_NO_TEMPLATES"

  depends_on = [
    aws_api_gateway_method.autorizar_transacao
  ]
}

# ==================================================================================
# INTEGRAÇÃO RESPONSE - 200
# ==================================================================================

resource "aws_api_gateway_integration_response" "autorizar_transacao_integration_response_200" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.autorizacoes.id

  http_method = "POST"
  status_code = "200"


  response_templates = {
    "application/json" = "$input.body"
  }

  depends_on = [
    aws_api_gateway_method_response.autorizar_transacao_200,
    aws_api_gateway_integration.autorizar_transacao_integration
  ]
}

# ==================================================================================
# INTEGRAÇÃO RESPONSE - 201
# ==================================================================================

resource "aws_api_gateway_integration_response" "autorizar_transacao_integration_response_201" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.autorizacoes.id

  http_method = "POST"
  status_code = "201"

  # Mapear HTTP 201 do backend para 201

  response_templates = {
    "application/json" = "$input.body"
  }

  depends_on = [
    aws_api_gateway_method_response.autorizar_transacao_201,
    aws_api_gateway_integration.autorizar_transacao_integration
  ]
}

# ==================================================================================
# INTEGRAÇÃO RESPONSE - 402
# ==================================================================================

resource "aws_api_gateway_integration_response" "autorizar_transacao_integration_response_402" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.autorizacoes.id

  http_method = "POST"
  status_code = "402"


  response_templates = {
    "application/json" = "$input.body"
  }

  depends_on = [
    aws_api_gateway_method_response.autorizar_transacao_402,
    aws_api_gateway_integration.autorizar_transacao_integration
  ]
}

# ==================================================================================
# INTEGRAÇÃO RESPONSE - 409
# ==================================================================================

resource "aws_api_gateway_integration_response" "autorizar_transacao_integration_response_409" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.autorizacoes.id

  http_method = "POST"
  status_code = "409"


  response_templates = {
    "application/json" = "$input.body"
  }

  depends_on = [
    aws_api_gateway_method_response.autorizar_transacao_409,
    aws_api_gateway_integration.autorizar_transacao_integration
  ]
}

# ==================================================================================
# INTEGRAÇÃO RESPONSE - 422
# ==================================================================================

resource "aws_api_gateway_integration_response" "autorizar_transacao_integration_response_422" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.autorizacoes.id

  http_method = "POST"
  status_code = "422"


  response_templates = {
    "application/json" = "$input.body"
  }

  depends_on = [
    aws_api_gateway_method_response.autorizar_transacao_422,
    aws_api_gateway_integration.autorizar_transacao_integration
  ]
}

# ==================================================================================
# INTEGRAÇÃO RESPONSE - 429
# ==================================================================================

resource "aws_api_gateway_integration_response" "autorizar_transacao_integration_response_429" {
  rest_api_id = aws_api_gateway_rest_api.pod99_api.id
  resource_id = aws_api_gateway_resource.autorizacoes.id

  http_method = "POST"
  status_code = "429"


  response_templates = {
    "application/json" = "$input.body"
  }

  depends_on = [
    aws_api_gateway_method_response.autorizar_transacao_429,
    aws_api_gateway_integration.autorizar_transacao_integration
  ]
}

# ==================================================================================
# MÉTODO 2
# POST /v1/contratos/authorize
#
# Endpoint chamado pela Lambda Authorizer
# NÃO é protegido pelo Authorizer, para evitar loop.
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

  http_method = "POST"
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

  http_method             = "POST"
  type                    = "HTTP_PROXY"
  integration_http_method = "POST"

  uri = "http://host.docker.internal:8080/v1/contratos/authorize"

  request_parameters = {
    "integration.request.header.Authorization" = "method.request.header.Authorization"
  }

  request_templates = {
    "application/json" = "$input.body"
  }

  passthrough_behavior = "WHEN_NO_TEMPLATES"

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

  http_method = "POST"
  status_code = "200"


  response_templates = {
    "application/json" = "$input.body"
  }

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
    # Lambda Authorizer
    aws_api_gateway_authorizer.lambda_authorizer,

    # Endpoint protegido
    aws_api_gateway_method.autorizar_transacao,
    aws_api_gateway_method_response.autorizar_transacao_200,
    aws_api_gateway_method_response.autorizar_transacao_201,
    aws_api_gateway_method_response.autorizar_transacao_402,
    aws_api_gateway_method_response.autorizar_transacao_409,
    aws_api_gateway_method_response.autorizar_transacao_422,
    aws_api_gateway_method_response.autorizar_transacao_429,
    aws_api_gateway_integration.autorizar_transacao_integration,
    aws_api_gateway_integration_response.autorizar_transacao_integration_response_200,
    aws_api_gateway_integration_response.autorizar_transacao_integration_response_201,
    aws_api_gateway_integration_response.autorizar_transacao_integration_response_402,
    aws_api_gateway_integration_response.autorizar_transacao_integration_response_409,
    aws_api_gateway_integration_response.autorizar_transacao_integration_response_422,
    aws_api_gateway_integration_response.autorizar_transacao_integration_response_429,

    # Endpoint do Authorizer
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
  description = "Endpoint usado pelo Lambda Authorizer"

  value = "${aws_api_gateway_stage.api_stage.invoke_url}/v1/contratos/authorize"
}

output "autorizacoes_url" {
  description = "Endpoint protegido para autorizar transações"

  value = "${aws_api_gateway_stage.api_stage.invoke_url}/v1/contratos/{idContrato}/autorizacoes"
}
