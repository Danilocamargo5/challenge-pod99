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

# ==================================================================================
# EventBridge Target - Criado via awslocal (LocalStack CLI)
# ==================================================================================

resource "null_resource" "eventbridge_target" {
  provisioner "local-exec" {
    command = <<-EOT
      awslocal events put-targets \
        --rule ${aws_cloudwatch_event_rule.transacao_autorizada.name} \
        --targets "Id=1,Arn=${aws_sqs_queue.accounting_queue.arn},RoleArn=${aws_iam_role.eventbridge_role.arn},SqsParameters={MessageGroupIdPath=$.id}" \
        --region us-east-1
    EOT
  }

  depends_on = [
    aws_cloudwatch_event_rule.transacao_autorizada,
    aws_sqs_queue.accounting_queue,
    aws_iam_role.eventbridge_role
  ]
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

output "eventbridge_target_note" {
  description = "Target criado via AWS CLI"
  value       = "✅ EventBridge Target criado com SqsParameters via AWS CLI"
}
