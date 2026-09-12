# ==================================================================================
# Outputs - Infrastructure Summary
# ==================================================================================

output "environment_info" {
  description = "Informações do ambiente"
  value = {
    environment = var.environment
    aws_region  = var.aws_region
    project     = var.project
    localstack  = var.use_localstack
  }
}

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

output "sqs_queues" {
  description = "Filas SQS criadas"
  value = {
    accounting_queue = aws_sqs_queue.accounting_queue.url
    accounting_dlq   = aws_sqs_queue.accounting_dlq.url
  }
}

output "test_data" {
  description = "Dados de teste criados"
  value = {
    accounts  = "1 (ACC-001)"
    contracts = "3 (CONTA-001, CONTA-002, CONTA-003)"
  }
}

output "setup_status" {
  description = "Status do setup"
  value       = "✅ Infraestrutura criada com sucesso via Terraform!"
}
