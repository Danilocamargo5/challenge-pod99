package com.pod99.limits.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Limit {
    private String idContrato;
    private String idConta;  // ← NOVO! Para validar relação
    private BigDecimal limite;
    private BigDecimal disponivel;
    private BigDecimal reservado;
    private Long version;
    
    public void reserve(BigDecimal valor) {
        if (disponivel.compareTo(valor) < 0) {
            throw new IllegalArgumentException("Limite insuficiente");
        }
        this.disponivel = disponivel.subtract(valor);
        this.reservado = reservado.add(valor);
    }
    
    public void release(BigDecimal valor) {
        this.disponivel = disponivel.add(valor);
        this.reservado = reservado.subtract(valor);
    }
}
