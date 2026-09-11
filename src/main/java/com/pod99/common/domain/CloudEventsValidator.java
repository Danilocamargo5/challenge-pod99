package com.pod99.common.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * CloudEvents validator seguindo especificação CloudEvents 1.0
 * https://cloudevents.io/
 * 
 * Validações:
 * - Campos obrigatórios presentes
 * - Tipos de dados corretos
 * - Formato ISO 8601 para timestamp
 * - UUID válido para event_id e correlation_id
 * - event_version segue semver
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloudEventsValidator {
    
    private final ObjectMapper objectMapper;
    
    private static final Set<String> REQUIRED_FIELDS = Set.of(
        "event_id",
        "event_type",
        "event_version",
        "occurred_at",
        "correlation_id"
    );
    
    private static final Set<String> OPTIONAL_FIELDS = Set.of(
        "trace_id",
        "id_autorizacao",
        "id_contrato",
        "valor",
        "saldo_reservado"
    );
    
    /**
     * Valida se um evento segue CloudEvents spec
     * 
     * @param eventJson JSON do evento
     * @throws IllegalArgumentException se validação falha
     */
    public void validate(String eventJson) {
        try {
            JsonNode event = objectMapper.readTree(eventJson);
            
            // 1. Validar campos obrigatórios
            validateRequiredFields(event);
            
            // 2. Validar tipos de dados
            validateDataTypes(event);
            
            // 3. Validar formatos específicos
            validateFormats(event);
            
            // 4. Validar semântica
            validateSemantics(event);
            
            log.info("✅ CloudEvents validado: event_id={}, type={}", 
                event.get("event_id").asText(), 
                event.get("event_type").asText());
            
        } catch (Exception e) {
            log.error("❌ Validação CloudEvents falhou: {}", e.getMessage());
            throw new IllegalArgumentException("CloudEvents validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Valida presença de campos obrigatórios
     */
    private void validateRequiredFields(JsonNode event) {
        for (String field : REQUIRED_FIELDS) {
            if (!event.has(field) || event.get(field).isNull()) {
                throw new IllegalArgumentException("Campo obrigatório ausente: " + field);
            }
        }
    }
    
    /**
     * Valida tipos de dados
     */
    private void validateDataTypes(JsonNode event) {
        // event_id: string (UUID)
        if (!event.get("event_id").isTextual()) {
            throw new IllegalArgumentException("event_id deve ser string");
        }
        
        // event_type: string
        if (!event.get("event_type").isTextual()) {
            throw new IllegalArgumentException("event_type deve ser string");
        }
        
        // event_version: string
        if (!event.get("event_version").isTextual()) {
            throw new IllegalArgumentException("event_version deve ser string");
        }
        
        // occurred_at: string (ISO 8601)
        if (!event.get("occurred_at").isTextual()) {
            throw new IllegalArgumentException("occurred_at deve ser string (ISO 8601)");
        }
        
        // correlation_id: string (UUID)
        if (!event.get("correlation_id").isTextual()) {
            throw new IllegalArgumentException("correlation_id deve ser string");
        }
        
        // Campos opcionais: validar se presentes
        if (event.has("valor") && !event.get("valor").isNumber()) {
            throw new IllegalArgumentException("valor deve ser número");
        }
        
        if (event.has("saldo_reservado") && !event.get("saldo_reservado").isNumber()) {
            throw new IllegalArgumentException("saldo_reservado deve ser número");
        }
    }
    
    /**
     * Valida formatos específicos (UUID, ISO 8601, semver)
     */
    private void validateFormats(JsonNode event) {
        // event_id: UUID válido
        String eventId = event.get("event_id").asText();
        if (!isValidUUID(eventId)) {
            throw new IllegalArgumentException("event_id deve ser UUID válido: " + eventId);
        }
        
        // correlation_id: UUID válido (ou vazio)
        String correlationId = event.get("correlation_id").asText();
        if (!correlationId.isEmpty() && !isValidUUID(correlationId)) {
            throw new IllegalArgumentException("correlation_id deve ser UUID válido: " + correlationId);
        }
        
        // trace_id: UUID válido (se presente)
        if (event.has("trace_id") && !event.get("trace_id").isNull()) {
            String traceId = event.get("trace_id").asText();
            if (!isValidUUID(traceId)) {
                throw new IllegalArgumentException("trace_id deve ser UUID válido: " + traceId);
            }
        }
        
        // occurred_at: ISO 8601 válido
        String occurredAt = event.get("occurred_at").asText();
        if (!isValidISO8601(occurredAt)) {
            throw new IllegalArgumentException("occurred_at deve ser ISO 8601: " + occurredAt);
        }
        
        // event_version: semver válido (ex: 1.0, 1.0.1)
        String eventVersion = event.get("event_version").asText();
        if (!isValidSemver(eventVersion)) {
            throw new IllegalArgumentException("event_version deve ser semver (ex: 1.0): " + eventVersion);
        }
    }
    
    /**
     * Validações de negócio/semântica
     */
    private void validateSemantics(JsonNode event) {
        // event_type não pode estar vazio
        String eventType = event.get("event_type").asText();
        if (eventType.isEmpty()) {
            throw new IllegalArgumentException("event_type não pode estar vazio");
        }
        
        // Valores monetários devem ser positivos
        if (event.has("valor") && event.get("valor").decimalValue().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("valor deve ser > 0");
        }
        
        // saldo_reservado não pode ser negativo
        if (event.has("saldo_reservado") && event.get("saldo_reservado").decimalValue().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("saldo_reservado não pode ser negativo");
        }
    }
    
    /**
     * Valida UUID format
     */
    private boolean isValidUUID(String uuid) {
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Valida ISO 8601 timestamp
     */
    private boolean isValidISO8601(String timestamp) {
        try {
            Instant.parse(timestamp);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Valida Semantic Versioning
     */
    private boolean isValidSemver(String version) {
        return version.matches("^(\\d+)\\.(\\d+)(\\.(\\d+))?(-[a-zA-Z0-9]+)?$");
    }
}
