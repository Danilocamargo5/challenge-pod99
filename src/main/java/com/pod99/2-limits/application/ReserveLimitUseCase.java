package com.pod99.limits.application;

import com.pod99.limits.domain.Limit;
import com.pod99.limits.domain.LimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReserveLimitUseCase {
    
    private final LimitRepository limitRepository;
    
    public void reserve(String idContrato, BigDecimal valor) {
        Limit limit = limitRepository.findByContractId(idContrato)
            .orElseThrow(() -> new IllegalArgumentException("Contrato não encontrado"));
        
        limit.reserve(valor);
        limitRepository.update(limit);
        
        log.info("✅ Limite reservado: contrato={}, valor={}, disponível={}", 
            idContrato, valor, limit.getDisponivel());
    }
}
