package com.pod99.authorization.domain;

import com.pod99.common.domain.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransacaoAutorizadaEvent extends DomainEvent {
    private String idAutorizacao;
    private String idContrato;
    private BigDecimal valor;
    private BigDecimal saldoReservado;
    
    public static TransacaoAutorizadaEvent from(Authorization auth, String traceId) {
        return TransacaoAutorizadaEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("TransacaoAutorizada")
            .eventVersion("1.0")
            .occurredAt(LocalDateTime.now(ZoneId.of("UTC")))
            .correlationId(auth.getCorrelationId())
            .traceId(traceId)
            .idAutorizacao(auth.getIdAutorizacao())
            .idContrato(auth.getIdContrato())
            .valor(auth.getValor())
            .saldoReservado(auth.getSaldoReservado())
            .build();
    }
}
