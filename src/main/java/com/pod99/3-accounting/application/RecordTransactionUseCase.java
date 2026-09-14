package com.pod99.accounting.application;

import com.pod99.accounting.domain.AccountingEntry;
import com.pod99.accounting.domain.AccountingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordTransactionUseCase {
    
    private final AccountingRepository accountingRepository;
    
    public void record(AccountingEntry entry) {
        // Verifica idempotência
        if (accountingRepository.findByEventId(entry.getEventId()).isPresent()) {
            log.warn("⚠️ Evento já processado: {}", entry.getEventId());
            return;
        }
        
        entry.setRegistradoEm(LocalDateTime.now());
        accountingRepository.save(entry);
        
        log.info("💰 Lançamento contábil registrado: evento={}, valor={}", 
            entry.getEventId(), entry.getValor());
    }
}
