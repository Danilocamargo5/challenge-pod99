package com.pod99.common.domain;

import lombok.Getter;

/**
 * Value Object para Account ID
 * 
 * Validações:
 * - Formato: ACC-XXX (ACC-001, ACC-002, ..., ACC-100)
 * - Não pode estar vazio
 * - Imutável
 */
@Getter
public class AccountId {
    private final String value;
    
    public AccountId(String value) {
        validate(value);
        this.value = value;
    }
    
    private void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Account ID não pode ser vazio");
        }
        
        if (!value.matches("ACC-\\d{3}")) {
            throw new IllegalArgumentException(
                "Account ID deve estar no formato ACC-XXX (exemplo: ACC-001, ACC-002, ...)"
            );
        }
        
        // Validar range (ACC-001 até ACC-100)
        int accountNumber = Integer.parseInt(value.substring(4));
        if (accountNumber < 1 || accountNumber > 999) {
            throw new IllegalArgumentException(
                "Account ID inválido: número deve estar entre 001 e 999"
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
        AccountId that = (AccountId) o;
        return value.equals(that.value);
    }
    
    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
