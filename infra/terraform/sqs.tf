# ==================================================================================
# SQS Queues
# ==================================================================================

# Dead Letter Queue (DLQ) para accounting
resource "aws_sqs_queue" "accounting_dlq" {
  name                      = "pod99-accounting-dlq.fifo"
  fifo_queue                = true
  content_based_deduplication = true
  message_retention_seconds = 1209600

  tags = {
    Name        = "pod99-accounting-dlq"
    Description = "Dead Letter Queue para fila de contabilização"
  }
}

# Fila de Accounting (FIFO para garantir ordem)
resource "aws_sqs_queue" "accounting_queue" {
  name                      = "pod99-accounting-queue.fifo"
  fifo_queue                = true
  content_based_deduplication = true
  visibility_timeout_seconds = 300
  message_retention_seconds = 86400

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.accounting_dlq.arn
    maxReceiveCount     = 3
  })

  tags = {
    Name        = "pod99-accounting-queue"
    Description = "Fila FIFO para processamento assíncrono de contabilização"
  }
}

output "accounting_queue_arn" {
  description = "ARN da fila de accounting"
  value       = aws_sqs_queue.accounting_queue.arn
}

output "accounting_queue_url" {
  description = "URL da fila de accounting"
  value       = aws_sqs_queue.accounting_queue.url
}
