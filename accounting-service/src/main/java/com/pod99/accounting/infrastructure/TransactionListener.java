package com.pod99.accounting.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import io.awspring.cloud.sqs.annotation.SqsListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionListener {
    
    private final ObjectMapper objectMapper;
    
    @SqsListener("pod99-transactions.fifo")
    public void handleTransactionAuthorized(String message) {
        
        log.info("📨 Recebido evento do SQS");
        log.info("   Mensagem: {}", message);
        
        try {
            // Parse do evento
            var event = objectMapper.readValue(message, java.util.Map.class);
            
            String idAutorizacao = (String) event.get("id");
            String idContrato = (String) event.get("idContrato");
            String idConta = (String) event.get("idConta");
            Number valor = (Number) event.get("valor");
            String moeda = (String) event.get("moeda");
            
            log.info("✅ Evento processado para contabilidade:");
            log.info("   Autorização: {}", idAutorizacao);
            log.info("   Contrato: {}", idContrato);
            log.info("   Conta: {}", idConta);
            log.info("   Valor: {} {}", valor, moeda);
            
            // TODO: Persistir na tabela de contabilidade
            log.info("💾 [MOCK] Persistindo em pod99-accounting (DynamoDB)");
            
        } catch (Exception e) {
            log.error("❌ Erro ao processar evento", e);
            throw new RuntimeException("Falha ao processar evento", e);
        }
    }
}
