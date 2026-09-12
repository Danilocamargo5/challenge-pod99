# ==================================================================================
# DynamoDB Tables
# ==================================================================================

# 1. Tabela de Limites (Contracts)
resource "aws_dynamodb_table" "limits" {
  name         = "pod99-limits"
  billing_mode = var.dynamodb_billing_mode
  hash_key     = "id_contrato"
  attribute {
    name = "id_contrato"
    type = "S"
  }

  tags = {
    Name        = "pod99-limits"
    Description = "Tabela de limites por contrato"
  }
}

# 2. Tabela de Autorizações
resource "aws_dynamodb_table" "authorizations" {
  name         = "pod99-authorizations"
  billing_mode = var.dynamodb_billing_mode
  hash_key     = "id_autorizacao"
  attribute {
    name = "id_autorizacao"
    type = "S"
  }

  tags = {
    Name        = "pod99-authorizations"
    Description = "Tabela de autorizações de transações"
  }
}

# 3. Tabela de Contabilização
resource "aws_dynamodb_table" "accounting" {
  name         = "pod99-accounting"
  billing_mode = var.dynamodb_billing_mode
  hash_key     = "event_id"
  attribute {
    name = "event_id"
    type = "S"
  }

  tags = {
    Name        = "pod99-accounting"
    Description = "Tabela de eventos contábeis"
  }
}

# 4. Tabela de Locks (Distributed Locking)
resource "aws_dynamodb_table" "locks" {
  name         = "pod99-locks"
  billing_mode = var.dynamodb_billing_mode
  hash_key     = "lock_key"
  attribute {
    name = "lock_key"
    type = "S"
  }

  tags = {
    Name        = "pod99-locks"
    Description = "Tabela de locks distribuídos (TTL: 30s)"
  }
}

# 5. Tabela de Rate Limit
resource "aws_dynamodb_table" "rate_limit" {
  name         = "pod99-rate-limit"
  billing_mode = var.dynamodb_billing_mode
  hash_key     = "account_id"
  attribute {
    name = "account_id"
    type = "S"
  }

  tags = {
    Name        = "pod99-rate-limit"
    Description = "Tabela de rate limiting por conta (TTL: 1s)"
  }
}
