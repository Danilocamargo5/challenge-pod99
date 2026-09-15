#!/usr/bin/env bash

set -e

cd "$(dirname "$0")/.."

echo "🚀 Iniciando ambiente local POD99..."

echo "🐳 Subindo LocalStack e DynamoDB..."
docker compose up -d localstack dynamodb-local

echo "🔧 Configurando rede Docker..."
./scripts/setup-docker-network.sh

echo "🏗️ Aplicando infraestrutura Terraform..."
(
  cd infra/terraform
  terraform apply -auto-approve
)

echo "🔌 Configurando EventBridge → SQS Target..."
./scripts/setup-eventbridge-sqs.sh

echo "🌐 Iniciando API Gateway Simulator..."
docker compose up -d api-gateway-simulator

echo ""
echo "============================================================"
echo "✅ Ambiente local POD99 iniciado com sucesso!"
echo "============================================================"
echo ""
echo "🚀 Para iniciar os 3 Microsserviços Spring Boot:"
echo ""
echo "   Abra 3 terminais diferentes e execute:"
echo ""
echo "   Terminal 1 (Authorization Service - porta 8080):"
echo "   mvn -pl authorization-service spring-boot:run"
echo ""
echo "   Terminal 2 (Limits Service - porta 8082):"
echo "   mvn -pl limits-service spring-boot:run"
echo ""
echo "   Terminal 3 (Accounting Service - SQS Listener):"
echo "   mvn -pl accounting-service spring-boot:run"
echo ""
echo "   Terminal 4 (Testar - depois que os 3 subirem):"
echo "   curl -X POST http://localhost:8081/v1/contratos/CONTA-001/autorizacoes ..."
echo ""
echo "📍 API Gateway Simulator: http://localhost:8081"
echo "📍 LocalStack:            http://localhost:4566"
echo "📍 DynamoDB Local:        http://localhost:8000"
echo ""
echo "🔗 Microsserviços:"
echo "   - authorization-service: http://localhost:8080"
echo "   - limits-service:        http://localhost:8082"
echo "   - accounting-service:    (listener SQS, sem HTTP)"
echo ""
echo "============================================================"
echo ""
echo "🚀 Subindo serviços Java (background)..."

# Subir em background em tmux ou nohup
nohup mvn -pl authorization-service spring-boot:run > /tmp/authorization.log 2>&1 &
sleep 2
nohup mvn -pl limits-service spring-boot:run > /tmp/limits.log 2>&1 &
sleep 2
nohup mvn -pl accounting-service spring-boot:run > /tmp/accounting.log 2>&1 &

echo "✅ Serviços Java iniciados em background"
echo ""
echo "Logs disponíveis em:"
echo "  - Authorization: tail -f /tmp/authorization.log"
echo "  - Limits: tail -f /tmp/limits.log"
echo "  - Accounting: tail -f /tmp/accounting.log"
