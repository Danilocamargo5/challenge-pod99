package com.pod99.authorization.application;

import com.pod99.authorization.domain.Authorization;
import com.pod99.authorization.domain.AuthorizationRepository;
import com.pod99.authorization.domain.AuthorizationStatus;
import com.pod99.common.exception.InsufficientLimitException;
import com.pod99.config.EventBridgePublisher;
import com.pod99.limits.domain.Limit;
import com.pod99.limits.domain.LimitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AuthorizeTransactionUseCase Tests")
class AuthorizeTransactionUseCaseTest {
    
    @Mock
    private AuthorizationRepository authRepository;
    
    @Mock
    private LimitRepository limitRepository;
    
    @Mock
    private EventBridgePublisher eventPublisher;
    
    private AuthorizeTransactionUseCase useCase;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        useCase = new AuthorizeTransactionUseCase(authRepository, limitRepository, eventPublisher);
    }
    
    @Test
    @DisplayName("✅ Deve autorizar transação com limite suficiente")
    void testAuthorizeWithSufficientLimit() {
        // Arrange
        String idContrato = "CONTA-001";
        String idempotencyKey = "key-123";
        
        Limit limit = Limit.builder()
            .idContrato(idContrato)
            .limite(new BigDecimal("100000.00"))
            .disponivel(new BigDecimal("100000.00"))
            .reservado(BigDecimal.ZERO)
            .version(0L)
            .build();
        
        when(limitRepository.findByContractId(idContrato)).thenReturn(Optional.of(limit));
        when(authRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        
        AuthorizeTransactionRequest request = new AuthorizeTransactionRequest(
            "ACC-001", 
            new BigDecimal("100.00"), 
            "BRL", 
            "DEBITO", 
            null, 
            null
        );
        
        // Act
        AuthorizeTransactionResponse response = useCase.execute(idContrato, request, idempotencyKey);
        
        // Assert
        assertNotNull(response);
        assertEquals("ACC-001", response.getCorrelationId());
        assertEquals(new BigDecimal("99900.00"), response.getSaldoReservado());
        verify(authRepository).save(any(Authorization.class));
        verify(eventPublisher).publish(any());
    }
    
    @Test
    @DisplayName("❌ Deve rejeitar se limite insuficiente")
    void testAuthorizeWithInsufficientLimit() {
        // Arrange
        String idContrato = "CONTA-002";
        String idempotencyKey = "key-456";
        
        Limit limit = Limit.builder()
            .idContrato(idContrato)
            .limite(new BigDecimal("100.00"))
            .disponivel(new BigDecimal("50.00"))
            .reservado(new BigDecimal("50.00"))
            .build();
        
        when(limitRepository.findByContractId(idContrato)).thenReturn(Optional.of(limit));
        when(authRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        
        AuthorizeTransactionRequest request = new AuthorizeTransactionRequest(
            "ACC-002",
            new BigDecimal("100.00"),
            "BRL",
            "DEBITO",
            null,
            null
        );
        
        // Act & Assert
        assertThrows(InsufficientLimitException.class, 
            () -> useCase.execute(idContrato, request, idempotencyKey));
        
        verify(authRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
    
    @Test
    @DisplayName("🔄 Deve ser idempotente para mesma requisição")
    void testIdempotencyCheck() {
        // Arrange
        String idContrato = "CONTA-001";
        String idempotencyKey = "key-idempotent";
        
        Authorization existingAuth = Authorization.builder()
            .idAutorizacao("AUTH-existing")
            .idContrato(idContrato)
            .status(AuthorizationStatus.APPROVED)
            .saldoReservado(new BigDecimal("99900.00"))
            .build();
        
        when(authRepository.findByIdempotencyKey(idempotencyKey))
            .thenReturn(Optional.of(existingAuth));
        
        AuthorizeTransactionRequest request = new AuthorizeTransactionRequest(
            "ACC-001",
            new BigDecimal("100.00"),
            "BRL",
            "DEBITO",
            null,
            null
        );
        
        // Act
        AuthorizeTransactionResponse response = useCase.execute(idContrato, request, idempotencyKey);
        
        // Assert
        assertEquals(existingAuth.getIdAutorizacao(), response.getIdAutorizacao());
        verify(limitRepository, never()).findByContractId(any());
    }
    
    @Test
    @DisplayName("❌ Deve rejeitar se contrato não existe")
    void testAuthorizeWithNonExistentContract() {
        // Arrange
        String idContrato = "CONTA-INVALID";
        String idempotencyKey = "key-789";
        
        when(limitRepository.findByContractId(idContrato)).thenReturn(Optional.empty());
        when(authRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        
        AuthorizeTransactionRequest request = new AuthorizeTransactionRequest(
            "ACC-001",
            new BigDecimal("100.00"),
            "BRL",
            "DEBITO",
            null,
            null
        );
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
            () -> useCase.execute(idContrato, request, idempotencyKey));
    }
}
