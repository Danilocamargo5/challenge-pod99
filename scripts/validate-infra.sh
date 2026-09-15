#!/bin/bash

set -e

echo "🔍 VALIDAÇÃO COMPLETA DA INFRAESTRUTURA POD99"
echo ""

# Cores
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Função para imprimir resultado
check() {
  if [ $1 -eq 0 ]; then
    echo -e "${GREEN}✅ $2${NC}"
  else
    echo -e "${RED}❌ $2${NC}"
    exit 1
  fi
}

# Função para testar endpoint
test_endpoint() {
  local url=$1
  local expected=$2
  local description=$3
  
  echo -n "   Testando $description... "
  response=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")
  
  if [ "$response" = "$expected" ] || [ "$response" = "200" ] || [ "$response" = "301" ]; then
    echo -e "${GREEN}✅ (HTTP $response)${NC}"
  else
    echo -e "${RED}❌ (HTTP $response, esperado $expected)${NC}"
    return 1
  fi
}

echo "════════════════════════════════════════════════════════════"
echo "1️⃣ DOCKER CONTAINERS"
echo "════════════════════════════════════════════════════════════"
echo ""

# DynamoDB
echo -n "DynamoDB Local... "
if docker ps | grep -q "dynamodb-local"; then
  echo -e "${GREEN}✅ RODANDO${NC}"
  test_endpoint "http://localhost:8000/" "200" "DynamoDB Local" || true
else
  echo -e "${RED}❌ NÃO ESTÁ RODANDO${NC}"
fi
echo ""

# LocalStack
echo -n "LocalStack... "
if docker ps | grep -q "localstack"; then
  echo -e "${GREEN}✅ RODANDO${NC}"
  test_endpoint "http://localhost:4566/_localstack/health" "200" "LocalStack Health" || true
else
  echo -e "${RED}❌ NÃO ESTÁ RODANDO${NC}"
fi
echo ""

# API Gateway Simulator
echo -n "API Gateway Simulator... "
if docker ps | grep -q "api-gateway-simulator"; then
  echo -e "${GREEN}✅ RODANDO${NC}"
  test_endpoint "http://localhost:8081/" "200" "API Gateway Simulator" || true
else
  echo -e "${YELLOW}⚠️  NÃO ESTÁ RODANDO (será iniciado ao chamar start-local.sh)${NC}"
fi
echo ""

echo "════════════════════════════════════════════════════════════"
echo "2️⃣ CONFIGURAÇÃO AWS SDK"
echo "════════════════════════════════════════════════════════════"
echo ""

# Verificar variáveis de ambiente
echo "Variáveis de ambiente:"
echo -n "   AWS_ACCESS_KEY_ID... "
if [ -z "$AWS_ACCESS_KEY_ID" ]; then
  echo -e "${YELLOW}⚠️  (não definido, OK para local)${NC}"
else
  echo -e "${GREEN}✅ $AWS_ACCESS_KEY_ID${NC}"
fi

echo -n "   AWS_SECRET_ACCESS_KEY... "
if [ -z "$AWS_SECRET_ACCESS_KEY" ]; then
  echo -e "${YELLOW}⚠️  (não definido, OK para local)${NC}"
else
  echo -e "${GREEN}✅ ****${NC}"
fi
echo ""

echo "════════════════════════════════════════════════════════════"
echo "3️⃣ ESTRUTURA DE MÓDULOS MAVEN"
echo "════════════════════════════════════════════════════════════"
echo ""

# Verificar se pom.xml pai existe
echo -n "pom.xml (parent)... "
if [ -f "pom.xml" ]; then
  if grep -q "<modules>" pom.xml; then
    echo -e "${GREEN}✅ ENCONTRADO (multi-module)${NC}"
  else
    echo -e "${RED}❌ NÃO É MULTI-MODULE${NC}"
  fi
else
  echo -e "${RED}❌ NÃO ENCONTRADO${NC}"
fi
echo ""

# Verificar módulos
for module in "common-lib" "authorization-service" "limits-service" "accounting-service"; do
  echo -n "$module... "
  if [ -d "$module" ] && [ -f "$module/pom.xml" ]; then
    echo -e "${GREEN}✅ ENCONTRADO${NC}"
  else
    echo -e "${RED}❌ NÃO ENCONTRADO${NC}"
  fi
done
echo ""

echo "════════════════════════════════════════════════════════════"
echo "4️⃣ PORTAS CONFIGURADAS"
echo "════════════════════════════════════════════════════════════"
echo ""

echo "Verificando application-local.yml:"
for service in "authorization-service" "limits-service" "accounting-service"; do
  port=$(grep "port:" "$service/src/main/resources/application-local.yml" | head -1 | awk '{print $NF}')
  echo -e "   $service: ${GREEN}porta $port${NC}"
done
echo ""

echo "════════════════════════════════════════════════════════════"
echo "5️⃣ SCRIPTS DE SETUP"
echo "════════════════════════════════════════════════════════════"
echo ""

scripts=("start-local.sh" "setup-eventbridge-sqs.sh" "setup-docker-network.sh" "validate-eventbridge-sqs.sh")
for script in "${scripts[@]}"; do
  echo -n "$script... "
  if [ -f "scripts/$script" ] && [ -x "scripts/$script" ]; then
    echo -e "${GREEN}✅ EXECUTÁVEL${NC}"
  else
    echo -e "${RED}❌ NÃO ENCONTRADO OU NÃO EXECUTÁVEL${NC}"
  fi
done
echo ""

echo "════════════════════════════════════════════════════════════"
echo "6️⃣ VALIDAÇÃO DE COMPILAÇÃO"
echo "════════════════════════════════════════════════════════════"
echo ""

echo "Testando compilação (Maven)..."
if command -v mvn &> /dev/null; then
  echo -n "   mvn -pl common-lib compile... "
  if mvn -pl common-lib compile -q 2>/dev/null; then
    echo -e "${GREEN}✅ OK${NC}"
  else
    echo -e "${RED}❌ FALHA${NC}"
  fi
else
  echo -e "${YELLOW}⚠️  Maven não encontrado no PATH${NC}"
fi
echo ""

echo "════════════════════════════════════════════════════════════"
echo "✅ VALIDAÇÃO COMPLETA!"
echo "════════════════════════════════════════════════════════════"
echo ""
echo "📋 PRÓXIMOS PASSOS:"
echo ""
echo "1. Executar infraestrutura:"
echo "   ./scripts/start-local.sh"
echo ""
echo "2. Aguardar ~30 segundos (LocalStack + Terraform + EventBridge setup)"
echo ""
echo "3. Em 3 terminais diferentes, rodar:"
echo "   Terminal 1: mvn -pl authorization-service spring-boot:run"
echo "   Terminal 2: mvn -pl limits-service spring-boot:run"
echo "   Terminal 3: mvn -pl accounting-service spring-boot:run"
echo ""
echo "4. Aguardar todos subirem (logs com ✅)"
echo ""
echo "5. Testar em Terminal 4 (depois de amanhã):"
echo "   curl -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes ..."
echo ""
