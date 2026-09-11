package com.pod99.authorization.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.pod99.authorization.domain.Authorization;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizeTransactionResponse {
    @JsonProperty("id_autorizacao")
    private String idAutorizacao;
    
    @JsonProperty("saldo_reservado")
    private BigDecimal saldoReservado;
    
    @JsonProperty("correlation_id")
    private String correlationId;
    
    @JsonProperty("status")
    private String status;
    
    public static AuthorizeTransactionResponse from(Authorization auth) {
        return AuthorizeTransactionResponse.builder()
            .idAutorizacao(auth.getIdAutorizacao())
            .saldoReservado(auth.getSaldoReservado())
            .correlationId(auth.getCorrelationId())
            .status(auth.getStatus().toString())
            .build();
    }
}
