#!/bin/bash

set -e

echo "🚀 Inicializando LocalStack..."

# Aguarda LocalStack ficar pronto
until curl -s http://localhost:4566/_localstack/health | grep -q '"services"'; do
  echo "⏳ Aguardando LocalStack..."
  sleep 2
done

echo "✅ LocalStack pronto"

# Cria tabelas DynamoDB
echo "📊 Criando tabelas DynamoDB..."

awslocal dynamodb create-table \
  --table-name pod99-limits \
  --attribute-definitions AttributeName=id_contrato,AttributeType=S \
  --key-schema AttributeName=id_contrato,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1 2>/dev/null || echo "Tabela pod99-limits já existe"

awslocal dynamodb create-table \
  --table-name pod99-authorizations \
  --attribute-definitions AttributeName=id_autorizacao,AttributeType=S \
  --key-schema AttributeName=id_autorizacao,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1 2>/dev/null || echo "Tabela pod99-authorizations já existe"

awslocal dynamodb create-table \
  --table-name pod99-accounting \
  --attribute-definitions AttributeName=event_id,AttributeType=S \
  --key-schema AttributeName=event_id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1 2>/dev/null || echo "Tabela pod99-accounting já existe"

awslocal dynamodb create-table \
  --table-name pod99-locks \
  --attribute-definitions AttributeName=lock_key,AttributeType=S \
  --key-schema AttributeName=lock_key,KeyType=HASH \
  --ttl-specification "Enabled=true,AttributeName=expiry_time" \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1 2>/dev/null || echo "Tabela pod99-locks já existe"

awslocal dynamodb create-table \
  --table-name pod99-rate-limit \
  --attribute-definitions AttributeName=account_id,AttributeType=S \
  --key-schema AttributeName=account_id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1 2>/dev/null || echo "Tabela pod99-rate-limit já existe"

# Insere um contrato de teste com limite
echo "💰 Inserindo dados de teste..."

awslocal dynamodb put-item \
  --table-name pod99-limits \
  --item '{
    "id_contrato": {"S": "CONTA-001"},
    "limite": {"N": "100000.00"},
    "disponivel": {"N": "100000.00"},
    "reservado": {"N": "0.00"},
    "version": {"N": "0"}
  }' \
  --region us-east-1 2>/dev/null || echo "Item já existe"

# Cria SQS Queue para contabilidade
echo "📦 Criando SQS Queue..."

awslocal sqs create-queue \
  --queue-name pod99-accounting-queue \
  --attributes '{
    "MessageRetentionPeriod": "86400",
    "VisibilityTimeout": "300"
  }' \
  --region us-east-1 2>/dev/null || echo "Fila pod99-accounting-queue já existe"

# Cria EventBridge Rule
echo "📡 Criando EventBridge Rule..."

awslocal events put-rule \
  --name "pod99-transacao-autorizada-rule" \
  --event-pattern '{
    "source": ["pod99.authorization"],
    "detail-type": ["TransacaoAutorizada"]
  }' \
  --state ENABLED \
  --region us-east-1 2>/dev/null || echo "Rule já existe"

# Conecta EventBridge → SQS
echo "🔗 Conectando EventBridge → SQS..."

QUEUE_URL=$(awslocal sqs get-queue-url --queue-name pod99-accounting-queue --region us-east-1 | grep QueueUrl | awk -F'"' '{print $4}')
QUEUE_ARN="arn:aws:sqs:us-east-1:000000000000:pod99-accounting-queue"

awslocal events put-targets \
  --rule "pod99-transacao-autorizada-rule" \
  --targets "Id=1,Arn=$QUEUE_ARN" \
  --region us-east-1 2>/dev/null || echo "Target já existe"

# Cria API Gateway REST API
echo "🌐 Criando API Gateway..."

API_ID=$(awslocal apigateway create-rest-api \
  --name "pod99-authorization-api" \
  --description "POD99 Authorization API" \
  --region us-east-1 | grep id | awk -F'"' '{print $4}' | head -1)

echo "✨ Setup completo!"
echo "📊 Tabelas criadas:"
awslocal dynamodb list-tables --region us-east-1
echo ""
echo "📦 Filas criadas:"
awslocal sqs list-queues --region us-east-1
echo ""
echo "✅ POD99 pronta para rodar em http://localhost:8080"
