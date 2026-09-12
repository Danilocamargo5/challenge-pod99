# ==================================================================================
# Lambda Authorizer - POD99
# ==================================================================================
#
# Fluxo:
#
# Cliente
#   |
#   | Authorization: Bearer jwt-ACC-001
#   v
# API Gateway
#   |
#   v
# Lambda Authorizer
#   |
#   | POST /v1/contratos/authorize
#   v
# Spring Boot
#   |
#   v
# JwtValidator
#   |
#   +---- Allow ----> API Gateway ---> Backend
#   |
#   +---- Deny -----> API Gateway ---> 403
#
# ==================================================================================

# ==================================================================================
# ZIP DA LAMBDA
# ==================================================================================

data "archive_file" "lambda_authorizer" {
  type        = "zip"
  output_path = "${path.module}/lambda-authorizer.zip"

  source {
    filename = "index.py"

    content = <<-PYTHON
      import json
      import os
      import urllib.request
      import urllib.error


      BACKEND_URL = os.environ.get(
          "BACKEND_URL",
          "http://host.docker.internal:8080/v1/contratos/authorize"
      )


      def lambda_handler(event, context):
          """
          Lambda Authorizer do tipo TOKEN.

          O API Gateway envia:

          {
              "type": "TOKEN",
              "authorizationToken": "Bearer jwt-ACC-001",
              "methodArn": "arn:aws:execute-api:..."
          }

          A Lambda encaminha o evento para o endpoint /authorize
          do Spring Boot.

          O Spring valida o token e devolve a IAM Policy.

          A Lambda devolve a mesma policy ao API Gateway.
          """

          print("========== POD99 Lambda Authorizer ==========")

          # --------------------------------------------------------------------------
          # Extrair dados do evento
          # --------------------------------------------------------------------------

          auth_token = event.get("authorizationToken")
          method_arn = event.get("methodArn")

          if not auth_token:
              print("Authorization token ausente")

              return generate_policy(
                  principal_id="user-unauthorized",
                  effect="Deny",
                  resource=method_arn or "*"
              )

          if not method_arn:
              print("methodArn ausente")

              return generate_policy(
                  principal_id="user-unauthorized",
                  effect="Deny",
                  resource="*"
              )

          print("Authorization token recebido")
          print("Method ARN:", method_arn)

          # --------------------------------------------------------------------------
          # Montar request para o Spring
          # --------------------------------------------------------------------------

          payload = {
              "authorizationToken": auth_token,
              "methodArn": method_arn
          }

          request_body = json.dumps(payload).encode("utf-8")

          request = urllib.request.Request(
              BACKEND_URL,
              data=request_body,
              headers={
                  "Content-Type": "application/json"
              },
              method="POST"
          )

          # --------------------------------------------------------------------------
          # Chamar Spring Boot
          # --------------------------------------------------------------------------

          try:

              with urllib.request.urlopen(request, timeout=5) as response:

                  response_body = response.read().decode("utf-8")

                  print("Spring HTTP status:", response.status)

                  if response.status != 200:
                      print("Spring retornou status inesperado")

                      return generate_policy(
                          principal_id="user-error",
                          effect="Deny",
                          resource=method_arn
                      )

                  # --------------------------------------------------------------------
                  # O Spring já devolve a IAM Policy.
                  # --------------------------------------------------------------------

                  result = json.loads(response_body)

                  print(
                      "Policy recebida do Spring:",
                      json.dumps(result)
                  )

                  return result

          # --------------------------------------------------------------------------
          # Erros HTTP
          # --------------------------------------------------------------------------

          except urllib.error.HTTPError as error:

              print(
                  "Erro HTTP chamando Spring:",
                  error.code
              )

              return generate_policy(
                  principal_id="user-error",
                  effect="Deny",
                  resource=method_arn
              )

          # --------------------------------------------------------------------------
          # Erro de conexão
          # --------------------------------------------------------------------------

          except urllib.error.URLError as error:

              print(
                  "Erro de conexão com Spring:",
                  str(error)
              )

              return generate_policy(
                  principal_id="user-error",
                  effect="Deny",
                  resource=method_arn
              )

          # --------------------------------------------------------------------------
          # Outros erros
          # --------------------------------------------------------------------------

          except Exception as error:

              print(
                  "Erro inesperado no Lambda Authorizer:",
                  str(error)
              )

              return generate_policy(
                  principal_id="user-error",
                  effect="Deny",
                  resource=method_arn
              )


      def generate_policy(principal_id, effect, resource):
          """
          Fallback para erros da Lambda.

          A policy correta para API Gateway REST usa:
          execute-api:Invoke
          """

          return {
              "principalId": principal_id,

              "policyDocument": {
                  "Version": "2012-10-17",

                  "Statement": [
                      {
                          "Action": "execute-api:Invoke",
                          "Effect": effect,
                          "Resource": resource
                      }
                  ]
              },

              "context": {}
          }
    PYTHON
  }
}

# ==================================================================================
# IAM ROLE DA LAMBDA
# ==================================================================================

resource "aws_iam_role" "lambda_authorizer_role" {
  name = "pod99-lambda-authorizer-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"

    Statement = [
      {
        Effect = "Allow"

        Action = "sts:AssumeRole"

        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name = "pod99-lambda-authorizer-role"
  }
}

# ==================================================================================
# IAM POLICY PARA CLOUDWATCH LOGS
# ==================================================================================

resource "aws_iam_role_policy" "lambda_authorizer_logs" {
  name = "pod99-lambda-authorizer-logs"

  role = aws_iam_role.lambda_authorizer_role.id

  policy = jsonencode({
    Version = "2012-10-17"

    Statement = [
      {
        Effect = "Allow"

        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]

        Resource = "*"
      }
    ]
  })
}

# ==================================================================================
# LAMBDA FUNCTION
# ==================================================================================

resource "aws_lambda_function" "lambda_authorizer" {
  function_name = "pod99-lambda-authorizer"

  filename = data.archive_file.lambda_authorizer.output_path

  source_code_hash = data.archive_file.lambda_authorizer.output_base64sha256

  role = aws_iam_role.lambda_authorizer_role.arn

  runtime = "python3.11"
  handler = "index.lambda_handler"

  timeout     = 10
  memory_size = 128

  environment {
    variables = {
      BACKEND_URL = "http://host.docker.internal:8080/v1/contratos/authorize"
    }
  }

  tags = {
    Name = "pod99-lambda-authorizer"
  }

  depends_on = [
    aws_iam_role_policy.lambda_authorizer_logs
  ]
}

# ==================================================================================
# PERMISSÃO PARA API GATEWAY INVOCAR A LAMBDA
# ==================================================================================

resource "aws_lambda_permission" "api_gateway_authorizer" {
  statement_id = "AllowApiGatewayInvokeAuthorizer"

  action = "lambda:InvokeFunction"

  function_name = aws_lambda_function.lambda_authorizer.function_name

  principal = "apigateway.amazonaws.com"

  source_arn = "${aws_api_gateway_rest_api.pod99_api.execution_arn}/*"
}

# ==================================================================================
# OUTPUTS
# ==================================================================================

output "lambda_authorizer_name" {
  description = "Nome da Lambda Authorizer"

  value = aws_lambda_function.lambda_authorizer.function_name
}

output "lambda_authorizer_arn" {
  description = "ARN da Lambda Authorizer"

  value = aws_lambda_function.lambda_authorizer.arn
}

