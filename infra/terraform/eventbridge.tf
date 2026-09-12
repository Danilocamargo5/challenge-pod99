# ==================================================================================
# EventBridge Rules
# ==================================================================================

resource "aws_cloudwatch_event_rule" "transacao_autorizada" {
  name        = "pod99-transacao-autorizada-rule"
  description = "Regra para eventos TransacaoAutorizada"
  state       = "ENABLED"

  event_pattern = jsonencode({
    source      = ["pod99.authorization"]
    detail-type = ["TransacaoAutorizada"]
  })

  tags = {
    Name = "pod99-transacao-autorizada-rule"
  }
}

# Target: enviar para SQS de accounting
resource "aws_cloudwatch_event_target" "accounting_queue" {
  rule      = aws_cloudwatch_event_rule.transacao_autorizada.name
  target_id = "SendToAccountingQueue"
  arn       = aws_sqs_queue.accounting_queue.arn
  role_arn  = aws_iam_role.eventbridge_role.arn
  input_path = "$"
}

# ==================================================================================
# IAM Role para EventBridge
# ==================================================================================

resource "aws_iam_role" "eventbridge_role" {
  name = "pod99-eventbridge-role"

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
    Name = "pod99-eventbridge-role"
  }
}

# Policy para EventBridge enviar pra SQS
resource "aws_iam_role_policy" "eventbridge_sqs_policy" {
  name = "pod99-eventbridge-sqs-policy"
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

output "eventbridge_rule_name" {
  description = "Nome da rule EventBridge"
  value       = aws_cloudwatch_event_rule.transacao_autorizada.name
}

output "eventbridge_rule_arn" {
  description = "ARN da rule EventBridge"
  value       = aws_cloudwatch_event_rule.transacao_autorizada.arn
}
