package com.pod99.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pod99.common.domain.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventBridgePublisher {
    
    private final EventBridgeClient eventBridgeClient;
    private final ObjectMapper objectMapper;
    
    public void publishEvent(DomainEvent event) {
        try {
            String detail = objectMapper.writeValueAsString(event);
            
            PutEventsRequest request = PutEventsRequest.builder()
                .entries(PutEventsRequestEntry.builder()
                    .source("pod99.authorization")
                    .detailType(event.getEventType())
                    .detail(detail)
                    .build())
                .build();
            
            var response = eventBridgeClient.putEvents(request);
            
            if (response.failedEntryCount() > 0) {
                log.error("❌ EventBridge retornou erro: {} entradas falharam", response.failedEntryCount());
                throw new RuntimeException("EventBridge publication failed");
            }
            
            log.info("✅ Evento publicado no EventBridge: {}", event.getEventType());
            
        } catch (Exception e) {
            log.error("❌ Erro ao publicar evento no EventBridge", e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}
