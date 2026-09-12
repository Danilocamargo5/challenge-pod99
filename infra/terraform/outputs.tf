# ==================================================================================
# Outputs - Infrastructure Summary
# ==================================================================================

# Environment Info
output "environment_info" {
  description = "Informações do ambiente"
  value = {
    environment = var.environment
    aws_region  = var.aws_region
    project     = var.project
    localstack  = var.use_localstack
  }
}

# ==================================================================================
# DynamoDB Tables
# ==================================================================================

output "dynamodb_tables" {
  description = "Nomes das tabelas DynamoDB criadas"
  value = {
    limits         = aws_dynamodb_table.limits.name
    authorizations = aws_dynamodb_table.authorizations.name
    accounting     = aws_dynamodb_table.accounting.name
    locks          = aws_dynamodb_table.locks.name
    rate_limit     = aws_dynamodb_table.rate_limit.name
  }
}

# ==================================================================================
# SQS Queues
# ==================================================================================

output "sqs_queues" {
  description = "Filas SQS criadas"
  value = {
    accounting_queue = aws_sqs_queue.accounting_queue.url
    accounting_dlq   = aws_sqs_queue.accounting_dlq.url
  }
}

# ==================================================================================
# EventBridge
# ==================================================================================

output "eventbridge" {
  description = "EventBridge rule"
  value = {
    rule_name = aws_cloudwatch_event_rule.transacao_autorizada.name
    rule_arn  = aws_cloudwatch_event_rule.transacao_autorizada.arn
  }
}

# ==================================================================================
# Test Data
# ==================================================================================

output "test_data" {
  description = "Dados de teste criados"
  value = {
    accounts  = "1 (ACC-001)"
    contracts = "3 (CONTA-001, CONTA-002, CONTA-003)"
  }
}

# ==================================================================================
# Connection Info
# ==================================================================================

output "connection_info" {
  description = "Informações de conexão"
  value = {
    localstack_endpoint = var.use_localstack ? "http://localhost:4566" : "AWS Real"
    app_endpoint        = var.use_localstack ? "http://host.docker.internal:8080" : "http://localhost:8080"
    api_endpoint        = "${aws_apigatewayv2_stage.api_stage.invoke_url}/v1/contratos/{id_contrato}/autorizacoes"
  }
}

# ==================================================================================
# Example curl
# ==================================================================================

output "example_curl_authorize" {
  description = "Exemplo de requisição de autorização"
  value       = "curl -X POST ${aws_apigatewayv2_stage.api_stage.invoke_url}/v1/contratos/CONTA-001/autorizacoes -H 'Content-Type: application/json' -H 'Authorization: Bearer jwt-ACC-001' -H 'Idempotency-Key: test-123' -d '{\"idConta\": \"ACC-001\", \"valor\": 100.00, \"moeda\": \"BRL\", \"tipoOperacao\": \"DEBITO\"}'"
}

# ==================================================================================
# Setup Status
# ==================================================================================

output "setup_status" {
  description = "Status do setup"
  value       = "✅ Infraestrutura criada com sucesso via Terraform!"
}
