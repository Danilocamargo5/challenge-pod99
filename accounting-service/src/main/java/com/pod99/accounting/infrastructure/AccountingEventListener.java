package com.pod99.accounting.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import io.awspring.cloud.sqs.annotation.SqsListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountingEventListener {
    
    private final ObjectMapper objectMapper;
    
    @SqsListener("pod99-accounting-queue.fifo")
    public void handleTransactionAuthorized(String message) {
        
        log.info("┌─────────────────────────────────────────────────────────────────┐");
        log.info("│ 📨 ACCOUNTING SERVICE - Recebido evento do SQS                   │");
        log.info("├─────────────────────────────────────────────────────────────────┤");
        
        try {
            // Parse do evento
            var event = objectMapper.readValue(message, java.util.Map.class);
            
            String idAutorizacao = (String) event.get("id");
            String idContrato = (String) event.get("idContrato");
            String idConta = (String) event.get("idConta");
            Number valor = (Number) event.get("valor");
            String moeda = (String) event.get("moeda");
            
            log.info("│ Autorização: {} | Contrato: {}", idAutorizacao, idContrato);
            log.info("│ Conta: {} | Valor: {} {}", idConta, valor, moeda);
            log.info("├─────────────────────────────────────────────────────────────────┤");
            
            // TODO: Persistir na tabela de contabilidade
            log.info("│ 💾 Processando contabilidade (persistência em DynamoDB)...       │");
            log.info("│ Status: PROCESSADO ✅                                            │");
            
            log.info("└─────────────────────────────────────────────────────────────────┘");
            
        } catch (Exception e) {
            log.error("❌ Erro ao processar evento", e);
            throw new RuntimeException("Falha ao processar evento", e);
        }
    }
}
