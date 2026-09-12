# ==================================================================================
# Seed Data - Populate DynamoDB with test data
# ==================================================================================

# Apenas 1 conta de teste com 3 contratos para testes rápidos
resource "aws_dynamodb_table_item" "test_limits_1" {
  table_name = aws_dynamodb_table.limits.name
  hash_key   = "id_contrato"
  
  item = jsonencode({
    id_contrato = { S = "CONTA-001" }
    id_conta    = { S = "ACC-001" }
    limite      = { N = "51000.00" }
    disponivel  = { N = "51000.00" }
    reservado   = { N = "0.00" }
    version     = { N = "0" }
  })
  depends_on = [aws_dynamodb_table.limits]
}

resource "aws_dynamodb_table_item" "test_limits_2" {
  table_name = aws_dynamodb_table.limits.name
  hash_key   = "id_contrato"
  
  item = jsonencode({
    id_contrato = { S = "CONTA-002" }
    id_conta    = { S = "ACC-001" }
    limite      = { N = "51000.00" }
    disponivel  = { N = "51000.00" }
    reservado   = { N = "0.00" }
    version     = { N = "0" }
  })
  depends_on = [aws_dynamodb_table.limits]
}

resource "aws_dynamodb_table_item" "test_limits_3" {
  table_name = aws_dynamodb_table.limits.name
  hash_key   = "id_contrato"
  
  item = jsonencode({
    id_contrato = { S = "CONTA-003" }
    id_conta    = { S = "ACC-001" }
    limite      = { N = "51000.00" }
    disponivel  = { N = "51000.00" }
    reservado   = { N = "0.00" }
    version     = { N = "0" }
  })
  depends_on = [aws_dynamodb_table.limits]
}

output "seed_data_status" {
  description = "Status da população de dados"
  value       = "✅ 3 registros de teste criados (1 conta × 3 contratos)"
  depends_on = [
    aws_dynamodb_table_item.test_limits_1,
    aws_dynamodb_table_item.test_limits_2,
    aws_dynamodb_table_item.test_limits_3
  ]
}
