package com.pod99.authorization.application;

import com.pod99.authorization.domain.Authorization;
import com.pod99.authorization.domain.AuthorizationRepository;
import com.pod99.config.EventBridgePublisher;
import com.pod99.config.LockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Use Case para Autorizar Transações
 * 
 * RESPONSABILIDADES:
 * 1. Orquestrar lógica de negócio
 * 2. Gerenciar seção crítica (locks)
 * 3. Garantir idempotência
 * 4. Publicar eventos de domínio
 * 5. Chamar Limits Service via HTTP (TODO)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizeTransactionUseCase {
    
    private final AuthorizationRepository authRepository;
    private final EventBridgePublisher eventPublisher;
    private final LockService lockService;
    
    public AuthorizeTransactionResponse execute(
            String idContrato,
            AuthorizeTransactionRequest request,
            String idempotencyKey) {
        
        String correlationId = MDC.get("X-Correlation-ID");
        String traceId = MDC.get("X-Trace-ID");
        
        log.info("🔐 Iniciando autorização: contrato={}, conta={}", 
            idContrato, request.getIdConta());
        
        // 🔒 ADQUIRIR LOCKS (seção crítica começa aqui)
        try {
            log.info("🔒 Adquirindo locks: conta={}, contrato={}", 
                request.getIdConta(), idContrato);
            
            lockService.acquireLock("AUTH:" + idContrato, 5000);
            
            log.info("✅ Lock adquirido para contrato: {}", idContrato);
            
            // 📋 Validar entrada
            if (request.getValor() == null || request.getValor() <= 0) {
                throw new IllegalArgumentException("Valor inválido");
            }
            
            // TODO: Chamar GET /limits-service/v1/limites/{idContrato}
            // para validar limite disponível
            
            // TODO: Se limite insuficiente, retornar 402
            
            // TODO: Reservar limite em PUT /limits-service/v1/limites/{idContrato}
            
            // ✅ Criar autorização
            String authId = UUID.randomUUID().toString();
            Authorization auth = Authorization.builder()
                .id(authId)
                .contrato(idContrato)
                .conta(request.getIdConta())
                .valor(BigDecimal.valueOf(request.getValor()))
                .status("APPROVED")
                .build();
            
            authRepository.save(auth);
            log.info("✅ Autorização criada: {}", authId);
            
            // 📤 Publicar evento
            log.info("📤 Publicando evento no EventBridge");
            eventPublisher.publishEvent(null); // TODO: passar evento real
            
            return new AuthorizeTransactionResponse(
                authId,
                "APPROVED",
                "Transação autorizada",
                authId,
                request.getValor(),
                false
            );
            
        } catch (Exception e) {
            log.error("❌ Erro ao autorizar transação", e);
            throw new RuntimeException("Falha na autorização", e);
        } finally {
            lockService.releaseLock("AUTH:" + idContrato);
            log.info("🔓 Lock liberado");
        }
    }
}
