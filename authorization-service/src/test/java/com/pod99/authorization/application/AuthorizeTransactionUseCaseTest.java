package com.pod99.authorization.application;

import com.pod99.config.EventBridgePublisher;
import com.pod99.config.LockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizeTransactionUseCaseTest {
    
    @Mock
    private EventBridgePublisher eventPublisher;
    
    @Mock
    private LockService lockService;
    
    @Mock
    private RestTemplate restTemplate;
    
    @InjectMocks
    private AuthorizeTransactionUseCase useCase;
    
    @Test
    void testAuthorizeTransaction_Success() {
        // Arrange
        String idContrato = "CONTA-001";
        AuthorizeTransactionUseCase.AuthorizeTransactionRequest request = new AuthorizeTransactionUseCase.AuthorizeTransactionRequest();
        request.setIdConta("ACC-001");
        request.setValor(BigDecimal.valueOf(100.00));
        request.setMoeda("BRL");
        request.setTipoOperacao("DEBITO");
        
        String idempotencyKey = "teste-123";
        
        when(lockService.acquireTransactionLocks(anyString(), eq(idContrato)))
                .thenReturn(Arrays.asList("lock1", "lock2"));
        
        AuthorizeTransactionUseCase.LimitReserveResponse limitResponse = new AuthorizeTransactionUseCase.LimitReserveResponse();
        limitResponse.setId(idContrato);
        limitResponse.setSaldoAtual(9900.0);
        limitResponse.setSaldoAnterior(10000.0);
        limitResponse.setReservado(100.0);
        
        when(restTemplate.postForObject(anyString(), any(), eq(AuthorizeTransactionUseCase.LimitReserveResponse.class)))
                .thenReturn(limitResponse);
        
        // Act
        AuthorizeTransactionUseCase.AuthorizeTransactionResponse response = 
                useCase.execute(idContrato, request, idempotencyKey);
        
        // Assert
        assertNotNull(response);
        assertEquals("APPROVED", response.getStatus());
        assertEquals(100.0, response.getSaldoReservado());
        
        verify(lockService).acquireTransactionLocks(anyString(), eq(idContrato));
        verify(restTemplate).postForObject(anyString(), any(), eq(AuthorizeTransactionUseCase.LimitReserveResponse.class));
        verify(eventPublisher).publishTransactionAuthorized(anyString(), eq(idContrato), eq("ACC-001"), anyDouble(), eq("BRL"));
        verify(lockService).releaseLocks(anyList());
    }
    
    @Test
    void testAuthorizeTransaction_InvalidValue() {
        // Arrange
        String idContrato = "CONTA-001";
        AuthorizeTransactionUseCase.AuthorizeTransactionRequest request = new AuthorizeTransactionUseCase.AuthorizeTransactionRequest();
        request.setIdConta("ACC-001");
        request.setValor(BigDecimal.valueOf(-100.00)); // Valor negativo
        request.setMoeda("BRL");
        request.setTipoOperacao("DEBITO");
        
        when(lockService.acquireTransactionLocks(anyString(), eq(idContrato)))
                .thenReturn(Arrays.asList("lock1", "lock2"));
        
        // Act & Assert
        assertThrows(RuntimeException.class, () -> 
                useCase.execute(idContrato, request, "teste-123"));
        
        verify(lockService).releaseLocks(anyList());
    }
}
