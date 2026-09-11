package com.pod99.authorization.domain;

import com.pod99.common.domain.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class TransacaoAutorizadaEvent extends DomainEvent {
    private String idAutorizacao;
    private String idContrato;
    private BigDecimal valor;
    private BigDecimal saldoReservado;
    
    public static TransacaoAutorizadaEvent from(Authorization auth, String traceId) {
        TransacaoAutorizadaEvent event = new TransacaoAutorizadaEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType("TransacaoAutorizada");
        event.setEventVersion("1.0");
        event.setOccurredAt(LocalDateTime.now(ZoneId.of("UTC")));
        event.setCorrelationId(auth.getCorrelationId());
        event.setTraceId(traceId);
        event.setIdAutorizacao(auth.getIdAutorizacao());
        event.setIdContrato(auth.getIdContrato());
        event.setValor(auth.getValor());
        event.setSaldoReservado(auth.getSaldoReservado());
        return event;
    }
}
