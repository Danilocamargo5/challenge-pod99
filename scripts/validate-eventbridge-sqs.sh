#!/bin/bash

set -e

LOCALSTACK_URL="http://localhost:4566"
QUEUE_NAME="pod99-accounting-queue"
REGION="us-east-1"

echo "🔍 Validando EventBridge → SQS flow..."
echo ""

# 1. Verificar se SQS queue existe
echo "[1/4] Verificando se SQS queue existe..."
QUEUE_URL=$(aws --endpoint-url="$LOCALSTACK_URL" \
  sqs get-queue-url \
  --queue-name "$QUEUE_NAME" \
  --region "$REGION" \
  --query 'QueueUrl' \
  --output text 2>/dev/null)

if [ -z "$QUEUE_URL" ]; then
  echo "❌ Fila SQS não encontrada: $QUEUE_NAME"
  exit 1
fi

echo "✅ SQS queue encontrada: $QUEUE_URL"
echo ""

# 2. Obter número de mensagens na fila (antes)
echo "[2/4] Contando mensagens na fila (ANTES)..."
ATTRIBUTES_BEFORE=$(aws --endpoint-url="$LOCALSTACK_URL" \
  sqs get-queue-attributes \
  --queue-url "$QUEUE_URL" \
  --attribute-names ApproximateNumberOfMessages \
  --region "$REGION" \
  --query 'Attributes.ApproximateNumberOfMessages' \
  --output text)

echo "📊 Mensagens na fila (ANTES): $ATTRIBUTES_BEFORE"
echo ""

# 3. Fazer uma requisição de autorização para gerar evento
echo "[3/4] Enviando requisição de autorização (gera evento)..."
IDEMPOTENCY_KEY="validate-sqs-$(date +%s%N)"

RESPONSE=$(curl -s -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer jwt-ACC-001" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "idConta":"ACC-001",
    "valor":50.00,
    "moeda":"BRL",
    "tipoOperacao":"DEBITO"
  }')

HTTP_CODE=$(echo "$RESPONSE" | jq -r '.status // .statusCode // 201')
echo "📨 Resposta HTTP: $HTTP_CODE"

if [ "$HTTP_CODE" != "201" ] && [ "$HTTP_CODE" != "200" ]; then
  echo "❌ Requisição falhou!"
  echo "$RESPONSE" | jq .
  exit 1
fi

echo "✅ Transação autorizada"
echo ""

# 4. Aguardar processamento e verificar mensagens na fila (depois)
echo "[4/4] Aguardando processamento (3 segundos)..."
sleep 3

ATTRIBUTES_AFTER=$(aws --endpoint-url="$LOCALSTACK_URL" \
  sqs get-queue-attributes \
  --queue-url "$QUEUE_URL" \
  --attribute-names ApproximateNumberOfMessages \
  --region "$REGION" \
  --query 'Attributes.ApproximateNumberOfMessages' \
  --output text)

echo "📊 Mensagens na fila (DEPOIS): $ATTRIBUTES_AFTER"
echo ""

# 5. Verificar se houve aumento de mensagens
if [ "$ATTRIBUTES_AFTER" -gt "$ATTRIBUTES_BEFORE" ]; then
  echo "✅ EventBridge → SQS FUNCIONANDO!"
  echo "   Mensagens adicionadas: $((ATTRIBUTES_AFTER - ATTRIBUTES_BEFORE))"
  
  # Tentar ler a mensagem
  echo ""
  echo "📨 Tentando ler mensagem da fila..."
  MESSAGE=$(aws --endpoint-url="$LOCALSTACK_URL" \
    sqs receive-message \
    --queue-url "$QUEUE_URL" \
    --region "$REGION" \
    --query 'Messages[0].Body' \
    --output text 2>/dev/null || echo "NENHUMA")
  
  if [ "$MESSAGE" != "NENHUMA" ]; then
    echo "✅ Mensagem recebida na SQS:"
    echo "$MESSAGE" | jq . 2>/dev/null || echo "$MESSAGE"
  fi
  
else
  echo "⚠️  Nenhuma mensagem adicionada à fila"
  echo "   (EventBridge pode não estar roteando para SQS corretamente)"
  echo ""
  echo "💡 Possíveis causas:"
  echo "   1. EventBridge Rule não foi criada pelo Terraform"
  echo "   2. Target (SQS) não foi associado à Rule"
  echo "   3. Evento não foi publicado no EventBridge"
  exit 1
fi

echo ""
echo "=========================================="
echo "✅ VALIDAÇÃO COMPLETA: EventBridge → SQS OK"
echo "=========================================="
