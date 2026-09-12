# ==================================================================================
# DynamoDB Tables
# ==================================================================================

# 1. Tabela de Limites (Contracts)
resource "aws_dynamodb_table" "limits" {
  name           = local.table_limits
  billing_mode   = var.dynamodb_billing_mode
  hash_key       = "id_contrato"
  attribute {
    name = "id_contrato"
    type = "S"
  }

  tags = {
    Name        = local.table_limits
    Description = "Tabela de limites por contrato"
  }
}

# 2. Tabela de Autorizações
resource "aws_dynamodb_table" "authorizations" {
  name           = local.table_authorizations
  billing_mode   = var.dynamodb_billing_mode
  hash_key       = "id_autorizacao"
  attribute {
    name = "id_autorizacao"
    type = "S"
  }

  # GSI para buscar por idempotency-key
  global_secondary_index {
    name            = "idempotency-key-index"
    hash_key        = "idempotency_key"
    projection_type = "ALL"
    
    attribute {
      name = "idempotency_key"
      type = "S"
    }
  }

  tags = {
    Name        = local.table_authorizations
    Description = "Tabela de autorizações de transações"
  }
}

# 3. Tabela de Contabilização
resource "aws_dynamodb_table" "accounting" {
  name           = local.table_accounting
  billing_mode   = var.dynamodb_billing_mode
  hash_key       = "event_id"
  attribute {
    name = "event_id"
    type = "S"
  }

  tags = {
    Name        = local.table_accounting
    Description = "Tabela de eventos contábeis"
  }
}

# 4. Tabela de Locks (Distributed Locking)
resource "aws_dynamodb_table" "locks" {
  name           = local.table_locks
  billing_mode   = var.dynamodb_billing_mode
  hash_key       = "lock_key"
  attribute {
    name = "lock_key"
    type = "S"
  }

  # TTL: expiry_time (30 segundos)
  ttl {
    attribute_name = "expiry_time"
    enabled        = true
  }

  tags = {
    Name        = local.table_locks
    Description = "Tabela de locks distribuídos (TTL: 30s)"
  }
}

# 5. Tabela de Rate Limit
resource "aws_dynamodb_table" "rate_limit" {
  name           = local.table_rate_limit
  billing_mode   = var.dynamodb_billing_mode
  hash_key       = "account_id"
  attribute {
    name = "account_id"
    type = "S"
  }

  # TTL: expiry_time (1 segundo)
  ttl {
    attribute_name = "expiry_time"
    enabled        = true
  }

  tags = {
    Name        = local.table_rate_limit
    Description = "Tabela de rate limiting por conta (TTL: 1s)"
  }
}
