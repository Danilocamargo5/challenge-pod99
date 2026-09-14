package com.pod99.authorization.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Authorization {
    private String idAutorizacao;
    private String idempotencyKey;
    private String idContrato;
    private String idConta;
    private BigDecimal valor;
    private String moeda;
    private String tipoOperacao;
    private String idEstabelecimento;  // Novo campo (ID do estabelecimento)
    private Map<String, Object> metadata;  // Novo campo (dados adicionais)
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
            String idEstabelecimento,
            Map<String, Object> metadata,
            BigDecimal saldoReservado,
            String correlationId,
            String idempotencyKey) {
        
        return Authorization.builder()
            .idAutorizacao(java.util.UUID.randomUUID().toString())
            .idempotencyKey(idempotencyKey)
            .idContrato(idContrato)
            .idConta(idConta)
            .valor(valor)
            .moeda(moeda)
            .tipoOperacao(tipoOperacao)
            .idEstabelecimento(idEstabelecimento)
            .metadata(metadata)
            .saldoReservado(saldoReservado)
            .status(AuthorizationStatus.APPROVED)
            .correlationId(correlationId)
            .criadoEm(LocalDateTime.now())
            .build();
    }
}
