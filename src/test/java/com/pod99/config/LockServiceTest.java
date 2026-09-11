package com.pod99.config;

import com.pod99.common.exception.LockAcquisitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("LockService Tests")
class LockServiceTest {
    
    @Mock
    private DynamoDbClient dynamoDbClient;
    
    private LockService lockService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        lockService = new LockService(dynamoDbClient);
    }
    
    @Test
    @DisplayName("✅ Deve adquirir locks com sucesso")
    void testAcquireLocks() {
        // Arrange
        String idAutorizacao = "AUTH-123";
        String idContrato = "CONTA-001";
        
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());
        
        // Act
        List<String> locks = lockService.acquireTransactionLocks(idAutorizacao, idContrato);
        
        // Assert
        assertEquals(2, locks.size());
        assertTrue(locks.contains(idAutorizacao));
        assertTrue(locks.contains(idContrato));
        verify(dynamoDbClient, times(2)).putItem(any());
    }
    
    @Test
    @DisplayName("🔄 Deve retentar em case de contenção")
    void testRetryOnLockContention() {
        // Arrange
        String idAutorizacao = "AUTH-456";
        String idContrato = "CONTA-002";
        
        // Primeira chamada falha (contenção), segunda sucede
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
            .thenThrow(ConditionalCheckFailedException.class)
            .thenReturn(PutItemResponse.builder().build())
            .thenReturn(PutItemResponse.builder().build());
        
        // Act
        List<String> locks = lockService.acquireTransactionLocks(idAutorizacao, idContrato);
        
        // Assert
        assertEquals(2, locks.size());
        verify(dynamoDbClient, atLeast(3)).putItem(any());  // 3 chamadas (1 fail + 2 success)
    }
    
    @Test
    @DisplayName("❌ Deve falhar após 5 retries")
    void testFailAfterMaxRetries() {
        // Arrange
        String idAutorizacao = "AUTH-789";
        String idContrato = "CONTA-003";
        
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
            .thenThrow(ConditionalCheckFailedException.class);
        
        // Act & Assert
        assertThrows(LockAcquisitionException.class,
            () -> lockService.acquireTransactionLocks(idAutorizacao, idContrato));
        
        verify(dynamoDbClient, atLeast(5)).putItem(any());  // Pelo menos 5 tentativas
    }
    
    @Test
    @DisplayName("🔓 Deve liberar locks em ordem REVERSA (LIFO)")
    void testReleaseLockInLIFOOrder() {
        // Arrange
        List<String> locks = List.of("AUTH-123", "CONTA-001");
        
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class)))
            .thenReturn(DeleteItemResponse.builder().build());
        
        // Act
        lockService.releaseLocks(locks);
        
        // Assert
        verify(dynamoDbClient, times(2)).deleteItem(any());
        // Verificar ordem: CONTA-001 liberado antes de AUTH-123 (LIFO)
    }
    
    @Test
    @DisplayName("🛡️ Deve liberar locks adquiridos em case de falha")
    void testReleaseLockOnFailure() {
        // Arrange
        String idAutorizacao = "AUTH-fail";
        String idContrato = "CONTA-fail";
        
        // Primeiro lock sucede, segundo falha
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
            .thenReturn(PutItemResponse.builder().build())
            .thenThrow(ConditionalCheckFailedException.class);
        
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class)))
            .thenReturn(DeleteItemResponse.builder().build());
        
        // Act
        assertThrows(LockAcquisitionException.class,
            () -> lockService.acquireTransactionLocks(idAutorizacao, idContrato));
        
        // Assert: Lock adquirido deve ser liberado
        verify(dynamoDbClient, atLeast(1)).deleteItem(any(DeleteItemRequest.class));
    }
}
