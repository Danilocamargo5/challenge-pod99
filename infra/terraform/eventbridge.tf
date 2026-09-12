# ==================================================================================
# EventBridge Rules
# ==================================================================================

# Rule para eventos de transação autorizada
resource "aws_cloudwatch_event_rule" "transacao_autorizada" {
  name        = local.rule_transacao_autorizada
  description = "Regra para eventos TransacaoAutorizada"
  state       = "ENABLED"

  event_pattern = jsonencode({
    source      = ["pod99.authorization"]
    detail-type = ["TransacaoAutorizada"]
  })

  tags = {
    Name = local.rule_transacao_autorizada
  }
}

# Target: enviar para SQS de accounting
resource "aws_cloudwatch_event_target" "accounting_queue" {
  rule      = aws_cloudwatch_event_rule.transacao_autorizada.name
  target_id = "SendToAccountingQueue"
  arn       = aws_sqs_queue.accounting_queue.arn

  role_arn = aws_iam_role.eventbridge_role.arn

  # Usar message group ID do evento para FIFO
  sqs_target {
    message_group_id_path = "$.event_id"
  }
}

# ==================================================================================
# IAM Role para EventBridge
# ==================================================================================

resource "aws_iam_role" "eventbridge_role" {
  name = "${var.project}-eventbridge-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "events.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name = "${var.project}-eventbridge-role"
  }
}

# Policy para EventBridge enviar pra SQS
resource "aws_iam_role_policy" "eventbridge_sqs_policy" {
  name = "${var.project}-eventbridge-sqs-policy"
  role = aws_iam_role.eventbridge_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "sqs:SendMessage"
        ]
        Resource = [
          aws_sqs_queue.accounting_queue.arn
        ]
      }
    ]
  })
}

# ==================================================================================
# Outputs
# ==================================================================================

output "eventbridge_rule_name" {
  description = "Nome da rule EventBridge"
  value       = aws_cloudwatch_event_rule.transacao_autorizada.name
}

output "eventbridge_rule_arn" {
  description = "ARN da rule EventBridge"
  value       = aws_cloudwatch_event_rule.transacao_autorizada.arn
}
