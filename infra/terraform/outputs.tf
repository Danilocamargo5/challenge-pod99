output "api_endpoint" {
  description = "URL do API Gateway para chamar a API"
  value       = try(aws_api_gateway_stage.prod.invoke_url, "http://localhost:8080")
}

output "api_key" {
  description = "API Key para autenticar chamadas (usar no header x-api-key)"
  value       = try(aws_api_gateway_api_key.pod99_client.value, "USE_HEADER_AUTHORIZATION")
  sensitive   = true
}

output "dynamodb_tables" {
  description = "Nomes das tabelas DynamoDB criadas"
  value = {
    limits           = aws_dynamodb_table.limits.name
    authorizations   = aws_dynamodb_table.authorizations.name
    accounting       = aws_dynamodb_table.accounting.name
    locks            = aws_dynamodb_table.locks.name
  }
}

output "sqs_queue_urls" {
  description = "URLs das filas SQS"
  value = {
    accounting      = aws_sqs_queue.accounting.url
    accounting_dlq  = aws_sqs_queue.accounting_dlq.url
    fraud           = aws_sqs_queue.fraud.url
    notifications   = aws_sqs_queue.notifications.url
  }
}

output "sqs_queue_arns" {
  description = "ARNs das filas SQS"
  value = {
    accounting      = aws_sqs_queue.accounting.arn
    accounting_dlq  = aws_sqs_queue.accounting_dlq.arn
    fraud           = aws_sqs_queue.fraud.arn
    notifications   = aws_sqs_queue.notifications.arn
  }
}

output "eventbridge_event_bus" {
  description = "Nome do EventBridge Event Bus"
  value       = aws_cloudwatch_event_bus.pod99.name
}

output "eventbridge_rule_arn" {
  description = "ARN da regra EventBridge"
  value       = aws_cloudwatch_event_rule.transacao_autorizada.arn
}

output "lambda_authorizer_arn" {
  description = "ARN da Lambda Authorizer"
  value       = try(aws_lambda_function.authorizer.arn, "")
}

output "cloudwatch_log_group" {
  description = "CloudWatch Log Group para logs de API Gateway"
  value       = aws_cloudwatch_log_group.api_gateway.name
}

output "environment_summary" {
  description = "Resumo do ambiente deployment"
  value = {
    region      = var.aws_region
    environment = var.environment
    app_name    = var.app_name
  }
}

output "deployment_instructions" {
  description = "Instruções de uso"
  value = <<-EOT
    
    Ambiente POD99 deployment concluído!
    
    1. API Endpoint:
       ${try(aws_api_gateway_stage.prod.invoke_url, "http://localhost:8080")}
    
    2. Autorizar requisições:
       - Header: Authorization: Bearer <token>
       - Header: x-api-key: ${try(aws_api_gateway_api_key.pod99_client.value, "YOUR_API_KEY")}
    
    3. Exemplo de chamada:
       curl -X POST ${try(aws_api_gateway_stage.prod.invoke_url, "http://localhost:8080")}/v1/contratos/CONTA-001/autorizacoes \
         -H "Authorization: Bearer <token>" \
         -H "x-api-key: ${try(aws_api_gateway_api_key.pod99_client.value, "YOUR_API_KEY")}" \
         -H "Idempotency-Key: $(uuidgen)" \
         -H "Content-Type: application/json" \
         -d '{
           "id_conta": "ACC-001",
           "valor": 100.00,
           "moeda": "BRL",
           "tipo_operacao": "DEBITO"
         }'
    
    4. Monitorar eventos:
       - AWS Console → EventBridge → pod99-event-bus
       - Verificar mensagens em SQS queues
    
    5. Logs:
       - CloudWatch → Log Groups → /aws/apigateway/pod99
       - X-Ray → Traces → pod99-api
  EOT
}
