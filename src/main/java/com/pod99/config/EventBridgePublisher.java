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
    
    public void publish(DomainEvent event) {
        try {
            String detail = objectMapper.writeValueAsString(event);
            
            PutEventsRequest request = PutEventsRequest.builder()
                .entries(PutEventsRequestEntry.builder()
                    .source("pod99.authorization")
                    .detailType(event.getEventType())
                    .detail(detail)
                    .build())
                .build();
            
            eventBridgeClient.putEvents(request);
            log.info("📡 Evento publicado: type={}, id={}", event.getEventType(), event.getEventId());
            
        } catch (Exception e) {
            log.error("❌ Erro ao publicar evento", e);
            throw new RuntimeException("Falha ao publicar evento", e);
        }
    }
}
