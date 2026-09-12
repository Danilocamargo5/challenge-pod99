# ==================================================================================
# SQS Queues
# ==================================================================================

# Dead Letter Queue (DLQ) para accounting
resource "aws_sqs_queue" "accounting_dlq" {
  name                      = "${local.queue_accounting_dlq}.fifo"
  fifo_queue                = true
  content_based_deduplication = true
  message_retention_seconds = 1209600  # 14 dias

  tags = {
    Name        = local.queue_accounting_dlq
    Description = "Dead Letter Queue para fila de contabilização"
  }
}

# Fila de Accounting (FIFO para garantir ordem)
resource "aws_sqs_queue" "accounting_queue" {
  name                      = "${local.queue_accounting}.fifo"
  fifo_queue                = true
  content_based_deduplication = true
  visibility_timeout_seconds = 300
  message_retention_seconds = 86400    # 1 dia

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.accounting_dlq.arn
    maxReceiveCount     = 3
  })

  tags = {
    Name        = local.queue_accounting
    Description = "Fila FIFO para processamento assíncrono de contabilização"
  }
}

# Output do ARN da fila
output "accounting_queue_arn" {
  description = "ARN da fila de accounting"
  value       = aws_sqs_queue.accounting_queue.arn
}

output "accounting_queue_url" {
  description = "URL da fila de accounting"
  value       = aws_sqs_queue.accounting_queue.url
}
