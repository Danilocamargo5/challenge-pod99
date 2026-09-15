#!/usr/bin/env bash

set -euo pipefail

API_URL="${API_URL:-http://localhost:8081}"
CONTRACT_ID="${CONTRACT_ID:-CONTA-001}"
ACCOUNT_ID="${ACCOUNT_ID:-ACC-001}"
TOKEN="${TOKEN:-jwt-ACC-001}"

TOTAL_REQUESTS="${TOTAL_REQUESTS:-100}"
CONCURRENCY="${CONCURRENCY:-10}"
AMOUNT="${AMOUNT:-0.01}"

RESULT_DIR=$(mktemp -d)
START_NS=0

cleanup() {
    rm -rf "$RESULT_DIR"
}
trap cleanup EXIT

echo "============================================================"
echo "🚀 POD99 - Load Test"
echo "============================================================"
echo ""
echo "API:          $API_URL"
echo "Contrato:     $CONTRACT_ID"
echo "Conta:        $ACCOUNT_ID"
echo "Requisições:  $TOTAL_REQUESTS"
echo "Concorrência: $CONCURRENCY"
echo "Valor:        R$ $AMOUNT"
echo ""

if ! command -v curl >/dev/null 2>&1; then
    echo "❌ curl não encontrado"
    exit 1
fi

generate_id() {
    if command -v uuidgen >/dev/null 2>&1; then
        uuidgen | tr '[:upper:]' '[:lower:]'
    else
        printf "%s-%s-%s" "$(date +%s%N)" "$$" "$RANDOM"
    fi
}

make_request() {
    local number="$1"
    local idempotency_key
    local response_file
    local metrics_file

    idempotency_key="load-$(generate_id)"
    response_file="$RESULT_DIR/response-${number}.json"
    metrics_file="$RESULT_DIR/metrics-${number}.txt"

    curl -sS \
        -o "$response_file" \
        -w "%{http_code}|%{time_total}" \
        -X POST \
        "${API_URL}/v1/contratos/${CONTRACT_ID}/autorizacoes" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${TOKEN}" \
        -H "Idempotency-Key: ${idempotency_key}" \
        -H "X-Correlation-ID: load-${number}-$(generate_id)" \
        -d "{
            \"idConta\":\"${ACCOUNT_ID}\",
            \"valor\":${AMOUNT},
            \"moeda\":\"BRL\",
            \"tipoOperacao\":\"DEBITO\"
        }" \
        > "$metrics_file" 2>/dev/null || echo "000|0" > "$metrics_file"
}

echo "🔍 Testando conectividade..."

PROBE_FILE="$RESULT_DIR/probe.json"

PROBE_STATUS=$(curl -sS \
    -o "$PROBE_FILE" \
    -w "%{http_code}" \
    -X POST \
    "${API_URL}/v1/contratos/${CONTRACT_ID}/autorizacoes" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Idempotency-Key: probe-$(generate_id)" \
    -d "{
        \"idConta\":\"${ACCOUNT_ID}\",
        \"valor\":${AMOUNT},
        \"moeda\":\"BRL\",
        \"tipoOperacao\":\"DEBITO\"
    }" 2>/dev/null || true)

if [ "$PROBE_STATUS" != "201" ]; then
    echo "❌ API não está pronta para o teste. HTTP ${PROBE_STATUS:-000}"
    cat "$PROBE_FILE" 2>/dev/null || true
    echo ""
    exit 1
fi

echo "✅ API respondeu HTTP 201"
echo ""
echo "🔥 Iniciando carga..."

START_NS=$(date +%s%N)

running=0

for ((i=1; i<=TOTAL_REQUESTS; i++)); do
    make_request "$i" &
    running=$((running + 1))

    if [ "$running" -ge "$CONCURRENCY" ]; then
        wait -n || true
        running=$((running - 1))
    fi
done

wait || true

END_NS=$(date +%s%N)

ELAPSED_MS=$(( (END_NS - START_NS) / 1000000 ))

if [ "$ELAPSED_MS" -eq 0 ]; then
    ELAPSED_MS=1
fi

echo ""
echo "📊 Processando resultados..."

HTTP_201=0
HTTP_402=0
HTTP_409=0
HTTP_422=0
HTTP_4XX=0
HTTP_5XX=0
HTTP_OTHER=0
FAILED=0
TOTAL_LATENCY_US=0
PROCESSED=0

for file in "$RESULT_DIR"/metrics-*.txt; do

    [ -f "$file" ] || continue

    IFS='|' read -r status latency < "$file"

    PROCESSED=$((PROCESSED + 1))

    case "$status" in
        201)
            HTTP_201=$((HTTP_201 + 1))
            ;;
        402)
            HTTP_402=$((HTTP_402 + 1))
            ;;
        409)
            HTTP_409=$((HTTP_409 + 1))
            ;;
        422)
            HTTP_422=$((HTTP_422 + 1))
            ;;
        4??)
            HTTP_4XX=$((HTTP_4XX + 1))
            ;;
        5??)
            HTTP_5XX=$((HTTP_5XX + 1))
            ;;
        000)
            FAILED=$((FAILED + 1))
            ;;
        *)
            HTTP_OTHER=$((HTTP_OTHER + 1))
            ;;
    esac

    latency_us=$(awk -v value="${latency:-0}" \
        'BEGIN { printf "%.0f", value * 1000000 }')

    TOTAL_LATENCY_US=$((TOTAL_LATENCY_US + latency_us))
done

if [ "$PROCESSED" -gt 0 ]; then
    AVG_LATENCY_MS=$(awk \
        -v total="$TOTAL_LATENCY_US" \
        -v count="$PROCESSED" \
        'BEGIN { printf "%.2f", (total / count) / 1000 }')
else
    AVG_LATENCY_MS="0.00"
fi

TPS=$(awk \
    -v requests="$PROCESSED" \
    -v milliseconds="$ELAPSED_MS" \
    'BEGIN { printf "%.2f", requests / (milliseconds / 1000) }')

echo ""
echo "============================================================"
echo "📈 RESULTADO"
echo "============================================================"
echo ""
echo "Requisições processadas: $PROCESSED"
echo "Tempo total:             ${ELAPSED_MS} ms"
echo "TPS médio:               $TPS"
echo "Latência média:          ${AVG_LATENCY_MS} ms"
echo ""
echo "HTTP 201:                $HTTP_201"
echo "HTTP 402:                $HTTP_402"
echo "HTTP 409:                $HTTP_409"
echo "HTTP 422:                $HTTP_422"
echo "Outros HTTP 4xx:         $HTTP_4XX"
echo "HTTP 5xx:                $HTTP_5XX"
echo "Outros:                  $HTTP_OTHER"
echo "Falhas de conexão:       $FAILED"
echo ""

if [ "$HTTP_5XX" -gt 0 ] || [ "$FAILED" -gt 0 ]; then
    echo "❌ Teste encontrou falhas de infraestrutura/aplicação."
    exit 1
fi

if [ "$PROCESSED" -ne "$TOTAL_REQUESTS" ]; then
    echo "❌ Nem todas as requisições foram processadas."
    exit 1
fi

echo "✅ Teste concluído sem erros 5xx ou falhas de conexão."
echo ""
echo "Observação:"
echo "HTTP 409 pode ser esperado durante concorrência,"
echo "pois o serviço utiliza locking para proteger o contrato."