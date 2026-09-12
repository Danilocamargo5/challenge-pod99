# ==================================================================================
# Seed Data - Populate DynamoDB with test data
# ==================================================================================

# Popula 100 contas × 3 contratos = 300 registros
resource "aws_dynamodb_table_item" "test_limits" {
  count          = 300
  table_name     = aws_dynamodb_table.limits.name
  hash_key       = "id_contrato"
  
  item = jsonencode({
    id_contrato = {
      S = format("CONTA-%03d", count.index + 1)
    }
    id_conta = {
      S = format("ACC-%03d", (count.index / 3) + 1)
    }
    limite = {
      N = tostring(51000 + ((count.index / 3 + 1) * 1000))
    }
    disponivel = {
      N = tostring(51000 + ((count.index / 3 + 1) * 1000))
    }
    reservado = {
      N = "0.00"
    }
    version = {
      N = "0"
    }
  })

  depends_on = [
    aws_dynamodb_table.limits
  ]
}

output "seed_data_status" {
  description = "Status da população de dados"
  value       = "✅ 300 registros de teste criados (100 contas × 3 contratos)"
  depends_on = [
    aws_dynamodb_table_item.test_limits
  ]
}
