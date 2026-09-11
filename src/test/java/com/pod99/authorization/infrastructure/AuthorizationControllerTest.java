package com.pod99.authorization.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pod99.authorization.application.AuthorizeTransactionRequest;
import com.pod99.authorization.application.AuthorizeTransactionResponse;
import com.pod99.authorization.application.AuthorizeTransactionUseCase;
import com.pod99.common.exception.InsufficientLimitException;
import com.pod99.common.exception.LockAcquisitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes do Controller (HTTP Adapter)
 * 
 * RESPONSABILIDADE: Testar conversão HTTP ↔ Objects
 * - Request parsing
 * - Response formatting
 * - Status codes
 * - Error handling
 * 
 * NÃO testa:
 * - Locks (responsabilidade do UseCase)
 * - Lógica de negócio (responsabilidade do UseCase)
 * - Validação de limite (responsabilidade do UseCase/Domain)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthorizationController Tests")
class AuthorizationControllerTest {
    
    private MockMvc mockMvc;
    
    @Mock
    private AuthorizeTransactionUseCase authorizeUseCase;
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AuthorizationController controller = new AuthorizationController(authorizeUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }
    
    @Test
    @DisplayName("✅ Deve retornar 201 Created em autorização bem-sucedida")
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
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.correlation_id").exists());
        
        // Verify: UseCase foi chamado com argumentos corretos
        verify(authorizeUseCase, times(1))
            .execute(idContrato, request, idempotencyKey);
    }
    
    @Test
    @DisplayName("🔒 Deve retornar 409 Conflict quando UseCase falha em lock")
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
        
        // Mock: UseCase lança LockAcquisitionException
        when(authorizeUseCase.execute(idContrato, request, idempotencyKey))
            .thenThrow(new LockAcquisitionException("Falha ao adquirir lock"));
        
        // Act & Assert
        mockMvc.perform(post("/v1/contratos/{idContrato}/autorizacoes", idContrato)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())  // 409
            .andExpect(jsonPath("$.error_code").value("CONFLICT"))
            .andExpect(jsonPath("$.correlation_id").exists());
        
        verify(authorizeUseCase, times(1))
            .execute(anyString(), any(), anyString());
    }
    
    @Test
    @DisplayName("❌ Deve retornar 402 Payment Required em limite insuficiente")
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
        
        // Mock: UseCase lança InsufficientLimitException
        when(authorizeUseCase.execute(idContrato, request, idempotencyKey))
            .thenThrow(new InsufficientLimitException("Limite insuficiente"));
        
        // Act & Assert
        mockMvc.perform(post("/v1/contratos/{idContrato}/autorizacoes", idContrato)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().is(402))  // PAYMENT_REQUIRED
            .andExpect(jsonPath("$.error_code").value("INSUFFICIENT_LIMIT"));
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
            .valor(new BigDecimal("-100.00"))  // Negativo
            .moeda("BRL")
            .tipoOperacao("DEBITO")
            .build();
        
        // Mock: UseCase lança validação error
        when(authorizeUseCase.execute(idContrato, request, idempotencyKey))
            .thenThrow(new IllegalArgumentException("Valor deve ser > 0"));
        
        // Act & Assert
        mockMvc.perform(post("/v1/contratos/{idContrato}/autorizacoes", idContrato)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())  // 422
            .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));
    }
    
    @Test
    @DisplayName("💥 Deve retornar 500 Internal Server Error em erro genérico")
    void testInternalError() throws Exception {
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
        
        // Mock: UseCase falha com erro genérico
        when(authorizeUseCase.execute(idContrato, request, idempotencyKey))
            .thenThrow(new RuntimeException("Erro ao conectar com DynamoDB"));
        
        // Act & Assert
        mockMvc.perform(post("/v1/contratos/{idContrato}/autorizacoes", idContrato)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isInternalServerError())  // 500
            .andExpect(jsonPath("$.error_code").value("INTERNAL_ERROR"));
    }
    
    @Test
    @DisplayName("📡 Deve incluir correlation_id em todas as respostas")
    void testCorrelationIdInResponse() throws Exception {
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
        
        when(authorizeUseCase.execute(idContrato, request, idempotencyKey))
            .thenReturn(response);
        
        // Act & Assert
        mockMvc.perform(post("/v1/contratos/{idContrato}/autorizacoes", idContrato)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.correlation_id").exists());
    }
}
