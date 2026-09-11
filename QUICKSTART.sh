#!/bin/bash
# QUICKSTART.sh — Execute este script para subir tudo em 30 segundos

set -e

echo "🚀 POD99 Quick Start"
echo "===================="

# Verifica se Docker está instalado
if ! command -v docker &> /dev/null; then
    echo "❌ Docker não encontrado. Instale em https://www.docker.com"
    exit 1
fi

echo "✅ Docker encontrado"
echo ""

# Sobe infraestrutura
echo "📦 Subindo Docker Compose..."
docker-compose up -d

# Aguarda pod99-app ficar pronta
echo "⏳ Aguardando aplicação iniciar..."
for i in {1..30}; do
    if curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
        echo "✅ Aplicação pronta!"
        break
    fi
    echo "   Tentativa $i/30..."
    sleep 2
done

echo ""
echo "🎉 POD99 está rodando!"
echo ""
echo "API endpoint: http://localhost:8080/v1/contratos/{id}/autorizacoes"
echo "Health check: http://localhost:8080/actuator/health"
echo ""
echo "📝 Teste com:"
echo ""
echo 'curl -X POST http://localhost:8080/v1/contratos/CONTA-001/autorizacoes \'
echo '  -H "Idempotency-Key: $(uuidgen)" \'
echo '  -H "Content-Type: application/json" \'
echo "  -d '{\"id_conta\":\"ACC-001\",\"valor\":100.00,\"moeda\":\"BRL\",\"tipo_operacao\":\"DEBITO\"}'"
echo ""
echo "📊 Ver logs:"
echo "docker-compose logs -f pod99-app"
echo ""
echo "⛔ Parar:"
echo "docker-compose down"
