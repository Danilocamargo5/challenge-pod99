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
# EventBridge Target
# Criado via curl no LocalStack por Terraform local-exec.
#
# Motivo:
# O LocalStack possui limitações na configuração do target FIFO do EventBridge
# através do provider Terraform. O target é criado diretamente pela API compatível
# do EventBridge, incluindo SqsParameters.MessageGroupId.
# ==================================================================================

resource "null_resource" "eventbridge_target" {

  provisioner "local-exec" {
    command = <<-EOT
      echo "Configurando EventBridge Target..."

      RESPONSE=$(curl -s -X POST http://localhost:4566/ \
        -H "Content-Type: application/x-amz-json-1.1" \
        -H "X-Amz-Target: AWSEvents.PutTargets" \
        -d '{
          "Rule": "pod99-transacao-autorizada-rule",
          "Targets": [{
            "Id": "1",
            "Arn": "${aws_sqs_queue.accounting_queue.arn}",
            "RoleArn": "${aws_iam_role.eventbridge_role.arn}",
            "SqsParameters": {
              "MessageGroupId": "pod99"
            }
          }]
        }')

      echo "Resposta do LocalStack:"
      echo "$RESPONSE"

      if ! echo "$RESPONSE" | grep -Eq '"FailedEntryCount"[[:space:]]*:[[:space:]]*0'; then
        echo "ERRO: EventBridge Target não foi criado."
        echo "Resposta recebida: $RESPONSE"
        exit 1
      fi

      echo "EventBridge Target criado com sucesso!"
    EOT
  }

  provisioner "local-exec" {
    when = destroy

    command = <<-EOT
      echo "Removendo EventBridge Target..."

      RESPONSE=$(curl -s -X POST http://localhost:4566/ \
        -H "Content-Type: application/x-amz-json-1.1" \
        -H "X-Amz-Target: AWSEvents.RemoveTargets" \
        -d '{
          "Rule": "pod99-transacao-autorizada-rule",
          "Ids": ["1"]
        }')

      echo "Resposta do LocalStack:"
      echo "$RESPONSE"

      if ! echo "$RESPONSE" | grep -Eq '"FailedEntryCount"[[:space:]]*:[[:space:]]*0'; then
        echo "ERRO: EventBridge Target não foi removido."
        echo "Resposta recebida: $RESPONSE"
        exit 1
      fi

      echo "EventBridge Target removido com sucesso!"
    EOT
  }

  depends_on = [
    aws_cloudwatch_event_rule.transacao_autorizada,
    aws_sqs_queue.accounting_queue,
    aws_iam_role.eventbridge_role,
    aws_iam_role_policy.eventbridge_sqs_policy
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

# ==================================================================================
# Policy para EventBridge enviar mensagens para SQS
# ==================================================================================

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

# ==================================================================================
# Outputs
# ==================================================================================

output "eventbridge_rule_name" {
  description = "Nome da rule EventBridge"

  value = aws_cloudwatch_event_rule.transacao_autorizada.name
}

output "eventbridge_rule_arn" {
  description = "ARN da rule EventBridge"

  value = aws_cloudwatch_event_rule.transacao_autorizada.arn
}

output "eventbridge_target_note" {
  description = "Target EventBridge configurado no LocalStack"

  value = "EventBridge Target criado via Terraform local-exec com SqsParameters.MessageGroupId"
}