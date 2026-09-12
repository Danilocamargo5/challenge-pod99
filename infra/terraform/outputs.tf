# ==================================================================================
# Outputs - Infrastructure Summary
# ==================================================================================

output "environment" {
  description = "Ambiente (local/prod)"
  value       = var.environment
}

output "aws_region" {
  description = "Região AWS"
  value       = var.aws_region
}

output "project_name" {
  description = "Nome do projeto"
  value       = var.project
}

# ==================================================================================
# DynamoDB Tables
# ==================================================================================

output "dynamodb_limits_table" {
  description = "Nome da tabela de limites"
  value       = aws_dynamodb_table.limits.name
}

output "dynamodb_authorizations_table" {
  description = "Nome da tabela de autorizações"
  value       = aws_dynamodb_table.authorizations.name
}

output "dynamodb_accounting_table" {
  description = "Nome da tabela de contabilização"
  value       = aws_dynamodb_table.accounting.name
}

output "dynamodb_locks_table" {
  description = "Nome da tabela de locks"
  value       = aws_dynamodb_table.locks.name
}

output "dynamodb_rate_limit_table" {
  description = "Nome da tabela de rate limit"
  value       = aws_dynamodb_table.rate_limit.name
}

# ==================================================================================
# SQS Queues
# ==================================================================================

output "sqs_accounting_queue_name" {
  description = "Nome da fila de accounting"
  value       = aws_sqs_queue.accounting_queue.name
}

output "sqs_accounting_queue_url" {
  description = "URL da fila de accounting"
  value       = aws_sqs_queue.accounting_queue.url
}

output "sqs_accounting_queue_arn" {
  description = "ARN da fila de accounting"
  value       = aws_sqs_queue.accounting_queue.arn
}

output "sqs_accounting_dlq_name" {
  description = "Nome da Dead Letter Queue"
  value       = aws_sqs_queue.accounting_dlq.name
}

# ==================================================================================
# EventBridge
# ==================================================================================

output "eventbridge_rule_name" {
  description = "Nome da rule EventBridge"
  value       = aws_cloudwatch_event_rule.transacao_autorizada.name
}

# ==================================================================================
# API Gateway
# ==================================================================================

output "api_gateway_invoke_url" {
  description = "URL base da API Gateway"
  value       = aws_apigatewayv2_stage.api_stage.invoke_url
}

output "api_authorize_endpoint" {
  description = "Endpoint de autorização"
  value       = "${aws_apigatewayv2_stage.api_stage.invoke_url}/v1/contratos/{id_contrato}/autorizacoes"
}

# ==================================================================================
# Test Data
# ==================================================================================

output "test_data_info" {
  description = "Informações dos dados de teste"
  value       = {
    accounts  = "100 (ACC-001 até ACC-100)"
    contracts = "300 (CONTA-001 até CONTA-300)"
    ratio     = "3 contratos por conta"
    example   = "ACC-001 tem CONTA-001, CONTA-002, CONTA-003"
  }
}

output "curl_example_authorize" {
  description = "Exemplo de requisição de autorização"
  value       = "curl -X POST ${aws_apigatewayv2_stage.api_stage.invoke_url}/v1/contratos/CONTA-001/autorizacoes -H 'Content-Type: application/json' -H 'Authorization: Bearer jwt-ACC-001' -H 'Idempotency-Key: test-123' -d '{\"idConta\": \"ACC-001\", \"valor\": 100.00, \"moeda\": \"BRL\", \"tipoOperacao\": \"DEBITO\"}'"
}

# ==================================================================================
# Connection Status
# ==================================================================================

output "localstack_endpoint" {
  description = "LocalStack endpoint"
  value       = var.use_localstack ? "http://localhost:4566" : "AWS Real"
}

output "app_endpoint" {
  description = "App Spring Boot endpoint"
  value       = var.use_localstack ? "http://host.docker.internal:8080" : "http://localhost:8080"
}

output "setup_complete" {
  description = "Setup status"
  value       = "✅ Infraestrutura criada com sucesso!"
}
