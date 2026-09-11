package com.pod99.authorization.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Authorization {
    private String idAutorizacao;
    private String idContrato;
    private String idConta;
    private BigDecimal valor;
    private String moeda;
    private String tipoOperacao;
    private BigDecimal saldoReservado;
    private AuthorizationStatus status;
    private String correlationId;
    private LocalDateTime criadoEm;
    
    public static Authorization criar(
            String idContrato,
            String idConta,
            BigDecimal valor,
            String moeda,
            String tipoOperacao,
            BigDecimal saldoReservado,
            String correlationId) {
        
        return Authorization.builder()
            .idAutorizacao(java.util.UUID.randomUUID().toString())
            .idContrato(idContrato)
            .idConta(idConta)
            .valor(valor)
            .moeda(moeda)
            .tipoOperacao(tipoOperacao)
            .saldoReservado(saldoReservado)
            .status(AuthorizationStatus.APPROVED)
            .correlationId(correlationId)
            .criadoEm(LocalDateTime.now())
            .build();
    }
}
