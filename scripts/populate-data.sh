#!/bin/bash

set -e

echo "📊 Populando dados de teste: 100 contas × 3 contratos = 300 registros..."

# Aguardar LocalStack pronto
until awslocal dynamodb list-tables --region us-east-1 > /dev/null 2>&1; do
  echo "⏳ Aguardando LocalStack pronto..."
  sleep 2
done

echo "✅ LocalStack pronto!"

TOTAL=0

# Gerar 100 contas (ACC-001 até ACC-100)
for account_num in {1..100}; do
    account_id=$(printf "ACC-%03d" $account_num)
    
    # Cada conta tem 3 contratos
    for contract_num in {1..3}; do
        # Calcular número global do contrato
        # ACC-001: CONTA-001, CONTA-002, CONTA-003
        # ACC-002: CONTA-004, CONTA-005, CONTA-006
        # ...
        global_contract_num=$(( ($account_num - 1) * 3 + $contract_num ))
        contract_id=$(printf "CONTA-%03d" $global_contract_num)
        
        # Inserir limite com valor variado
        limite=$(printf "%.2f" $(( 50000 + ($account_num * 1000) )))
        
        awslocal dynamodb put-item \
          --table-name pod99-limits \
          --item "{
            \"id_contrato\": {\"S\": \"$contract_id\"},
            \"id_conta\": {\"S\": \"$account_id\"},
            \"limite\": {\"N\": \"$limite\"},
            \"disponivel\": {\"N\": \"$limite\"},
            \"reservado\": {\"N\": \"0.00\"},
            \"version\": {\"N\": \"0\"}
          }" \
          --region us-east-1 2>/dev/null || true
        
        TOTAL=$((TOTAL + 1))
        
        # Mostrar progresso a cada 30 registros
        if [ $((TOTAL % 30)) -eq 0 ]; then
            echo "  ✓ $TOTAL registros inseridos..."
        fi
    done
done

echo ""
echo "✅ População concluída!"
echo "📊 Total de limites inseridos: $TOTAL"
echo "   - Contas: 100 (ACC-001 até ACC-100)"
echo "   - Contratos: 300 (CONTA-001 até CONTA-300)"
echo "   - Contratos por conta: 3"
echo ""
echo "🔍 Exemplo de dados:"
awslocal dynamodb get-item \
  --table-name pod99-limits \
  --key '{"id_contrato": {"S": "CONTA-001"}}' \
  --region us-east-1 | grep -E "id_contrato|id_conta|limite|disponivel" | head -4

echo ""
echo "✨ Dados de teste populados com sucesso!"
