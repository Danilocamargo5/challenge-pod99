package com.pod99.common.infrastructure;

import com.pod99.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitService Tests")
class RateLimitServiceTest {
    
    private RateLimitService rateLimitService;
    
    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(null);
    }
    
    @Test
    @DisplayName("✅ Primeira requisição deve passar (sem limite)")
    void testFirstRequestPasses() {
        String accountId = "ACC-001";
        int limitPerSecond = 5;
        
        assertDoesNotThrow(() -> {
            rateLimitService.checkRateLimit(accountId, limitPerSecond);
        });
    }
    
    @Test
    @DisplayName("⚠️ Requisições dentro do limite devem passar")
    void testRequestsWithinLimitPass() {
        String accountId = "ACC-002";
        int limitPerSecond = 3;
        
        for (int i = 0; i < limitPerSecond; i++) {
            assertDoesNotThrow(() -> {
                rateLimitService.checkRateLimit(accountId, limitPerSecond);
            });
        }
    }
    
    @Test
    @DisplayName("❌ Requisição acima do limite deve falhar")
    void testRequestAboveLimitThrows() {
        String accountId = "ACC-003";
        int limitPerSecond = 2;
        
        for (int i = 0; i < limitPerSecond; i++) {
            assertDoesNotThrow(() -> {
                rateLimitService.checkRateLimit(accountId, limitPerSecond);
            });
        }
        
        assertThrows(RateLimitExceededException.class, () -> {
            rateLimitService.checkRateLimit(accountId, limitPerSecond);
        });
    }
    
    @Test
    @DisplayName("🔄 Diferentes contas têm limites independentes")
    void testDifferentAccountsHaveIndependentLimits() {
        int limitPerSecond = 2;
        
        assertDoesNotThrow(() -> {
            rateLimitService.checkRateLimit("ACC-A", limitPerSecond);
            rateLimitService.checkRateLimit("ACC-A", limitPerSecond);
        });
        
        assertDoesNotThrow(() -> {
            rateLimitService.checkRateLimit("ACC-B", limitPerSecond);
            rateLimitService.checkRateLimit("ACC-B", limitPerSecond);
        });
        
        assertThrows(RateLimitExceededException.class, () -> {
            rateLimitService.checkRateLimit("ACC-A", limitPerSecond);
        });
    }
}
