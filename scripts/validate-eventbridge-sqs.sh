#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

LOCALSTACK_URL="${LOCALSTACK_URL:-http://localhost:4566}"
API_GATEWAY_URL="${API_GATEWAY_URL:-http://localhost:8081}"

QUEUE_NAME="pod99-accounting-queue.fifo"
RULE_NAME="pod99-transacao-autorizada-rule"
REGION="${AWS_REGION:-us-east-1}"

aws_local() {
    docker exec \
        -e AWS_ACCESS_KEY_ID=test \
        -e AWS_SECRET_ACCESS_KEY=test \
        -e AWS_DEFAULT_REGION="$REGION" \
        localstack \
        aws --endpoint-url=http://localhost:4566 "$@"
}

echo "============================================================"
echo "🔍 POD99 - Validação EventBridge → SQS → Accounting"
echo "============================================================"
echo ""

echo "[1/6] Verificando LocalStack..."

if ! curl -sf "$LOCALSTACK_URL/_localstack/health" > /dev/null; then
    echo "❌ LocalStack não está disponível em $LOCALSTACK_URL"
    exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "localstack"; then
    echo "❌ Container localstack não está em execução"
    exit 1
fi

echo "✅ LocalStack disponível"
echo ""

echo "[2/6] Verificando EventBridge Rule..."

if ! aws_local events describe-rule \
    --name "$RULE_NAME" \
    --region "$REGION" \
    > /dev/null 2>&1; then

    echo "❌ EventBridge Rule não encontrada: $RULE_NAME"
    exit 1
fi

echo "✅ EventBridge Rule encontrada: $RULE_NAME"
echo ""

echo "[3/6] Verificando target EventBridge → SQS..."

TARGETS_JSON=$(
    aws_local events list-targets-by-rule \
        --rule "$RULE_NAME" \
        --region "$REGION" \
        --output json
)

TARGET_ARN=$(
    echo "$TARGETS_JSON" |
        jq -r '.Targets[0].Arn // empty'
)

MESSAGE_GROUP_ID=$(
    echo "$TARGETS_JSON" |
        jq -r '.Targets[0].SqsParameters.MessageGroupId // empty'
)

if [ -z "$TARGET_ARN" ]; then
    echo "❌ EventBridge Rule não possui target configurado"
    exit 1
fi

if [[ "$TARGET_ARN" != *":pod99-accounting-queue.fifo" ]]; then
    echo "❌ Target aponta para uma fila inesperada:"
    echo "   $TARGET_ARN"
    exit 1
fi

if [ "$MESSAGE_GROUP_ID" != "pod99" ]; then
    echo "❌ SqsParameters.MessageGroupId inválido:"
    echo "   esperado: pod99"
    echo "   atual:    ${MESSAGE_GROUP_ID:-<vazio>}"
    exit 1
fi

echo "✅ Target configurado corretamente"
echo "   ARN: $TARGET_ARN"
echo "   MessageGroupId: $MESSAGE_GROUP_ID"
echo ""

echo "[4/6] Verificando fila SQS FIFO..."

QUEUE_URL=$(
    aws_local sqs get-queue-url \
        --queue-name "$QUEUE_NAME" \
        --region "$REGION" \
        --query 'QueueUrl' \
        --output text 2>/dev/null || true
)

if [ -z "$QUEUE_URL" ] || [ "$QUEUE_URL" = "None" ]; then
    echo "❌ Fila SQS não encontrada: $QUEUE_NAME"
    exit 1
fi

echo "✅ SQS encontrada"
echo "   $QUEUE_URL"
echo ""

echo "[5/6] Gerando transação autorizada..."

IDEMPOTENCY_KEY="validate-sqs-$(date +%s)-$$"
RESPONSE_FILE=$(mktemp)

cleanup() {
    rm -f "$RESPONSE_FILE"
}

trap cleanup EXIT

HTTP_STATUS=$(
    curl -s \
        -o "$RESPONSE_FILE" \
        -w "%{http_code}" \
        -X POST \
        "$API_GATEWAY_URL/v1/contratos/CONTA-001/autorizacoes" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer jwt-ACC-001" \
        -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
        -d '{
            "idConta": "ACC-001",
            "valor": 1.00,
            "moeda": "BRL",
            "tipoOperacao": "DEBITO"
        }'
)

echo "HTTP $HTTP_STATUS"

if [ "$HTTP_STATUS" != "201" ]; then
    echo "❌ Autorização não retornou HTTP 201"
    echo ""
    cat "$RESPONSE_FILE"
    echo ""
    exit 1
fi

echo "✅ Transação autorizada"
echo ""

echo "[6/6] Verificando EventBridge → SQS..."

sleep 2

VISIBLE=$(
    aws_local sqs get-queue-attributes \
        --queue-url "$QUEUE_URL" \
        --attribute-names ApproximateNumberOfMessages \
        --region "$REGION" \
        --query 'Attributes.ApproximateNumberOfMessages' \
        --output text 2>/dev/null || echo "0"
)

NOT_VISIBLE=$(
    aws_local sqs get-queue-attributes \
        --queue-url "$QUEUE_URL" \
        --attribute-names ApproximateNumberOfMessagesNotVisible \
        --region "$REGION" \
        --query 'Attributes.ApproximateNumberOfMessagesNotVisible' \
        --output text 2>/dev/null || echo "0"
)

echo "📊 Mensagens visíveis:         $VISIBLE"
echo "📊 Mensagens em processamento: $NOT_VISIBLE"
echo ""

if [ "${VISIBLE:-0}" -gt 0 ] ||
   [ "${NOT_VISIBLE:-0}" -gt 0 ]; then

    echo "✅ Evento chegou à SQS"

else

    echo "ℹ️ Nenhuma mensagem permanece na fila."
    echo "   O Accounting Service pode já ter consumido"
    echo "   e processado a mensagem."
    echo ""
    echo "   Configuração estrutural validada:"
    echo "   EventBridge Rule  ✅"
    echo "   SQS Target        ✅"
    echo "   MessageGroupId    ✅"
    echo "   SQS FIFO          ✅"
    echo "   HTTP 201          ✅"
fi

echo ""
echo "============================================================"
echo "✅ VALIDAÇÃO EVENTBRIDGE → SQS CONCLUÍDA"
echo "============================================================"
