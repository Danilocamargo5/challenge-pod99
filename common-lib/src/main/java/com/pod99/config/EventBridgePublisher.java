package com.pod99.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pod99.common.domain.CloudEventsValidator;
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
    private final CloudEventsValidator cloudEventsValidator;
    
    /**
     * Publica um evento no EventBridge com validação CloudEvents
     * 
     * @param event Evento de domínio (será serializado como CloudEvents)
     * @throws IllegalArgumentException se evento não passa validação
     */
    public void publish(DomainEvent event) {
        try {
            String detail = objectMapper.writeValueAsString(event);
            
            // 1. Validar CloudEvents schema
            log.debug("🔍 Validando CloudEvents schema...");
            cloudEventsValidator.validate(detail);
            
            // 2. Publicar em EventBridge
            PutEventsRequest request = PutEventsRequest.builder()
                .entries(PutEventsRequestEntry.builder()
                    .source("pod99.authorization")
                    .detailType(event.getEventType())
                    .detail(detail)
                    .build())
                .build();
            
            var response = eventBridgeClient.putEvents(request);
            
            // 3. Verificar se houve erros
            if (response.failedEntryCount() > 0) {
                log.error("❌ EventBridge retornou erro: {} entradas falharam", response.failedEntryCount());
                throw new RuntimeException("EventBridge publication failed");
            }
            
            log.info("📡 Evento publicado (CloudEvents válido): type={}, id={}, version={}", 
                event.getEventType(), 
                event.getEventId(),
                event.getEventVersion());
            
        } catch (IllegalArgumentException e) {
            log.error("❌ Validação CloudEvents falhou: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("❌ Erro ao publicar evento", e);
            throw new RuntimeException("Falha ao publicar evento", e);
        }
    }
}
