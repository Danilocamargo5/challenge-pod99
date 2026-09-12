# ==================================================================================
# EventBridge - REMOVIDO (SqsParameters não funciona no LocalStack 4.14.0)
# ==================================================================================

# LocalStack não consegue validar SqsParameters corretamente
# Contabilização será feita direto no app por enquanto

output "eventbridge_note" {
  value = "EventBridge removido - contabilização assíncrona será implementada depois"
}
