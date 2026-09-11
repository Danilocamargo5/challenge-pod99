package com.pod99.common.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CloudEventsValidator Tests")
class CloudEventsValidatorTest {
    
    private CloudEventsValidator validator;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        validator = new CloudEventsValidator(objectMapper);
    }
    
    @Test
    @DisplayName("✅ Deve validar CloudEvent correto")
    void testValidCloudEvent() {
        String eventJson = """
            {
              "event_id": "550e8400-e29b-41d4-a716-446655440000",
              "event_type": "TransacaoAutorizada",
              "event_version": "1.0",
              "occurred_at": "2026-09-11T18:30:00Z",
              "correlation_id": "trace-550e8400-e29b-41d4-a716-446655440001",
              "id_autorizacao": "AUTH-123",
              "id_contrato": "CONTA-001",
              "valor": 100.00,
              "saldo_reservado": 99900.00,
              "trace_id": "550e8400-e29b-41d4-a716-446655440002"
            }
            """;
        
        // Act & Assert: Não deve lançar exceção
        assertDoesNotThrow(() -> validator.validate(eventJson));
    }
    
    @Test
    @DisplayName("❌ Deve rejeitar sem event_id")
    void testMissingEventId() {
        String eventJson = """
            {
              "event_type": "TransacaoAutorizada",
              "event_version": "1.0",
              "occurred_at": "2026-09-11T18:30:00Z",
              "correlation_id": "trace-123"
            }
            """;
        
        assertThrows(IllegalArgumentException.class, 
            () -> validator.validate(eventJson),
            "Deve lançar exceção sem event_id");
    }
    
    @Test
    @DisplayName("❌ Deve rejeitar event_id inválido (não UUID)")
    void testInvalidEventIdFormat() {
        String eventJson = """
            {
              "event_id": "not-a-uuid",
              "event_type": "TransacaoAutorizada",
              "event_version": "1.0",
              "occurred_at": "2026-09-11T18:30:00Z",
              "correlation_id": "trace-123"
            }
            """;
        
        assertThrows(IllegalArgumentException.class,
            () -> validator.validate(eventJson),
            "event_id deve ser UUID válido");
    }
    
    @Test
    @DisplayName("❌ Deve rejeitar occurred_at inválido (não ISO 8601)")
    void testInvalidTimestampFormat() {
        String eventJson = """
            {
              "event_id": "550e8400-e29b-41d4-a716-446655440000",
              "event_type": "TransacaoAutorizada",
              "event_version": "1.0",
              "occurred_at": "2026-09-11 18:30:00",
              "correlation_id": "trace-123"
            }
            """;
        
        assertThrows(IllegalArgumentException.class,
            () -> validator.validate(eventJson),
            "occurred_at deve ser ISO 8601");
    }
    
    @Test
    @DisplayName("❌ Deve rejeitar valor negativo")
    void testNegativeValue() {
        String eventJson = """
            {
              "event_id": "550e8400-e29b-41d4-a716-446655440000",
              "event_type": "TransacaoAutorizada",
              "event_version": "1.0",
              "occurred_at": "2026-09-11T18:30:00Z",
              "correlation_id": "trace-123",
              "valor": -100.00
            }
            """;
        
        assertThrows(IllegalArgumentException.class,
            () -> validator.validate(eventJson),
            "valor deve ser > 0");
    }
    
    @Test
    @DisplayName("❌ Deve rejeitar saldo_reservado negativo")
    void testNegativeBalance() {
        String eventJson = """
            {
              "event_id": "550e8400-e29b-41d4-a716-446655440000",
              "event_type": "TransacaoAutorizada",
              "event_version": "1.0",
              "occurred_at": "2026-09-11T18:30:00Z",
              "correlation_id": "trace-123",
              "saldo_reservado": -1000.00
            }
            """;
        
        assertThrows(IllegalArgumentException.class,
            () -> validator.validate(eventJson),
            "saldo_reservado não pode ser negativo");
    }
    
    @Test
    @DisplayName("❌ Deve rejeitar event_version inválido (não semver)")
    void testInvalidSemver() {
        String eventJson = """
            {
              "event_id": "550e8400-e29b-41d4-a716-446655440000",
              "event_type": "TransacaoAutorizada",
              "event_version": "invalid-version",
              "occurred_at": "2026-09-11T18:30:00Z",
              "correlation_id": "trace-123"
            }
            """;
        
        assertThrows(IllegalArgumentException.class,
            () -> validator.validate(eventJson),
            "event_version deve ser semver");
    }
    
    @Test
    @DisplayName("✅ Deve aceitar saldo_reservado zero")
    void testZeroBalance() {
        String eventJson = """
            {
              "event_id": "550e8400-e29b-41d4-a716-446655440000",
              "event_type": "TransacaoAutorizada",
              "event_version": "1.0",
              "occurred_at": "2026-09-11T18:30:00Z",
              "correlation_id": "trace-123",
              "saldo_reservado": 0.00
            }
            """;
        
        assertDoesNotThrow(() -> validator.validate(eventJson));
    }
    
    @Test
    @DisplayName("✅ Deve aceitar trace_id opcional")
    void testOptionalTraceId() {
        String eventJson = """
            {
              "event_id": "550e8400-e29b-41d4-a716-446655440000",
              "event_type": "TransacaoAutorizada",
              "event_version": "1.0",
              "occurred_at": "2026-09-11T18:30:00Z",
              "correlation_id": "trace-123"
            }
            """;
        
        assertDoesNotThrow(() -> validator.validate(eventJson));
    }
    
    @Test
    @DisplayName("❌ Deve rejeitar JSON inválido")
    void testInvalidJson() {
        String eventJson = "{ invalid json }";
        
        assertThrows(IllegalArgumentException.class,
            () -> validator.validate(eventJson),
            "JSON inválido deve falhar");
    }
}
