package com.pod99.authorization.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.pod99.authorization.domain.Authorization;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthorizeTransactionResponse {
    @JsonProperty("id_autorizacao")
    private String idAutorizacao;
    
    @JsonProperty("saldo_reservado")
    private BigDecimal saldoReservado;
    
    @JsonProperty("correlation_id")
    private String correlationId;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    /**
     * Indica se é repetição (idempotência) - retorna 200
     * ou nova autorização - retorna 201
     * (não serializado no JSON)
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private boolean repetition;
    
    public static AuthorizeTransactionResponse from(Authorization auth) {
        return AuthorizeTransactionResponse.builder()
            .idAutorizacao(auth.getIdAutorizacao())
            .saldoReservado(auth.getSaldoReservado())
            .correlationId(auth.getCorrelationId())
            .status(auth.getStatus().toString())
            .timestamp(Instant.now())
            .repetition(false)  // Default: é nova
            .build();
    }
    
    public static AuthorizeTransactionResponse fromRepetition(Authorization auth) {
        return AuthorizeTransactionResponse.builder()
            .idAutorizacao(auth.getIdAutorizacao())
            .saldoReservado(auth.getSaldoReservado())
            .correlationId(auth.getCorrelationId())
            .status(auth.getStatus().toString())
            .timestamp(Instant.now())
            .repetition(true)  // É repetição (idempotência)
            .build();
    }
}
