package com.pod99.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pod99.common.domain.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * TODO: Integrar com EventBridge real
 * Por enquanto apenas logged
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventBridgePublisher {
    
    private final ObjectMapper objectMapper;
    
    public void publish(DomainEvent event) {
        try {
            String detail = objectMapper.writeValueAsString(event);
            log.info("📤 [TODO] Publicar evento no EventBridge: {}", detail);
            // TODO: eventBridgeClient.putEvents(...);
        } catch (Exception e) {
            log.error("❌ Erro ao publicar evento", e);
        }
    }
}
