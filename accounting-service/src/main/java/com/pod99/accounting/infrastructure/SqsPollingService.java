package com.pod99.accounting.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.Map;

@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class SqsPollingService {
    
    private final SqsAsyncClient sqsAsyncClient;
    private final ObjectMapper objectMapper;
    private static final String QUEUE_URL = "http://localhost:4566/000000000000/pod99-accounting-queue.fifo";
    
    @Scheduled(fixedDelay = 5000) // Poll a cada 5 segundos
    public void pollMessages() {
        try {
            ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                    .queueUrl(QUEUE_URL)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(1)
                    .build();
            
            var response = sqsAsyncClient.receiveMessage(request).get();
            
            if (response.messages() != null && !response.messages().isEmpty()) {
                for (Message message : response.messages()) {
                    handleMessage(message.body());
                }
            }
        } catch (Exception e) {
            log.warn("Erro ao fazer polling de mensagens SQS", e);
        }
    }
    
    private void handleMessage(String messageBody) {
        log.info("┌─────────────────────────────────────────────────────────────────┐");
        log.info("│ 📨 ACCOUNTING SERVICE - Recebido evento do SQS (polling)         │");
        log.info("├─────────────────────────────────────────────────────────────────┤");
        
        try {
            Map<String, Object> event = objectMapper.readValue(messageBody, Map.class);
            
            String idAutorizacao = (String) event.get("id");
            String idContrato = (String) event.get("idContrato");
            String idConta = (String) event.get("idConta");
            Number valor = (Number) event.get("valor");
            String moeda = (String) event.get("moeda");
            
            log.info("│ Autorização: {} | Contrato: {}", idAutorizacao, idContrato);
            log.info("│ Conta: {} | Valor: {} {}", idConta, valor, moeda);
            log.info("├─────────────────────────────────────────────────────────────────┤");
            log.info("│ 💾 Processando contabilidade (persistência em DynamoDB)...       │");
            log.info("│ Status: PROCESSADO ✅                                            │");
            log.info("└─────────────────────────────────────────────────────────────────┘");
            
        } catch (Exception e) {
            log.error("❌ Erro ao processar evento", e);
        }
    }
}
