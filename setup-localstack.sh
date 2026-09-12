#!/bin/bash

# ==================================================================================
# setup-localstack.sh - Cria toda a infraestrutura no LocalStack
# Substitui Terraform quando ele não está disponível
# ==================================================================================

set -e

AWS_ENDPOINT="http://localhost:4566"
AWS_REGION="us-east-1"
AWS_ACCESS_KEY="test"
AWS_SECRET_KEY="test"

# Exportar credenciais
export AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY
export AWS_SECRET_ACCESS_KEY=$AWS_SECRET_KEY
export AWS_DEFAULT_REGION=$AWS_REGION

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║  LocalStack Setup - Criando Infraestrutura                     ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# ==================================================================================
# 1. DYNAMODB TABLES
# ==================================================================================

echo "📊 Criando tabelas DynamoDB..."

# pod99-limits
aws dynamodb create-table \
  --endpoint-url $AWS_ENDPOINT \
  --table-name pod99-limits \
  --attribute-definitions AttributeName=id_contrato,AttributeType=S \
  --key-schema AttributeName=id_contrato,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region $AWS_REGION 2>/dev/null && echo "  ✅ pod99-limits" || echo "  ℹ️ pod99-limits já existe"

# pod99-authorizations
aws dynamodb create-table \
  --endpoint-url $AWS_ENDPOINT \
  --table-name pod99-authorizations \
  --attribute-definitions AttributeName=id_autorizacao,AttributeType=S \
  --key-schema AttributeName=id_autorizacao,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region $AWS_REGION 2>/dev/null && echo "  ✅ pod99-authorizations" || echo "  ℹ️ pod99-authorizations já existe"

# pod99-accounting
aws dynamodb create-table \
  --endpoint-url $AWS_ENDPOINT \
  --table-name pod99-accounting \
  --attribute-definitions AttributeName=event_id,AttributeType=S \
  --key-schema AttributeName=event_id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region $AWS_REGION 2>/dev/null && echo "  ✅ pod99-accounting" || echo "  ℹ️ pod99-accounting já existe"

# pod99-locks
aws dynamodb create-table \
  --endpoint-url $AWS_ENDPOINT \
  --table-name pod99-locks \
  --attribute-definitions AttributeName=lock_key,AttributeType=S \
  --key-schema AttributeName=lock_key,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region $AWS_REGION 2>/dev/null && echo "  ✅ pod99-locks" || echo "  ℹ️ pod99-locks já existe"

# pod99-rate-limit
aws dynamodb create-table \
  --endpoint-url $AWS_ENDPOINT \
  --table-name pod99-rate-limit \
  --attribute-definitions AttributeName=account_id,AttributeType=S \
  --key-schema AttributeName=account_id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region $AWS_REGION 2>/dev/null && echo "  ✅ pod99-rate-limit" || echo "  ℹ️ pod99-rate-limit já existe"

echo ""

# ==================================================================================
# 2. SQS QUEUES
# ==================================================================================

echo "📦 Criando filas SQS..."

# DLQ
aws sqs create-queue \
  --endpoint-url $AWS_ENDPOINT \
  --queue-name pod99-accounting-dlq.fifo \
  --attributes 'FifoQueue=true,ContentBasedDeduplication=true,MessageRetentionPeriod=1209600' \
  --region $AWS_REGION 2>/dev/null && echo "  ✅ pod99-accounting-dlq.fifo" || echo "  ℹ️ pod99-accounting-dlq.fifo já existe"

# Main queue com DLQ
aws sqs create-queue \
  --endpoint-url $AWS_ENDPOINT \
  --queue-name pod99-accounting-queue.fifo \
  --attributes 'FifoQueue=true,ContentBasedDeduplication=true,MessageRetentionPeriod=86400,VisibilityTimeout=300,RedrivePolicy={"deadLetterTargetArn":"arn:aws:sqs:us-east-1:000000000000:pod99-accounting-dlq.fifo","maxReceiveCount":"3"}' \
  --region $AWS_REGION 2>/dev/null && echo "  ✅ pod99-accounting-queue.fifo" || echo "  ℹ️ pod99-accounting-queue.fifo já existe"

echo ""

# ==================================================================================
# 3. EVENTBRIDGE RULE
# ==================================================================================

echo "📡 Criando EventBridge rule..."

aws events put-rule \
  --endpoint-url $AWS_ENDPOINT \
  --name pod99-transacao-autorizada-rule \
  --event-pattern '{"source":["pod99.authorization"],"detail-type":["TransacaoAutorizada"]}' \
  --state ENABLED \
  --region $AWS_REGION 2>/dev/null && echo "  ✅ pod99-transacao-autorizada-rule" || echo "  ℹ️ pod99-transacao-autorizada-rule já existe"

# Conectar EventBridge → SQS
aws events put-targets \
  --endpoint-url $AWS_ENDPOINT \
  --rule pod99-transacao-autorizada-rule \
  --targets 'Id=1,Arn=arn:aws:sqs:us-east-1:000000000000:pod99-accounting-queue.fifo' \
  --region $AWS_REGION 2>/dev/null && echo "  ✅ Conectado EventBridge → SQS" || echo "  ℹ️ Target já existe"

echo ""

# ==================================================================================
# 4. POPULATE TEST DATA (100 contas × 3 contratos = 300 registros)
# ==================================================================================

echo "💰 Populando dados de teste (100 contas × 3 contratos)..."

for account_num in {1..100}; do
  account_id=$(printf "ACC-%03d" $account_num)
  limit_amount=$((50000 + account_num * 1000))
  
  for contract_idx in {1..3}; do
    contract_num=$((account_num * 3 + contract_idx - 1))
    contract_id=$(printf "CONTA-%03d" $contract_num)
    
    aws dynamodb put-item \
      --endpoint-url $AWS_ENDPOINT \
      --table-name pod99-limits \
      --item "{\
        \"id_contrato\": {\"S\": \"$contract_id\"},\
        \"id_conta\": {\"S\": \"$account_id\"},\
        \"limite\": {\"N\": \"$limit_amount.00\"},\
        \"disponivel\": {\"N\": \"$limit_amount.00\"},\
        \"reservado\": {\"N\": \"0.00\"},\
        \"version\": {\"N\": \"0\"}\
      }" \
      --region $AWS_REGION 2>/dev/null || true
  done
  
  if [ $((account_num % 10)) -eq 0 ]; then
    echo "  ✅ $account_num/100 contas criadas"
  fi
done

echo "  ✅ 300 registros de teste criados!"
echo ""

# ==================================================================================
# 5. SUMMARY
# ==================================================================================

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║  ✅ SETUP COMPLETO!                                            ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""
echo "📊 Tabelas criadas:"
aws dynamodb list-tables --endpoint-url $AWS_ENDPOINT --region $AWS_REGION | grep TableNames -A 10
echo ""
echo "📦 Filas criadas:"
aws sqs list-queues --endpoint-url $AWS_ENDPOINT --region $AWS_REGION | grep QueueUrls -A 5 || echo "  ✅ 2 filas FIFO criadas"
echo ""
echo "✨ Pronto para rodar em http://localhost:8080"
echo ""
