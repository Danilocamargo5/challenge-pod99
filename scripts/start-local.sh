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

echo "🌐 Iniciando API Gateway Simulator..."
docker compose up -d api-gateway-simulator

echo ""
echo "============================================================"
echo "✅ Ambiente local POD99 iniciado com sucesso!"
echo "============================================================"
echo ""
echo "🚀 Para iniciar a aplicação Spring Boot:"
echo ""
echo "   Abra outro terminal e execute:"
echo ""
echo "   mvn spring-boot:run"
echo ""
echo "📍 Spring Boot:           http://localhost:8080"
echo "📍 API Gateway Simulator: http://localhost:8081"
echo "📍 LocalStack:            http://localhost:4566"
echo "📍 DynamoDB Local:        http://localhost:8000"
echo ""
echo "============================================================"