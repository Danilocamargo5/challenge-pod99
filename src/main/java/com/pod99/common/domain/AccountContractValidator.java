package com.pod99.common.domain;

import com.pod99.limits.domain.Limit;
import com.pod99.limits.domain.LimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Validador de relação entre Account e Contract
 * 
 * Validações:
 * 1. Contract existe no banco de dados
 * 2. Contract pertence à Account solicitada
 * 
 * Padrão: Object validator (como em bank-transfer-api)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountContractValidator {
    
    private final LimitRepository limitRepository;
    
    /**
     * Valida se contrato existe e pertence à conta
     * 
     * @param accountId Account que fez a requisição (ACC-001)
     * @param contractId Contract solicitado (CONTA-001)
     * @throws IllegalArgumentException se contrato não existe
     * @throws IllegalArgumentException se contrato não pertence à conta
     */
    public void validate(AccountId accountId, ContractId contractId) {
        // 1. Validar formato
        log.info("🔍 Validando Account: {} e Contract: {}", accountId, contractId);
        
        // 2. Verificar se contrato existe
        Limit limit = limitRepository.findByContractId(contractId.getValue())
            .orElseThrow(() -> {
                log.error("❌ Contrato não encontrado: {}", contractId);
                return new IllegalArgumentException(
                    "Contrato não encontrado: " + contractId
                );
            });
        
        // 3. Verificar se contrato pertence à conta
        if (!limit.getIdConta().equals(accountId.getValue())) {
            log.error("❌ Contrato {} não pertence à conta {}", contractId, accountId);
            throw new IllegalArgumentException(
                String.format(
                    "Contrato %s não pertence à conta %s",
                    contractId,
                    accountId
                )
            );
        }
        
        log.info("✅ Validação OK: Account {} pode acessar Contract {}", accountId, contractId);
    }
}
