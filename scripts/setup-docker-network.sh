#!/usr/bin/env bash

set -e

NETWORK_NAME="pod99-network"

echo "🔎 Aguardando rede Docker: ${NETWORK_NAME}..."

for i in {1..30}; do
  NETWORK_ID=$(docker network inspect "${NETWORK_NAME}" --format '{{.Id}}' 2>/dev/null || true)

  if [ -n "${NETWORK_ID}" ]; then
    break
  fi

  sleep 1
done

if [ -z "${NETWORK_ID}" ]; then
  echo "❌ Rede Docker '${NETWORK_NAME}' não encontrada."
  exit 1
fi

BRIDGE_NAME="br-${NETWORK_ID:0:12}"

echo "🌐 Rede encontrada: ${NETWORK_NAME}"
echo "🌉 Bridge Docker: ${BRIDGE_NAME}"

if sudo iptables-legacy -C FORWARD \
  -i "${BRIDGE_NAME}" \
  -o "${BRIDGE_NAME}" \
  -j ACCEPT 2>/dev/null; then

  echo "✅ Regra de FORWARD já existe."
else
  echo "🔧 Adicionando regra de FORWARD..."

  sudo iptables-legacy -I FORWARD 1 \
    -i "${BRIDGE_NAME}" \
    -o "${BRIDGE_NAME}" \
    -j ACCEPT

  echo "✅ Regra adicionada."
fi

echo "✅ Rede Docker configurada."
