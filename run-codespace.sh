#!/bin/bash

# Script para rodar POD99 no Codespace
# Execute em 4 terminais diferentes

set -e

PROJECT_DIR="$HOME/challenge-pod99"
# ou no Codespace: PROJECT_DIR="/workspace/challenge-pod99"

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║           POD99 - QUICK START NO CODESPACE                    ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

if [ "$1" == "terminal1" ]; then
    echo "🐳 TERMINAL 1: LocalStack"
    echo "════════════════════════════════════════════════════════════"
    cd "$PROJECT_DIR"
    docker-compose up
    
elif [ "$1" == "terminal2" ]; then
    echo "🏗️  TERMINAL 2: Terraform"
    echo "════════════════════════════════════════════════════════════"
    sleep 30  # Aguardar LocalStack estar pronto
    cd "$PROJECT_DIR/infra/terraform"
    terraform init
    terraform apply -var-file=local.tfvars -auto-approve
    
elif [ "$1" == "terminal3" ]; then
    echo "☕ TERMINAL 3: Spring Boot"
    echo "════════════════════════════════════════════════════════════"
    sleep 60  # Aguardar Terraform estar pronto
    cd "$PROJECT_DIR"
    git pull origin develop
    ./mvnw spring-boot:run
    
elif [ "$1" == "terminal4" ]; then
    echo "🧪 TERMINAL 4: Testes"
    echo "════════════════════════════════════════════════════════════"
    sleep 90  # Aguardar app estar pronto
    cd "$PROJECT_DIR"
    
    echo ""
    echo "✅ Todos os terminais foram iniciados!"
    echo ""
    echo "📋 Testes disponíveis em: QUICK-TESTS.md"
    echo ""
    echo "Copie e cole um por um:"
    echo ""
    cat QUICK-TESTS.md | grep -A 20 "## 🧪"
    
else
    echo ""
    echo "❌ Falta informar qual terminal!"
    echo ""
    echo "USO:"
    echo "  $0 terminal1    # LocalStack (docker-compose up)"
    echo "  $0 terminal2    # Terraform"
    echo "  $0 terminal3    # Spring Boot"
    echo "  $0 terminal4    # Testes"
    echo ""
    echo "Abra 4 terminais e rode cada comando em um:"
    echo ""
    echo "  # Terminal 1"
    echo "  cd ~/challenge-pod99 && docker-compose up"
    echo ""
    echo "  # Terminal 2"
    echo "  sleep 30 && cd ~/challenge-pod99/infra/terraform && terraform init && terraform apply -var-file=local.tfvars"
    echo ""
    echo "  # Terminal 3"
    echo "  sleep 60 && cd ~/challenge-pod99 && ./mvnw spring-boot:run"
    echo ""
    echo "  # Terminal 4"
    echo "  sleep 90 && cd ~/challenge-pod99 && cat QUICK-TESTS.md"
    echo ""
fi
