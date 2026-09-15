package com.pod99.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pod99.common.domain.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventBridgePublisher {
    
    private final ObjectMapper objectMapper;
    
    public void publishEvent(DomainEvent event) {
        try {
            String detail = objectMapper.writeValueAsString(event);
            log.info("📤 [EventBridge] Publicando evento: {}", event.getEventType());
            log.info("   Conteúdo: {}", detail);
            // TODO: Chamar eventBridgeClient.putEvents() quando aws-core estiver disponível
        } catch (Exception e) {
            log.error("❌ Erro ao publicar evento", e);
        }
    }
}
