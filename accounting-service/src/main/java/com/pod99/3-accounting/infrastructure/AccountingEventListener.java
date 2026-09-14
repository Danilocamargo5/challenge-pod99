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
    
    @SqsListener("pod99-accounting-queue.fifo")
    public void handleTransacaoAutorizada(String message) {
        try {
            // 📥 LOG DE ENTRADA
            log.info("═══════════════════════════════════════════════════════════════");
            log.info("🔵 [ACCOUNTING] ENTRADA - Recebido evento de SQS");
            log.info("   Tamanho da mensagem: {} bytes", message.length());
            log.info("═══════════════════════════════════════════════════════════════");
            
            // EventBridge envolve o evento em um wrapper com "detail"
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(message);
            String detailJson = root.has("detail") 
                ? objectMapper.writeValueAsString(root.get("detail"))
                : message;  // Se não tiver "detail", trata como direto
            
            TransacaoAutorizadaEvent event = objectMapper.readValue(detailJson, TransacaoAutorizadaEvent.class);
            
            log.info("📩 Evento decodificado com sucesso");
            log.info("   ID Autorização: {} | Event ID: {}", 
                event.getIdAutorizacao(), event.getEventId());
            log.info("   Valor: {} | Tipo Operação: {}", 
                event.getValor(), event.getTipoOperacao());
            
            AccountingEntry entry = AccountingEntry.builder()
                .eventId(event.getEventId())
                .idAutorizacao(event.getIdAutorizacao())
                .idContrato(event.getIdContrato())
                .valor(event.getValor())
                .tipoLancamento("DEBIT")
                .dataOperacao(event.getOccurredAt().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
                .build();
            
            recordUseCase.record(entry);
            
            // 📤 LOG DE SAÍDA
            log.info("═══════════════════════════════════════════════════════════════");
            log.info("🟢 [ACCOUNTING] SAÍDA - Contabilização salva com sucesso");
            log.info("   ID Autorização: {} | Valor: {}", 
                event.getIdAutorizacao(), event.getValor());
            log.info("   Tipo Lançamento: DEBIT | Contrato: {}", event.getIdContrato());
            log.info("   Status: RECORDED | Event ID: {}", event.getEventId());
            log.info("═══════════════════════════════════════════════════════════════");
            
        } catch (Exception e) {
            log.error("❌ [ACCOUNTING] ERRO - Falha ao processar contabilização", e);
        }
    }
}
