#!/usr/bin/env bash

set -euo pipefail

LOCALSTACK_URL="${LOCALSTACK_URL:-http://localhost:4566}"
REGION="${AWS_REGION:-us-east-1}"
TABLE_NAME="pod99-limits"

export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
export AWS_DEFAULT_REGION="$REGION"

echo "============================================================"
echo "📊 POD99 - População de dados de teste"
echo "============================================================"
echo ""
echo "100 contas × 3 contratos = 300 registros"
echo ""

echo "⏳ Aguardando LocalStack..."

for i in {1..30}; do
    if curl -sf "$LOCALSTACK_URL/_localstack/health" > /dev/null 2>&1; then
        echo "✅ LocalStack disponível"
        break
    fi

    if [ "$i" -eq 30 ]; then
        echo "❌ LocalStack não ficou disponível"
        exit 1
    fi

    sleep 2
done

echo ""
echo "🔍 Verificando tabela $TABLE_NAME..."

if ! aws \
    --endpoint-url="$LOCALSTACK_URL" \
    dynamodb describe-table \
    --table-name "$TABLE_NAME" \
    --region "$REGION" \
    > /dev/null 2>&1; then

    echo "❌ Tabela DynamoDB não encontrada: $TABLE_NAME"
    echo "   Execute primeiro: ./scripts/start-local.sh"
    exit 1
fi

echo "✅ Tabela encontrada"
echo ""
echo "📥 Inserindo registros..."

TOTAL=0

for account_num in $(seq 1 100); do

    account_id=$(printf "ACC-%03d" "$account_num")

    for contract_num in $(seq 1 3); do

        global_contract_num=$(( (account_num - 1) * 3 + contract_num ))

        contract_id=$(printf "CONTA-%03d" "$global_contract_num")

        limite=$((50000 + account_num * 1000))

        aws \
            --endpoint-url="$LOCALSTACK_URL" \
            dynamodb put-item \
            --table-name "$TABLE_NAME" \
            --region "$REGION" \
            --item "{
                \"id_contrato\": {\"S\": \"$contract_id\"},
                \"id_conta\": {\"S\": \"$account_id\"},
                \"limite\": {\"N\": \"${limite}.00\"},
                \"disponivel\": {\"N\": \"${limite}.00\"},
                \"reservado\": {\"N\": \"0.00\"},
                \"version\": {\"N\": \"0\"}
            }" \
            > /dev/null

        TOTAL=$((TOTAL + 1))

        if [ $((TOTAL % 30)) -eq 0 ]; then
            echo "   ✓ $TOTAL/300 registros inseridos"
        fi
    done
done

echo ""
echo "🔍 Validando quantidade de registros..."

COUNT=$(aws \
    --endpoint-url="$LOCALSTACK_URL" \
    dynamodb scan \
    --table-name "$TABLE_NAME" \
    --region "$REGION" \
    --select COUNT \
    --query 'Count' \
    --output text)

if [ "$COUNT" -lt 300 ]; then
    echo "❌ Quantidade inesperada de registros"
    echo "   Esperado: pelo menos 300"
    echo "   Encontrado: $COUNT"
    exit 1
fi

echo "✅ Registros encontrados: $COUNT"

echo ""
echo "🔍 Validando CONTA-001..."

ITEM=$(aws \
    --endpoint-url="$LOCALSTACK_URL" \
    dynamodb get-item \
    --table-name "$TABLE_NAME" \
    --region "$REGION" \
    --key '{"id_contrato":{"S":"CONTA-001"}}' \
    --output json)

ACCOUNT=$(echo "$ITEM" | jq -r '.Item.id_conta.S // empty')
LIMIT=$(echo "$ITEM" | jq -r '.Item.limite.N // empty')

if [ "$ACCOUNT" != "ACC-001" ]; then
    echo "❌ CONTA-001 não está associada à ACC-001"
    exit 1
fi

echo "✅ CONTA-001 → ACC-001"
echo "   Limite: R$ $LIMIT"

echo ""
echo "============================================================"
echo "✅ POPULAÇÃO CONCLUÍDA"
echo "============================================================"
echo ""
echo "Contas:             ACC-001 até ACC-100"
echo "Contratos:          CONTA-001 até CONTA-300"
echo "Contratos por conta: 3"
echo "Registros inseridos: $TOTAL"
echo ""