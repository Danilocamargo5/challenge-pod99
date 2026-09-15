package com.pod99.authorization.application;

import com.pod99.config.EventBridgePublisher;
import com.pod99.config.LockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Use Case para Autorizar Transações
 * TODO: Integrar com Limits Service via HTTP
 * TODO: Implementar lógica de autorização completa
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizeTransactionUseCase {
    
    private final EventBridgePublisher eventPublisher;
    private final LockService lockService;
    
    public Object execute(String accountId, Object request, String contractId) {
        // TODO: Implementar após integração com limits-service
        log.info("TODO: Implement authorize logic");
        return null;
    }
    
    public void authorize(String accountId, String contractId, Object request) {
        // TODO: Implementar após integração com limits-service
        log.info("TODO: Implement authorize logic");
    }
}
