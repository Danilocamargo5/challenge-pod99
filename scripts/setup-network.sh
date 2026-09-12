#!/bin/bash

# ==================================================================================
# Setup de Rede para POD99
# ==================================================================================
#
# Este script configura a rede Docker para permitir comunicação entre
# containers e o host, especialmente para LocalStack → Spring Boot
#
# Uso: ./scripts/setup-network.sh
#
# ==================================================================================

set -e

echo "🔧 Configurando rede Docker para POD99..."

# ==================================================================================
# 1. Obter interface de bridge do Docker
# ==================================================================================

echo ""
echo "📍 Procurando interface de bridge do Docker..."

# Procurar por interfaces br-* que estão em uso
BRIDGE_INTERFACE=$(ip link show | grep "br-" | grep -v "docker0" | head -1 | awk '{print $2}' | sed 's/://g')

if [ -z "$BRIDGE_INTERFACE" ]; then
  echo "⚠️  Nenhuma interface de bridge encontrada."
  echo ""
  echo "Opções:"
  echo "1. Iniciar docker-compose primeiro:"
  echo "   docker-compose up -d"
  echo ""
  echo "2. Depois executar este script novamente:"
  echo "   ./scripts/setup-network.sh"
  echo ""
  exit 1
fi

echo "✅ Interface encontrada: $BRIDGE_INTERFACE"

# ==================================================================================
# 2. Verificar se a regra já existe
# ==================================================================================

echo ""
echo "🔍 Verificando regras de firewall existentes..."

# Verificar se já existe uma regra similar
if sudo iptables-legacy -L FORWARD -n | grep -q "$BRIDGE_INTERFACE"; then
  echo "✅ Regra de firewall já está configurada!"
  echo ""
  exit 0
fi

# ==================================================================================
# 3. Aplicar regra de firewall
# ==================================================================================

echo ""
echo "🚀 Aplicando regra de firewall..."
echo "   Comando: sudo iptables-legacy -I FORWARD 1 -i $BRIDGE_INTERFACE -o $BRIDGE_INTERFACE -j ACCEPT"

if sudo iptables-legacy -I FORWARD 1 -i "$BRIDGE_INTERFACE" -o "$BRIDGE_INTERFACE" -j ACCEPT; then
  echo "✅ Regra aplicada com sucesso!"
else
  echo "❌ Erro ao aplicar regra!"
  exit 1
fi

# ==================================================================================
# 4. Persistir a regra (opcional, se iptables-persistent está instalado)
# ==================================================================================

echo ""
echo "💾 Tentando persistir regra (opcional)..."

if command -v iptables-save &> /dev/null; then
  if sudo iptables-save 2>/dev/null; then
    echo "✅ Regra persistida!"
  else
    echo "⚠️  Não foi possível persistir (pode precisar iptables-persistent)"
  fi
else
  echo "⚠️  iptables-persistent não instalado (regra será perdida ao reiniciar)"
  echo "   Para instalar: sudo apt-get install iptables-persistent"
fi

# ==================================================================================
# 5. Verificar a regra
# ==================================================================================

echo ""
echo "✅ Regras de firewall ativas:"
sudo iptables-legacy -L FORWARD -n | grep -E "ACCEPT.*$BRIDGE_INTERFACE" || echo "   Nenhuma regra encontrada"

echo ""
echo "🎉 Configuração de rede concluída!"
echo ""
echo "Próximos passos:"
echo "1. docker-compose up -d"
echo "2. cd infra/terraform && terraform apply -var-file=local.tfvars"
echo "3. mvn clean compile spring-boot:run"
echo ""
