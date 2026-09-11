#!/bin/bash

# Script para rodar a aplicação POD99 localmente com Spring profile=local
# Usage: ./run-local.sh

echo "🚀 Iniciando POD99 com profile=local..."
echo ""
echo "Certifique-se de que docker-compose já está rodando em outro terminal:"
echo "  docker-compose up"
echo ""

mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
