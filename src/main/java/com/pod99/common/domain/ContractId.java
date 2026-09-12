package com.pod99.common.domain;

import lombok.Getter;

/**
 * Value Object para Contract ID
 * 
 * Validações:
 * - Formato: CONTA-XXX (CONTA-001, CONTA-002, ..., CONTA-300)
 * - Não pode estar vazio
 * - Imutável
 */
@Getter
public class ContractId {
    private final String value;
    
    public ContractId(String value) {
        validate(value);
        this.value = value;
    }
    
    private void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Contract ID não pode ser vazio");
        }
        
        if (!value.matches("CONTA-\\d{3}")) {
            throw new IllegalArgumentException(
                "Contract ID deve estar no formato CONTA-XXX (exemplo: CONTA-001, CONTA-002, ...)"
            );
        }
        
        // Validar range (CONTA-001 até CONTA-300)
        // 100 contas × 3 contratos por conta = 300 contratos
        int contractNumber = Integer.parseInt(value.substring(5));
        if (contractNumber < 1 || contractNumber > 999) {
            throw new IllegalArgumentException(
                "Contract ID inválido: número deve estar entre 001 e 999"
            );
        }
    }
    
    @Override
    public String toString() {
        return value;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContractId that = (ContractId) o;
        return value.equals(that.value);
    }
    
    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
