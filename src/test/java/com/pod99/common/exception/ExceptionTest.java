package com.pod99.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Exception Classes Tests")
class ExceptionTest {
    
    @Test
    @DisplayName("✅ IdempotencyException deve ser lançada com mensagem")
    void testIdempotencyException() {
        String message = "Transação já foi processada";
        
        IdempotencyException ex = assertThrows(IdempotencyException.class, () -> {
            throw new IdempotencyException(message);
        });
        
        assertEquals(message, ex.getMessage());
        assertInstanceOf(RuntimeException.class, ex);
    }
    
    @Test
    @DisplayName("✅ InsufficientLimitException deve ser lançada com mensagem")
    void testInsufficientLimitException() {
        String message = "Limite insuficiente para transação";
        
        InsufficientLimitException ex = assertThrows(InsufficientLimitException.class, () -> {
            throw new InsufficientLimitException(message);
        });
        
        assertEquals(message, ex.getMessage());
        assertInstanceOf(RuntimeException.class, ex);
    }
    
    @Test
    @DisplayName("✅ LockAcquisitionException com mensagem")
    void testLockAcquisitionExceptionWithMessage() {
        String message = "Falha ao adquirir lock";
        
        LockAcquisitionException ex = assertThrows(LockAcquisitionException.class, () -> {
            throw new LockAcquisitionException(message);
        });
        
        assertEquals(message, ex.getMessage());
        assertNull(ex.getCause());
    }
    
    @Test
    @DisplayName("✅ LockAcquisitionException com mensagem e causa")
    void testLockAcquisitionExceptionWithCause() {
        String message = "Falha ao adquirir lock";
        RuntimeException cause = new RuntimeException("Timeout");
        
        LockAcquisitionException ex = assertThrows(LockAcquisitionException.class, () -> {
            throw new LockAcquisitionException(message, cause);
        });
        
        assertEquals(message, ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
    
    @Test
    @DisplayName("✅ RateLimitExceededException com todos os parâmetros")
    void testRateLimitExceededException() {
        String accountId = "ACC-001";
        int requestCount = 10;
        int limitPerSecond = 5;
        long retryAfterSeconds = 2;
        
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, () -> {
            throw new RateLimitExceededException(accountId, requestCount, limitPerSecond, retryAfterSeconds);
        });
        
        assertTrue(ex.getMessage().contains("ACC-001"));
        assertTrue(ex.getMessage().contains("10"));
        assertTrue(ex.getMessage().contains("5"));
        assertEquals(retryAfterSeconds, ex.getRetryAfterSeconds());
    }
    
    @Test
    @DisplayName("✅ RateLimitExceededException getters")
    void testRateLimitExceededExceptionGetters() {
        String accountId = "ACC-002";
        int requestCount = 15;
        int limitPerSecond = 5;
        long retryAfterSeconds = 3;
        
        RateLimitExceededException ex = new RateLimitExceededException(
            accountId, requestCount, limitPerSecond, retryAfterSeconds
        );
        
        assertEquals(accountId, ex.getAccountId());
        assertEquals(requestCount, ex.getRequestCount());
        assertEquals(limitPerSecond, ex.getLimitPerSecond());
        assertEquals(retryAfterSeconds, ex.getRetryAfterSeconds());
    }
}
