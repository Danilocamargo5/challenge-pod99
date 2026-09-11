#!/bin/bash

# ============================================================================
# POD99 Load Test Script
# Simula 5.000 TPS (transações por segundo)
# ============================================================================

set -e

# Configurações
API_URL="${API_URL:-http://localhost:8080}"
AUTHORIZATION_HEADER="${AUTHORIZATION_HEADER:-Authorization: Bearer test-token}"
API_KEY="${API_KEY:-test-api-key}"
DURATION_SECONDS="${DURATION_SECONDS:-60}"  # Duração do teste (segundos)
TARGET_TPS="${TARGET_TPS:-5000}"              # Target TPS
TOTAL_REQUESTS=$((TARGET_TPS * DURATION_SECONDS))
CONCURRENT_REQUESTS="${CONCURRENT_REQUESTS:-100}"  # Requisições concorrentes

# Cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'  # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}POD99 Load Test${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}Configurações:${NC}"
echo "API URL: $API_URL"
echo "Duração: ${DURATION_SECONDS}s"
echo "Target TPS: ${TARGET_TPS}"
echo "Total de requisições: ${TOTAL_REQUESTS}"
echo "Requisições concorrentes: ${CONCURRENT_REQUESTS}"
echo ""

# Função para gerar UUID
generate_uuid() {
    if command -v uuidgen &> /dev/null; then
        uuidgen | tr '[:upper:]' '[:lower:]'
    else
        python3 -c 'import uuid; print(uuid.uuid4())'
    fi
}

# Função para fazer requisição
make_request() {
    local request_number=$1
    local idempotency_key=$(generate_uuid)
    local correlation_id=$(generate_uuid)
    
    # Gerar dados aleatórios
    local id_conta="ACC-$(printf "%03d" $((RANDOM % 1000)))"
    local valor="$((10 + RANDOM % 90)).$(printf "%02d" $((RANDOM % 100)))"
    local id_estabelecimento="EST-$(printf "%03d" $((RANDOM % 500)))"
    
    # Payload
    local payload=$(cat <<EOF
{
  "idConta": "${id_conta}",
  "valor": ${valor},
  "moeda": "BRL",
  "tipoOperacao": "DEBITO",
  "idEstabelecimento": "${id_estabelecimento}",
  "metadata": {
    "request_number": ${request_number},
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  }
}
EOF
)
    
    # Fazer requisição
    start_time=$(date +%s%N)
    
    http_code=$(curl -s -o /tmp/response.json -w "%{http_code}" \
        -X POST "${API_URL}/v1/contratos/CONTRATO-001/autorizacoes" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer test-token" \
        -H "x-api-key: ${API_KEY}" \
        -H "Idempotency-Key: ${idempotency_key}" \
        -H "X-Correlation-ID: ${correlation_id}" \
        -d "${payload}" 2>/dev/null)
    
    end_time=$(date +%s%N)
    duration_ms=$(( (end_time - start_time) / 1000000 ))
    
    # Retornar código HTTP, duração e número de requisição
    echo "${http_code}|${duration_ms}|${request_number}"
}

# Teste de conexão rápido
echo -e "${YELLOW}Testando conectividade...${NC}"
http_code=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${API_URL}/v1/contratos/CONTRATO-001/autorizacoes" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer test-token" \
    -H "x-api-key: ${API_KEY}" \
    -H "Idempotency-Key: $(generate_uuid)" \
    -d '{"idConta":"TEST","valor":10.00,"moeda":"BRL","tipoOperacao":"DEBITO"}')

if [ "$http_code" == "000" ] || [ "$http_code" == "5"* ]; then
    echo -e "${RED}❌ Conexão falhou! HTTP $http_code${NC}"
    echo "API não está respondendo em $API_URL"
    exit 1
fi
echo -e "${GREEN}✅ Conectado! (HTTP $http_code)${NC}"
echo ""

# Função para processar resultados
declare -A results
declare -a latencies
total_time_start=$(date +%s%N)

# Executar requisições
echo -e "${YELLOW}Iniciando teste de carga...${NC}"
echo "Enviando ${TOTAL_REQUESTS} requisições em ${DURATION_SECONDS} segundos..."
echo ""

request_count=0
concurrent_count=0

for ((i=1; i<=TOTAL_REQUESTS; i++)); do
    # Executar requisição em background
    make_request $i &
    
    ((concurrent_count++))
    ((request_count++))
    
    # Limitar requisições concorrentes
    if [ $concurrent_count -ge $CONCURRENT_REQUESTS ]; then
        # Coletar resultados de requisições backgroundjá feitas
        wait -n
        ((concurrent_count--))
    fi
    
    # Mostrar progresso a cada 10%
    if [ $((i % (TOTAL_REQUESTS / 10))) -eq 0 ]; then
        echo -e "${BLUE}Progresso: $((i * 100 / TOTAL_REQUESTS))% (${i}/${TOTAL_REQUESTS})${NC}"
    fi
done

# Aguardar todas as requisições completarem
wait

total_time_end=$(date +%s%N)
total_time_ms=$(( (total_time_end - total_time_start) / 1000000 ))
actual_tps=$(echo "scale=2; $TOTAL_REQUESTS / ($total_time_ms / 1000)" | bc)

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}Teste Concluído!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}Resumo de Resultados:${NC}"
echo "Total de requisições: ${TOTAL_REQUESTS}"
echo "Tempo total: ${total_time_ms}ms ($(echo "scale=2; $total_time_ms / 1000" | bc)s)"
echo "TPS Real: ${actual_tps}"
echo "Target TPS: ${TARGET_TPS}"
echo ""

# Analisar códigos HTTP dos arquivos de resposta
echo -e "${YELLOW}Distribuição de Códigos HTTP:${NC}"
grep -h "error_code" /tmp/response.json 2>/dev/null | sort | uniq -c || echo "Erro ao ler respostas"

echo ""
echo -e "${GREEN}✅ Teste de carga concluído!${NC}"
echo ""
echo "Para mais detalhes:"
echo "  - Verifique os logs da aplicação"
echo "  - AWS CloudWatch → Métricas"
echo "  - AWS X-Ray → Traces"
