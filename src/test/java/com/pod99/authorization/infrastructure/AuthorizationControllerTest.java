package com.pod99.authorization.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pod99.authorization.application.AuthorizeTransactionRequest;
import com.pod99.authorization.application.AuthorizeTransactionResponse;
import com.pod99.authorization.application.AuthorizeTransactionUseCase;
import com.pod99.common.exception.InsufficientLimitException;
import com.pod99.common.exception.LockAcquisitionException;
import com.pod99.config.LockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthorizationController Integration Tests")
class AuthorizationControllerTest {
    
    private MockMvc mockMvc;
    
    @Mock
    private AuthorizeTransactionUseCase authorizeUseCase;
    
    @Mock
    private LockService lockService;
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AuthorizationController controller = new AuthorizationController(
            authorizeUseCase, 
            lockService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }
    
    @Test
    @DisplayName("✅ Deve autorizar transação com sucesso (locks adquiridos e liberados)")
    void testAuthorizeTransactionSuccess() throws Exception {
        // Arrange
        String idContrato = "CONTA-001";
        String idConta = "ACC-001";
        String idempotencyKey = UUID.randomUUID().toString();
        
        AuthorizeTransactionRequest request = AuthorizeTransactionRequest.builder()
            .idConta(idConta)
            .valor(new BigDecimal("100.00"))
            .moeda("BRL")
            .tipoOperacao("DEBITO")
            .build();
        
        AuthorizeTransactionResponse response = AuthorizeTransactionResponse.builder()
            .idAutorizacao("AUTH-123")
            .saldoReservado(new BigDecimal("99900.00"))
            .correlationId("trace-123")
            .status("APPROVED")
            .build();
        
        // Mock: Locks adquiridos com sucesso
        when(lockService.acquireTransactionLocks(idConta, idContrato))
            .thenReturn(List.of(idConta, idContrato));
        
        // Mock: UseCase executa com sucesso
        when(authorizeUseCase.execute(idContrato, request, idempotencyKey))
            .thenReturn(response);
        
        // Act & Assert
        mockMvc.perform(post("/v1/contratos/{idContrato}/autorizacoes", idContrato)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id_autorizacao").value("AUTH-123"))
            .andExpect(jsonPath("$.saldo_reservado").value(99900.00))
            .andExpect(jsonPath("$.status").value("APPROVED"));
        
        // Verify: Locks foram adquiridos e liberados
        verify(lockService, times(1)).acquireTransactionLocks(idConta, idContrato);
        verify(lockService, times(1)).releaseLocks(any());
        verify(authorizeUseCase, times(1)).execute(idContrato, request, idempotencyKey);
    }
    
    @Test
    @DisplayName("🔒 Deve retornar 409 Conflict quando falha em adquirir lock")
    void testConflictOnLockAcquisitionFailure() throws Exception {
        // Arrange
        String idContrato = "CONTA-001";
        String idConta = "ACC-001";
        String idempotencyKey = UUID.randomUUID().toString();
        
        AuthorizeTransactionRequest request = AuthorizeTransactionRequest.builder()
            .idConta(idConta)
            .valor(new BigDecimal("100.00"))
            .moeda("BRL")
            .tipoOperacao("DEBITO")
            .build();
        
        // Mock: Lock falha (race condition)
        when(lockService.acquireTransactionLocks(idConta, idContrato))
            .thenThrow(new LockAcquisitionException("Falha ao adquirir lock"));
        
        // Act & Assert
        mockMvc.perform(post("/v1/contratos/{idContrato}/autorizacoes", idContrato)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error_code").value("CONFLICT"))
            .andExpect(jsonPath("$.message").exists());
        
        // Verify: UseCase NÃO deve ser executado se lock falhar
        verify(authorizeUseCase, never()).execute(anyString(), any(), anyString());
        verify(lockService, times(1)).acquireTransactionLocks(idConta, idContrato);
        // releaseLocks NÃO deve ser chamado (nenhum lock foi adquirido)
        verify(lockService, never()).releaseLocks(any());
    }
    
    @Test
    @DisplayName("❌ Deve retornar 402 Payment Required quando limite insuficiente")
    void testInsufficientLimit() throws Exception {
        // Arrange
        String idContrato = "CONTA-001";
        String idConta = "ACC-001";
        String idempotencyKey = UUID.randomUUID().toString();
        
        AuthorizeTransactionRequest request = AuthorizeTransactionRequest.builder()
            .idConta(idConta)
            .valor(new BigDecimal("999999.00"))
            .moeda("BRL")
            .tipoOperacao("DEBITO")
            .build();
        
        // Mock: Locks adquiridos
        when(lockService.acquireTransactionLocks(idConta, idContrato))
            .thenReturn(List.of(idConta, idContrato));
        
        // Mock: UseCase lança exception de limite insuficiente
        when(authorizeUseCase.execute(idContrato, request, idempotencyKey))
            .thenThrow(new InsufficientLimitException("Limite insuficiente"));
        
        // Act & Assert
        mockMvc.perform(post("/v1/contratos/{idContrato}/autorizacoes", idContrato)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().is(402))  // PAYMENT_REQUIRED
            .andExpect(jsonPath("$.error_code").value("INSUFFICIENT_LIMIT"))
            .andExpect(jsonPath("$.correlation_id").exists());
        
        // Verify: Locks foram adquiridos E liberados (mesmo com erro)
        verify(lockService, times(1)).acquireTransactionLocks(idConta, idContrato);
        verify(lockService, times(1)).releaseLocks(any());
    }
    
    @Test
    @DisplayName("🔐 Deve liberar locks em caso de erro no UseCase")
    void testLockReleaseOnUseCaseFailure() throws Exception {
        // Arrange
        String idContrato = "CONTA-001";
        String idConta = "ACC-001";
        String idempotencyKey = UUID.randomUUID().toString();
        
        AuthorizeTransactionRequest request = AuthorizeTransactionRequest.builder()
            .idConta(idConta)
            .valor(new BigDecimal("100.00"))
            .moeda("BRL")
            .tipoOperacao("DEBITO")
            .build();
        
        // Mock: Locks adquiridos
        List<String> locks = List.of(idConta, idContrato);
        when(lockService.acquireTransactionLocks(idConta, idContrato))
            .thenReturn(locks);
        
        // Mock: UseCase falha com erro genérico
        when(authorizeUseCase.execute(idContrato, request, idempotencyKey))
            .thenThrow(new RuntimeException("Erro ao processar autorização"));
        
        // Act & Assert
        mockMvc.perform(post("/v1/contratos/{idContrato}/autorizacoes", idContrato)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error_code").value("INTERNAL_ERROR"));
        
        // Verify: Locks FORAM adquiridos e FORAM liberados (mesmo com erro)
        verify(lockService, times(1)).acquireTransactionLocks(idConta, idContrato);
        verify(lockService, times(1)).releaseLocks(locks);
    }
    
    @Test
    @DisplayName("⚠️ Deve retornar 422 Unprocessable Entity em validação")
    void testValidationError() throws Exception {
        // Arrange
        String idContrato = "CONTA-001";
        String idConta = "ACC-001";
        String idempotencyKey = UUID.randomUUID().toString();
        
        AuthorizeTransactionRequest request = AuthorizeTransactionRequest.builder()
            .idConta(idConta)
            .valor(new BigDecimal("-100.00"))  // Negativo (inválido)
            .moeda("BRL")
            .tipoOperacao("DEBITO")
            .build();
        
        // Mock: Locks adquiridos
        when(lockService.acquireTransactionLocks(idConta, idContrato))
            .thenReturn(List.of(idConta, idContrato));
        
        // Mock: UseCase lança validação error
        when(authorizeUseCase.execute(idContrato, request, idempotencyKey))
            .thenThrow(new IllegalArgumentException("Valor deve ser > 0"));
        
        // Act & Assert
        mockMvc.perform(post("/v1/contratos/{idContrato}/autorizacoes", idContrato)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));
        
        // Verify: Locks foram liberados
        verify(lockService, times(1)).releaseLocks(any());
    }
    
    @Test
    @DisplayName("🔒 Ordem LIFO: Libera locks em ordem reversa (CONTA primeiro, depois ACC)")
    void testLockReleaseOrderLIFO() throws Exception {
        // Arrange
        String idContrato = "CONTA-001";
        String idConta = "ACC-001";
        String idempotencyKey = UUID.randomUUID().toString();
        
        AuthorizeTransactionRequest request = AuthorizeTransactionRequest.builder()
            .idConta(idConta)
            .valor(new BigDecimal("100.00"))
            .moeda("BRL")
            .tipoOperacao("DEBITO")
            .build();
        
        AuthorizeTransactionResponse response = AuthorizeTransactionResponse.builder()
            .idAutorizacao("AUTH-123")
            .saldoReservado(new BigDecimal("99900.00"))
            .correlationId("trace-123")
            .status("APPROVED")
            .build();
        
        // Mock: Locks adquiridos em ordem FIFO [ACC, CONTA]
        List<String> locksAcquired = List.of(idConta, idContrato);
        when(lockService.acquireTransactionLocks(idConta, idContrato))
            .thenReturn(locksAcquired);
        
        when(authorizeUseCase.execute(idContrato, request, idempotencyKey))
            .thenReturn(response);
        
        // Act
        mockMvc.perform(post("/v1/contratos/{idContrato}/autorizacoes", idContrato)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
        
        // Assert: releaseLocks deve ser chamado com os locks adquiridos
        verify(lockService, times(1)).releaseLocks(locksAcquired);
    }
}
