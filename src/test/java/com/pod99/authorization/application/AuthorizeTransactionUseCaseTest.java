package com.pod99.authorization.application;

import com.pod99.authorization.domain.Authorization;
import com.pod99.authorization.domain.AuthorizationRepository;
import com.pod99.authorization.domain.AuthorizationStatus;
import com.pod99.authorization.domain.TransacaoAutorizadaEvent;
import com.pod99.common.domain.AccountContractValidator;
import com.pod99.common.exception.InsufficientLimitException;
import com.pod99.common.exception.LockAcquisitionException;
import com.pod99.config.EventBridgePublisher;
import com.pod99.config.LockService;
import com.pod99.limits.domain.Limit;
import com.pod99.limits.domain.LimitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("AuthorizeTransactionUseCase Tests")
class AuthorizeTransactionUseCaseTest {
    
    @Mock
    private AuthorizationRepository authRepository;
    
    @Mock
    private LimitRepository limitRepository;
    
    @Mock
    private EventBridgePublisher eventPublisher;
    
    @Mock
    private LockService lockService;
    
    @Mock
    private AccountContractValidator accountContractValidator;
    
    private AuthorizeTransactionUseCase useCase;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        useCase = new AuthorizeTransactionUseCase(
            authRepository, 
            limitRepository, 
            eventPublisher,
            lockService,
            accountContractValidator);
        
        // Setup MDC para testes
        MDC.put("X-Correlation-ID", "test-correlation");
        MDC.put("X-Trace-ID", "test-trace");
    }
    
    @Test
    @DisplayName("✅ Deve autorizar transação (adquire locks, executa, libera locks)")
    void testAuthorizeWithLocksSuccess() {
        // Arrange
        String idContrato = "CONTA-001";
        String idConta = "ACC-001";
        String idempotencyKey = "key-123";
        
        Limit limit = Limit.builder()
            .idContrato(idContrato)
            .limite(new BigDecimal("100000.00"))
            .disponivel(new BigDecimal("100000.00"))
            .reservado(BigDecimal.ZERO)
            .version(0L)
            .build();
        
        List<String> acquiredLocks = List.of(idConta, idContrato);
        
        // Mock locks
        when(lockService.acquireTransactionLocks(idConta, idContrato))
            .thenReturn(acquiredLocks);
        
        when(limitRepository.findByContractId(idContrato))
            .thenReturn(Optional.of(limit));
        when(authRepository.findByIdempotencyKey(idempotencyKey))
            .thenReturn(Optional.empty());
        
        AuthorizeTransactionRequest request = AuthorizeTransactionRequest.builder()
            .idConta(idConta)
            .valor(new BigDecimal("100.00"))
            .moeda("BRL")
            .tipoOperacao("DEBITO")
            .build();
        
        // Act
        AuthorizeTransactionResponse response = useCase.execute(idContrato, request, idempotencyKey);
        
        // Assert
        assertNotNull(response);
        assertEquals("APPROVED", response.getStatus());
        
        // Verify locks
        verify(lockService, times(1))
            .acquireTransactionLocks(idConta, idContrato);
        verify(lockService, times(1))
            .releaseLocks(acquiredLocks);
        verify(authRepository, times(1)).save(any(Authorization.class));
        verify(eventPublisher, times(1)).publish(any());
    }
    
    @Test
    @DisplayName("❌ Deve rejeitar com limite insuficiente (mas liberar locks)")
    void testInsufficientLimitWithLockRelease() {
        // Arrange
        String idContrato = "CONTA-002";
        String idConta = "ACC-002";
        String idempotencyKey = "key-456";
        
        Limit limit = Limit.builder()
            .idContrato(idContrato)
            .limite(new BigDecimal("100.00"))
            .disponivel(new BigDecimal("50.00"))
            .reservado(new BigDecimal("50.00"))
            .build();
        
        List<String> acquiredLocks = List.of(idConta, idContrato);
        
        // Mock locks
        when(lockService.acquireTransactionLocks(idConta, idContrato))
            .thenReturn(acquiredLocks);
        
        when(limitRepository.findByContractId(idContrato))
            .thenReturn(Optional.of(limit));
        when(authRepository.findByIdempotencyKey(idempotencyKey))
            .thenReturn(Optional.empty());
        
        AuthorizeTransactionRequest request = AuthorizeTransactionRequest.builder()
            .idConta(idConta)
            .valor(new BigDecimal("100.00"))
            .moeda("BRL")
            .tipoOperacao("DEBITO")
            .build();
        
        // Act & Assert
        assertThrows(InsufficientLimitException.class,
            () -> useCase.execute(idContrato, request, idempotencyKey));
        
        // Verify: Locks foram liberados
        verify(lockService, times(1))
            .acquireTransactionLocks(idConta, idContrato);
        verify(lockService, times(1))
            .releaseLocks(acquiredLocks);
        verify(authRepository, never()).save(any());
    }
    
    @Test
    @DisplayName("🔒 Deve falhar se não conseguir adquirir lock")
    void testLockAcquisitionFailure() {
        // Arrange
        String idContrato = "CONTA-003";
        String idConta = "ACC-003";
        String idempotencyKey = "key-lock-fail";
        
        // Mock: Lock falha
        when(lockService.acquireTransactionLocks(idConta, idContrato))
            .thenThrow(new LockAcquisitionException("Falha ao adquirir lock"));
        
        AuthorizeTransactionRequest request = AuthorizeTransactionRequest.builder()
            .idConta(idConta)
            .valor(new BigDecimal("100.00"))
            .moeda("BRL")
            .tipoOperacao("DEBITO")
            .build();
        
        // Act & Assert
        assertThrows(LockAcquisitionException.class,
            () -> useCase.execute(idContrato, request, idempotencyKey));
        
        // Verify: Nenhuma lógica foi executada
        verify(authRepository, never()).findByIdempotencyKey(anyString());
        verify(limitRepository, never()).findByContractId(anyString());
        verify(lockService, never()).releaseLocks(any());
    }
    
    @Test
    @DisplayName("🔄 Deve ser idempotente (com locks)")
    void testIdempotencyWithLocks() {
        // Arrange
        String idContrato = "CONTA-001";
        String idConta = "ACC-001";
        String idempotencyKey = "key-idempotent";
        
        Authorization existingAuth = Authorization.builder()
            .idAutorizacao("AUTH-existing")
            .idContrato(idContrato)
            .idConta(idConta)
            .valor(new BigDecimal("100.00"))
            .saldoReservado(new BigDecimal("99900.00"))
            .correlationId("previous-trace")
            .status(AuthorizationStatus.APPROVED)
            .build();
        
        List<String> acquiredLocks = List.of(idConta, idContrato);
        
        // Mock: Locks adquiridos
        when(lockService.acquireTransactionLocks(idConta, idContrato))
            .thenReturn(acquiredLocks);
        
        // Mock: Idempotência já existe
        when(authRepository.findByIdempotencyKey(idempotencyKey))
            .thenReturn(Optional.of(existingAuth));
        
        AuthorizeTransactionRequest request = AuthorizeTransactionRequest.builder()
            .idConta(idConta)
            .valor(new BigDecimal("100.00"))
            .moeda("BRL")
            .tipoOperacao("DEBITO")
            .build();
        
        // Act
        AuthorizeTransactionResponse response = useCase.execute(idContrato, request, idempotencyKey);
        
        // Assert
        assertEquals(existingAuth.getIdAutorizacao(), response.getIdAutorizacao());
        
        // Verify: Locks foram adquiridos e liberados, mas nada mais executado
        verify(lockService, times(1))
            .acquireTransactionLocks(idConta, idContrato);
        verify(lockService, times(1))
            .releaseLocks(acquiredLocks);
        verify(limitRepository, never()).findByContractId(any());
    }
    
    @Test
    @DisplayName("❌ Deve rejeitar contrato inválido (mas liberar locks)")
    void testNonExistentContractWithLockRelease() {
        // Arrange
        String idContrato = "CONTA-999";
        String idConta = "ACC-001";
        String idempotencyKey = "key-789";
        
        List<String> acquiredLocks = List.of(idConta, idContrato);
        
        // Mock: Locks adquiridos
        when(lockService.acquireTransactionLocks(idConta, idContrato))
            .thenReturn(acquiredLocks);
        
        // Mock: Contrato não existe
        when(authRepository.findByIdempotencyKey(idempotencyKey))
            .thenReturn(Optional.empty());
        when(limitRepository.findByContractId(idContrato))
            .thenReturn(Optional.empty());
        
        AuthorizeTransactionRequest request = AuthorizeTransactionRequest.builder()
            .idConta(idConta)
            .valor(new BigDecimal("100.00"))
            .moeda("BRL")
            .tipoOperacao("DEBITO")
            .build();
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
            () -> useCase.execute(idContrato, request, idempotencyKey));
        
        // Verify: Locks foram liberados mesmo com erro
        verify(lockService, times(1))
            .acquireTransactionLocks(idConta, idContrato);
        verify(lockService, times(1))
            .releaseLocks(acquiredLocks);
    }
    
    @Test
    @DisplayName("🔐 Locks devem ser liberados mesmo se evento falhar")
    void testLockReleaseOnEventPublishFailure() {
        // Arrange
        String idContrato = "CONTA-001";
        String idConta = "ACC-001";
        String idempotencyKey = "key-event-fail";
        
        Limit limit = Limit.builder()
            .idContrato(idContrato)
            .limite(new BigDecimal("100000.00"))
            .disponivel(new BigDecimal("100000.00"))
            .build();
        
        List<String> acquiredLocks = List.of(idConta, idContrato);
        
        // Mock: Locks OK
        when(lockService.acquireTransactionLocks(idConta, idContrato))
            .thenReturn(acquiredLocks);
        
        when(authRepository.findByIdempotencyKey(idempotencyKey))
            .thenReturn(Optional.empty());
        when(limitRepository.findByContractId(idContrato))
            .thenReturn(Optional.of(limit));
        
        Authorization savedAuth = Authorization.builder()
            .idAutorizacao("AUTH-123")
            .idContrato(idContrato)
            .valor(new BigDecimal("100.00"))
            .build();
        doNothing().when(authRepository).save(any());
        
        // Mock: Evento falha (doThrow para métodos void)
        doThrow(new RuntimeException("EventBridge timeout"))
            .when(eventPublisher).publish(any());
        
        AuthorizeTransactionRequest request = AuthorizeTransactionRequest.builder()
            .idConta(idConta)
            .valor(new BigDecimal("100.00"))
            .moeda("BRL")
            .tipoOperacao("DEBITO")
            .build();
        
        // Act & Assert
        assertThrows(RuntimeException.class,
            () -> useCase.execute(idContrato, request, idempotencyKey));
        
        // Verify: Locks foram liberados mesmo com erro no evento
        verify(lockService, times(1))
            .acquireTransactionLocks(idConta, idContrato);
        verify(lockService, times(1))
            .releaseLocks(acquiredLocks);
    }
}
