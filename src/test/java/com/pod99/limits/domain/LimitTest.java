package com.pod99.limits.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Limit Domain Tests")
class LimitTest {
    
    private Limit limit;
    
    @BeforeEach
    void setUp() {
        limit = Limit.builder()
            .idContrato("CONTA-001")
            .limite(new BigDecimal("100000.00"))
            .disponivel(new BigDecimal("100000.00"))
            .reservado(BigDecimal.ZERO)
            .version(0L)
            .build();
    }
    
    @Test
    @DisplayName("✅ Deve reservar limite com sucesso")
    void testReserveLimit() {
        // Act
        limit.reserve(new BigDecimal("1000.00"));
        
        // Assert
        assertEquals(new BigDecimal("99000.00"), limit.getDisponivel());
        assertEquals(new BigDecimal("1000.00"), limit.getReservado());
    }
    
    @Test
    @DisplayName("❌ Deve rejeitar se limite insuficiente")
    void testReserveWithInsufficientLimit() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
            () -> limit.reserve(new BigDecimal("150000.00")));
        
        // Verificar que não mudou
        assertEquals(new BigDecimal("100000.00"), limit.getDisponivel());
        assertEquals(BigDecimal.ZERO, limit.getReservado());
    }
    
    @Test
    @DisplayName("🔄 Deve permitir reserva que deixa saldo zero")
    void testReserveExactAmount() {
        // Act
        limit.reserve(new BigDecimal("100000.00"));
        
        // Assert
        assertEquals(BigDecimal.ZERO, limit.getDisponivel());
        assertEquals(new BigDecimal("100000.00"), limit.getReservado());
    }
    
    @Test
    @DisplayName("🔓 Deve liberar limite reservado")
    void testReleaseLimitPartially() {
        // Arrange
        limit.reserve(new BigDecimal("5000.00"));
        
        // Act
        limit.release(new BigDecimal("2000.00"));
        
        // Assert
        assertEquals(new BigDecimal("97000.00"), limit.getDisponivel());
        assertEquals(new BigDecimal("3000.00"), limit.getReservado());
    }
    
    @Test
    @DisplayName("🔓 Deve liberar todo limite reservado")
    void testReleaseFullAmount() {
        // Arrange
        limit.reserve(new BigDecimal("10000.00"));
        
        // Act
        limit.release(new BigDecimal("10000.00"));
        
        // Assert
        assertEquals(new BigDecimal("100000.00"), limit.getDisponivel());
        assertEquals(BigDecimal.ZERO, limit.getReservado());
    }
    
    @Test
    @DisplayName("🔀 Deve suportar múltiplas reservas sequenciais")
    void testMultipleReservations() {
        // Act
        limit.reserve(new BigDecimal("1000.00"));
        limit.reserve(new BigDecimal("2000.00"));
        limit.reserve(new BigDecimal("3000.00"));
        
        // Assert
        assertEquals(new BigDecimal("94000.00"), limit.getDisponivel());
        assertEquals(new BigDecimal("6000.00"), limit.getReservado());
    }
    
    @Test
    @DisplayName("❌ Deve rejeitar reserva após limite zerado")
    void testReserveAfterZeroLimit() {
        // Arrange
        limit.reserve(new BigDecimal("100000.00"));
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
            () -> limit.reserve(new BigDecimal("1.00")));
    }
}
