package com.pod99.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.*;

import java.time.Instant;
import java.util.UUID;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventBridgePublisher {
    
    private final EventBridgeClient eventBridgeClient;
    private final ObjectMapper objectMapper;
    private final String EVENT_BUS = "default";
    
    public void publishTransactionAuthorized(
            String idAutorizacao,
            String idContrato,
            String idConta,
            Double valor,
            String moeda) {
        
        log.info("📤 Publicando evento TransacaoAutorizada no EventBridge");
        log.info("   ID Autorização: {}", idAutorizacao);
        log.info("   ID Contrato: {}", idContrato);
        log.info("   ID Conta: {}", idConta);
        log.info("   Valor: {} {}", valor, moeda);
        
        try {
            // Criar evento de domínio
            String eventDetail = objectMapper.writeValueAsString(Map.of(
                "id", idAutorizacao,
                "idContrato", idContrato,
                "idConta", idConta,
                "valor", valor,
                "moeda", moeda,
                "timestamp", Instant.now().toString()
            ));
            
            PutEventsRequest request = PutEventsRequest.builder()
                    .eventBusName(EVENT_BUS)
                    .entries(PutEventsRequestEntry.builder()
                            .time(Instant.now())
                            .source("pod99.authorization")
                            .detailType("TransacaoAutorizada")
                            .detail(eventDetail)
                            .build())
                    .build();
            
            PutEventsResponse response = eventBridgeClient.putEvents(request);
            
            if (response.failedEntryCount() == 0) {
                log.info("✅ Evento publicado com sucesso no EventBridge");
            } else {
                log.error("❌ Falha ao publicar evento: {}", response.failedEntryCount());
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao publicar evento no EventBridge", e);
            throw new RuntimeException("Falha ao publicar evento", e);
        }
    }
}
