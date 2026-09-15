#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

LOCALSTACK_URL="${LOCALSTACK_URL:-http://localhost:4566}"
REGION="${AWS_REGION:-us-east-1}"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

ERRORS=0
WARNINGS=0

ok() {
    echo -e "${GREEN}✅ $1${NC}"
}

fail() {
    echo -e "${RED}❌ $1${NC}"
    ERRORS=$((ERRORS + 1))
}

warn() {
    echo -e "${YELLOW}⚠️  $1${NC}"
    WARNINGS=$((WARNINGS + 1))
}

section() {
    echo ""
    echo "============================================================"
    echo "$1"
    echo "============================================================"
    echo ""
}

container_running() {
    docker ps --format '{{.Names}}' | grep -qx "$1"
}

http_check() {
    local url="$1"
    local description="$2"

    if curl -sf --connect-timeout 3 "$url" > /dev/null 2>&1; then
        ok "$description"
    else
        fail "$description não respondeu em $url"
    fi
}

aws_local() {
    docker exec \
        -e AWS_ACCESS_KEY_ID=test \
        -e AWS_SECRET_ACCESS_KEY=test \
        -e AWS_DEFAULT_REGION="$REGION" \
        localstack \
        aws --endpoint-url=http://localhost:4566 "$@"
}

echo "============================================================"
echo "🔍 POD99 - VALIDAÇÃO DA INFRAESTRUTURA LOCAL"
echo "============================================================"

section "1. FERRAMENTAS NECESSÁRIAS"

for command in docker terraform mvn curl jq; do
    if command -v "$command" > /dev/null 2>&1; then
        ok "$command encontrado"
    else
        fail "$command não encontrado no PATH"
    fi
done

section "2. ESTRUTURA DO PROJETO"

required_files=(
    "pom.xml"
    "docker-compose.yml"
    "infra/terraform/main.tf"
    "infra/terraform/sqs.tf"
    "infra/terraform/eventbridge.tf"
    "scripts/start-local.sh"
    "scripts/validate-eventbridge-sqs.sh"
    "scripts/setup-docker-network.sh"
)

for file in "${required_files[@]}"; do
    if [ -f "$file" ]; then
        ok "$file"
    else
        fail "Arquivo não encontrado: $file"
    fi
done

for module in \
    common-lib \
    authorization-service \
    limits-service \
    accounting-service; do

    if [ -f "$module/pom.xml" ]; then
        ok "Módulo Maven: $module"
    else
        fail "Módulo Maven inválido ou ausente: $module"
    fi
done

section "3. SCRIPTS"

scripts=(
    "scripts/start-local.sh"
    "scripts/setup-docker-network.sh"
    "scripts/validate-eventbridge-sqs.sh"
    "scripts/validate-infra.sh"
)

for script in "${scripts[@]}"; do

    if [ ! -f "$script" ]; then
        fail "Script não encontrado: $script"
        continue
    fi

    if bash -n "$script"; then
        ok "Sintaxe válida: $script"
    else
        fail "Erro de sintaxe: $script"
    fi

    if [ -x "$script" ]; then
        ok "Executável: $script"
    else
        warn "Sem permissão de execução: $script"
    fi
done

section "4. TERRAFORM"

if terraform -chdir=infra/terraform fmt -check > /dev/null; then
    ok "terraform fmt -check"
else
    fail "Terraform possui arquivos sem formatação"
fi

if terraform -chdir=infra/terraform validate > /dev/null; then
    ok "terraform validate"
else
    fail "terraform validate"
fi

section "5. MAVEN"

if mvn -q -DskipTests compile; then
    ok "Compilação Maven"
else
    fail "Falha na compilação Maven"
fi

section "6. DOCKER"

if ! docker info > /dev/null 2>&1; then
    fail "Docker não está disponível"
else
    ok "Docker disponível"

    if container_running "localstack"; then
        ok "LocalStack rodando"
        http_check "$LOCALSTACK_URL/_localstack/health" \
            "LocalStack Health"
    else
        fail "LocalStack não está rodando"
    fi

    if container_running "dynamodb-local"; then
        ok "DynamoDB Local rodando"
    else
        warn "DynamoDB Local não está rodando"
    fi

    if container_running "api-gateway-simulator"; then
        ok "API Gateway Simulator rodando"
    else
        warn "API Gateway Simulator não está rodando"
    fi
fi

section "7. AWS / LOCALSTACK"

if container_running "localstack" &&
   curl -sf "$LOCALSTACK_URL/_localstack/health" > /dev/null 2>&1; then

    if aws_local sqs get-queue-url \
        --queue-name "pod99-accounting-queue.fifo" \
        --region "$REGION" \
        > /dev/null 2>&1; then

        ok "SQS pod99-accounting-queue.fifo"
    else
        fail "SQS pod99-accounting-queue.fifo não encontrada"
    fi

    if aws_local sqs get-queue-url \
        --queue-name "pod99-accounting-dlq.fifo" \
        --region "$REGION" \
        > /dev/null 2>&1; then

        ok "DLQ pod99-accounting-dlq.fifo"
    else
        fail "DLQ pod99-accounting-dlq.fifo não encontrada"
    fi

    if aws_local events describe-rule \
        --name "pod99-transacao-autorizada-rule" \
        --region "$REGION" \
        > /dev/null 2>&1; then

        ok "EventBridge Rule pod99-transacao-autorizada-rule"
    else
        fail "EventBridge Rule não encontrada"
    fi

    TARGETS_JSON=$(
        aws_local events list-targets-by-rule \
            --rule "pod99-transacao-autorizada-rule" \
            --region "$REGION" \
            --output json 2>/dev/null || echo '{"Targets":[]}'
    )

    TARGET_ARN=$(
        echo "$TARGETS_JSON" |
            jq -r '.Targets[0].Arn // empty'
    )

    MESSAGE_GROUP_ID=$(
        echo "$TARGETS_JSON" |
            jq -r '.Targets[0].SqsParameters.MessageGroupId // empty'
    )

    if [[ "$TARGET_ARN" == *":pod99-accounting-queue.fifo" ]]; then
        ok "EventBridge Target → SQS FIFO"
    else
        fail "EventBridge Target não aponta para pod99-accounting-queue.fifo"
    fi

    if [ "$MESSAGE_GROUP_ID" = "pod99" ]; then
        ok "SQS MessageGroupId = pod99"
    else
        fail "MessageGroupId incorreto ou ausente"
    fi

else
    warn "LocalStack indisponível; validação dos recursos AWS foi ignorada"
fi

section "RESULTADO"

echo "Erros:    $ERRORS"
echo "Avisos:   $WARNINGS"
echo ""

if [ "$ERRORS" -gt 0 ]; then
    echo -e "${RED}❌ INFRAESTRUTURA COM PROBLEMAS${NC}"
    echo ""
    exit 1
fi

if [ "$WARNINGS" -gt 0 ]; then
    echo -e "${YELLOW}⚠️  VALIDAÇÃO CONCLUÍDA COM AVISOS${NC}"
else
    echo -e "${GREEN}✅ INFRAESTRUTURA VALIDADA COM SUCESSO${NC}"
fi

echo ""
echo "Para iniciar todo o ambiente:"
echo ""
echo "  ./scripts/start-local.sh"
echo ""
echo "Depois, para validar EventBridge → SQS:"
echo ""
echo "  ./scripts/validate-eventbridge-sqs.sh"
echo ""
