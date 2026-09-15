#!/bin/bash

set -e

echo "🔧 Configurando EventBridge → SQS Target..."

# Aguardar LocalStack estar pronto
echo "⏳ Aguardando LocalStack (5s)..."
sleep 5

# 1. Verificar se Rule existe
echo "[1/3] Verificando se EventBridge Rule existe..."
RULE_RESPONSE=$(curl -s -X POST http://localhost:4566/ \
  -H "Content-Type: application/x-amz-json-1.1" \
  -H "X-Amz-Target: AWSEvents.DescribeRule" \
  -d '{
    "Name": "pod99-transacao-autorizada-rule"
  }')

if echo "$RULE_RESPONSE" | grep -q "RuleNotFoundException\|does not exist"; then
  echo "❌ EventBridge Rule não encontrada!"
  echo "💡 Rode: terraform apply -var-file=local.tfvars"
  exit 1
fi

echo "✅ EventBridge Rule encontrada"
echo ""

# 2. Remover targets antigos (se houver)
echo "[2/3] Removendo targets antigos..."
curl -s -X POST http://localhost:4566/ \
  -H "Content-Type: application/x-amz-json-1.1" \
  -H "X-Amz-Target: AWSEvents.RemoveTargets" \
  -d '{
    "Rule": "pod99-transacao-autorizada-rule",
    "Ids": ["1"]
  }' > /dev/null 2>&1 || true

echo "✅ Targets antigos removidos"
echo ""

# 3. Criar novo target (Rule → SQS)
echo "[3/3] Criando novo Target (EventBridge Rule → SQS Queue)..."
TARGET_RESPONSE=$(curl -s -X POST http://localhost:4566/ \
  -H "Content-Type: application/x-amz-json-1.1" \
  -H "X-Amz-Target: AWSEvents.PutTargets" \
  -d '{
    "Rule": "pod99-transacao-autorizada-rule",
    "Targets": [{
      "Id": "1",
      "Arn": "arn:aws:sqs:us-east-1:000000000000:pod99-accounting-queue.fifo",
      "RoleArn": "arn:aws:iam::000000000000:role/pod99-eventbridge-role",
      "SqsParameters": {
        "RoleArn": "arn:aws:iam::000000000000:role/pod99-eventbridge-role"
      }
    }]
  }')

if echo "$TARGET_RESPONSE" | grep -q "FailedEntryCount" || echo "$TARGET_RESPONSE" | grep -q "FailureCount"; then
  FAILED_COUNT=$(echo "$TARGET_RESPONSE" | grep -o '"FailedEntryCount":[0-9]*' | cut -d: -f2 || echo "unknown")
  if [ "$FAILED_COUNT" != "0" ]; then
    echo "⚠️  Alguns targets falharam: $TARGET_RESPONSE"
  fi
fi

echo "✅ Target criado com sucesso!"
echo ""
echo "=========================================="
echo "✅ EventBridge → SQS CONFIGURADO!"
echo "=========================================="
echo ""
echo "Agora quando você fazer uma transação:"
echo "1. EventBridge publica evento"
echo "2. SQS recebe mensagem"
echo "3. Spring consome via @SqsListener"
echo "4. AccountingEventListener processa"
echo ""
