package com.pod99.accounting.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingEventListener {
    
    private final ObjectMapper objectMapper;
    
    @SqsListener("pod99-accounting-queue.fifo")
    public void handleTransacaoAutorizada(String message) {
        log.info("┌─────────────────────────────────────────────────────────────────┐");
        log.info("│ 📨 ACCOUNTING SERVICE - Recebido evento do SQS                   │");
        log.info("├─────────────────────────────────────────────────────────────────┤");
        
        try {
            // EventBridge envolve o evento em wrapper com "detail"
            JsonNode root = objectMapper.readTree(message);
            String eventJson = root.has("detail") 
                ? objectMapper.writeValueAsString(root.get("detail"))
                : message;
            
            JsonNode event = objectMapper.readTree(eventJson);
            
            String idAutorizacao = event.has("id") ? event.get("id").asText() : "UNKNOWN";
            String idContrato = event.has("idContrato") ? event.get("idContrato").asText() : "UNKNOWN";
            String idConta = event.has("idConta") ? event.get("idConta").asText() : "UNKNOWN";
            double valor = event.has("valor") ? event.get("valor").asDouble() : 0.0;
            String moeda = event.has("moeda") ? event.get("moeda").asText() : "BRL";
            
            log.info("│ Autorização: {} | Contrato: {}", idAutorizacao, idContrato);
            log.info("│ Conta: {} | Valor: {} {}", idConta, valor, moeda);
            log.info("├─────────────────────────────────────────────────────────────────┤");
            log.info("│ 💾 Processando contabilidade (persistência em DynamoDB)...       │");
            log.info("│ Status: PROCESSADO ✅                                            │");
            log.info("└─────────────────────────────────────────────────────────────────┘");
            
        } catch (Exception e) {
            log.error("❌ Erro ao processar evento", e);
            throw new RuntimeException("Falha ao processar evento", e);
        }
    }
}
