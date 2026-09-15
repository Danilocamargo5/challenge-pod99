package com.pod99.accounting.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pod99.accounting.application.RecordTransactionUseCase;
import com.pod99.accounting.domain.AccountingEntry;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

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
            
            // Parse genérico (sem depender de authorization-service)
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(message);
            com.fasterxml.jackson.databind.JsonNode detailNode = root.has("detail") ? root.get("detail") : root;
            
            // Extrair campos genéricos
            String idAutorizacao = detailNode.has("idAutorizacao") ? detailNode.get("idAutorizacao").asText() : "UNKNOWN";
            String idContrato = detailNode.has("idContrato") ? detailNode.get("idContrato").asText() : "UNKNOWN";
            double valor = detailNode.has("valor") ? detailNode.get("valor").asDouble() : 0.0;
            String eventId = detailNode.has("eventId") ? detailNode.get("eventId").asText() : "UNKNOWN";
            
            log.info("📩 Evento decodificado com sucesso");
            log.info("   ID Autorização: {} | Event ID: {}", idAutorizacao, eventId);
            log.info("   Valor: {} | Contrato: {}", valor, idContrato);
            
            AccountingEntry entry = AccountingEntry.builder()
                .eventId(eventId)
                .idAutorizacao(idAutorizacao)
                .idContrato(idContrato)
                .valor(BigDecimal.valueOf(valor))
                .tipoLancamento("DEBIT")
                .dataOperacao(LocalDateTime.now())
                .build();
            
            recordUseCase.record(entry);
            
            // 📤 LOG DE SAÍDA
            log.info("═══════════════════════════════════════════════════════════════");
            log.info("🟢 [ACCOUNTING] SAÍDA - Contabilização salva com sucesso");
            log.info("   ID Autorização: {} | Valor: {}", idAutorizacao, valor);
            log.info("   Tipo Lançamento: DEBIT | Contrato: {}", idContrato);
            log.info("   Status: RECORDED | Event ID: {}", eventId);
            log.info("═══════════════════════════════════════════════════════════════");
            
        } catch (Exception e) {
            log.error("❌ [ACCOUNTING] ERRO - Falha ao processar contabilização", e);
        }
    }
}
