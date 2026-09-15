#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

AUTH_HEALTH="http://localhost:8080/actuator/health"
LIMITS_HEALTH="http://localhost:8082/actuator/health"
ACCOUNTING_HEALTH="http://localhost:8083/actuator/health"

AUTH_PID="-"
LIMITS_PID="-"
ACCOUNTING_PID="-"

wait_for_url() {
    local name="$1"
    local url="$2"
    local log_file="$3"

    echo "⏳ Aguardando $name..."

    for i in {1..30}; do
        if curl -sf --connect-timeout 2 "$url" > /dev/null; then
            echo "✅ $name disponível"
            return 0
        fi

        if [ "$i" -eq 30 ]; then
            echo "❌ $name não iniciou corretamente"
            echo "Verifique: tail -f $log_file"
            return 1
        fi

        sleep 2
    done
}

start_service_if_needed() {
    local name="$1"
    local module="$2"
    local health_url="$3"
    local log_file="$4"
    local pid_var="$5"

    if curl -sf --connect-timeout 2 "$health_url" > /dev/null; then
        echo "✅ $name já está em execução"
        printf -v "$pid_var" '%s' "já em execução"
        return 0
    fi

    echo "🚀 Iniciando $name..."

    nohup mvn -pl "$module" spring-boot:run \
        > "$log_file" 2>&1 &

    local pid=$!
    printf -v "$pid_var" '%s' "$pid"

    wait_for_url "$name" "$health_url" "$log_file"
}

echo "🚀 Iniciando ambiente local POD99..."
echo ""

echo "🐳 Subindo LocalStack e DynamoDB..."
docker compose up -d localstack dynamodb-local

echo ""
echo "⏳ Aguardando LocalStack ficar disponível..."

for i in {1..30}; do
    if curl -sf --connect-timeout 2 \
        http://localhost:4566/_localstack/health > /dev/null; then
        echo "✅ LocalStack disponível"
        break
    fi

    if [ "$i" -eq 30 ]; then
        echo "❌ LocalStack não ficou disponível a tempo"
        exit 1
    fi

    sleep 2
done

echo ""
echo "🔧 Configurando rede Docker..."
./scripts/setup-docker-network.sh

echo ""
echo "🏗️ Aplicando infraestrutura Terraform..."

(
    cd infra/terraform
    terraform init
    terraform apply -auto-approve
)

echo ""
echo "🌐 Iniciando API Gateway Simulator..."
docker compose up -d api-gateway-simulator

echo ""
echo "🚀 Verificando microsserviços Java..."

start_service_if_needed \
    "Authorization Service" \
    "authorization-service" \
    "$AUTH_HEALTH" \
    "/tmp/authorization.log" \
    AUTH_PID

echo ""

start_service_if_needed \
    "Limits Service" \
    "limits-service" \
    "$LIMITS_HEALTH" \
    "/tmp/limits.log" \
    LIMITS_PID

echo ""

start_service_if_needed \
    "Accounting Service" \
    "accounting-service" \
    "$ACCOUNTING_HEALTH" \
    "/tmp/accounting.log" \
    ACCOUNTING_PID

echo ""
echo "============================================================"
echo "✅ Ambiente local POD99 iniciado com sucesso!"
echo "============================================================"
echo ""
echo "📍 API Gateway Simulator: http://localhost:8081"
echo "📍 Authorization Service: http://localhost:8080"
echo "📍 Limits Service:        http://localhost:8082"
echo "📍 Accounting Service:    http://localhost:8083"
echo "📍 LocalStack:            http://localhost:4566"
echo "📍 DynamoDB Local:        http://localhost:8000"
echo ""
echo "📨 SQS Accounting:"
echo "   pod99-accounting-queue.fifo"
echo ""
echo "🧾 Processos Java:"
echo "   Authorization: $AUTH_PID"
echo "   Limits:        $LIMITS_PID"
echo "   Accounting:    $ACCOUNTING_PID"
echo ""
echo "📋 Logs:"
echo "   tail -f /tmp/authorization.log"
echo "   tail -f /tmp/limits.log"
echo "   tail -f /tmp/accounting.log"
echo ""
echo "🧪 Teste pela API Gateway:"
echo ""
echo "curl -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes \\"
echo '  -H "Content-Type: application/json" \'
echo '  -H "Authorization: Bearer jwt-ACC-001" \'
echo '  -H "Idempotency-Key: teste-001" \'
echo "  -d '{\"idConta\":\"ACC-001\",\"valor\":50.00,\"moeda\":\"BRL\",\"tipoOperacao\":\"DEBITO\"}'"
echo ""
echo "============================================================"