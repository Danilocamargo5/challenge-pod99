package com.pod99.accounting.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pod99.accounting.application.RecordTransactionUseCase;
import com.pod99.accounting.domain.AccountingEntry;
import com.pod99.authorization.domain.TransacaoAutorizadaEvent;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingEventListener {
    
    private final RecordTransactionUseCase recordUseCase;
    private final ObjectMapper objectMapper;
    
    @SqsListener("pod99-accounting-queue")
    public void handleTransacaoAutorizada(String message) {
        try {
            // log.info("📨 Recebido evento SQS");
            
            TransacaoAutorizadaEvent event = objectMapper.readValue(message, TransacaoAutorizadaEvent.class);
            
            // log.info("💰 Processando transação: id={}, valor={}", event.getIdAutorizacao(), event.getValor());
            
            AccountingEntry entry = AccountingEntry.builder()
                .eventId(event.getEventId())
                .idAutorizacao(event.getIdAutorizacao())
                .idContrato(event.getIdContrato())
                .valor(event.getValor())
                .tipoLancamento("DEBIT")
                .dataOperacao(event.getOccurredAt())
                .build();
            
            recordUseCase.record(entry);
            
            log.info("✅ Evento processado com sucesso");
            
        } catch (Exception e) {
            log.error("❌ Erro ao processar evento", e);
        }
    }
}
