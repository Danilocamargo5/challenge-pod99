# ==================================================================================
# Seed Data - Populate DynamoDB with test data
# ==================================================================================

resource "null_resource" "populate_test_data" {
  provisioner "local-exec" {
    command = "bash ${path.module}/../scripts/populate-data.sh"
    
    environment = {
      AWS_ACCESS_KEY_ID     = var.aws_access_key_id
      AWS_SECRET_ACCESS_KEY = var.aws_secret_access_key
      AWS_REGION            = var.aws_region
      AWS_ENDPOINT_URL      = var.use_localstack ? "http://localhost:4566" : ""
    }
  }

  depends_on = [
    aws_dynamodb_table.limits,
    aws_dynamodb_table.authorizations,
    aws_dynamodb_table.accounting,
    aws_dynamodb_table.locks,
    aws_dynamodb_table.rate_limit
  ]

  triggers = {
    script_hash = filemd5("${path.module}/../scripts/populate-data.sh")
  }
}

output "seed_data_status" {
  description = "Status da população de dados"
  value       = "✅ Dados de teste populados (100 contas × 3 contratos = 300 registros)"
  depends_on = [
    null_resource.populate_test_data
  ]
}
