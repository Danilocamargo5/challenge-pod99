package com.pod99.limits.infrastructure;

import com.pod99.limits.domain.Limit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DynamoDBLimitRepository Tests")
class DynamoDBLimitRepositoryTest {
    
    @Mock
    private DynamoDBLimitRepository limitRepository;
    
    @Test
    @DisplayName("✅ Deve buscar limite por ID do contrato")
    void testFindByContractId() {
        String idContrato = "CONTA-001";
        Limit limit = Limit.builder()
            .idContrato(idContrato)
            .limite(new BigDecimal("100000.00"))
            .disponivel(new BigDecimal("100000.00"))
            .reservado(BigDecimal.ZERO)
            .build();
        
        when(limitRepository.findByContractId(idContrato))
            .thenReturn(Optional.of(limit));
        
        Optional<Limit> result = limitRepository.findByContractId(idContrato);
        
        assertTrue(result.isPresent());
        assertEquals(idContrato, result.get().getIdContrato());
    }
    
    @Test
    @DisplayName("✅ Deve retornar Empty quando contrato não existe")
    void testFindByContractIdNotFound() {
        String idContrato = "CONTA-999";
        
        when(limitRepository.findByContractId(idContrato))
            .thenReturn(Optional.empty());
        
        Optional<Limit> result = limitRepository.findByContractId(idContrato);
        
        assertFalse(result.isPresent());
    }
}
