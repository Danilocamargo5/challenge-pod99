package com.pod99.authorization.application;

import com.pod99.authorization.domain.Authorization;
import com.pod99.authorization.domain.AuthorizationRepository;
import com.pod99.authorization.domain.TransacaoAutorizadaEvent;
import com.pod99.common.exception.InsufficientLimitException;
import com.pod99.common.exception.LockAcquisitionException;
import com.pod99.config.EventBridgePublisher;
import com.pod99.config.LockService;
import com.pod99.limits.domain.Limit;
import com.pod99.limits.domain.LimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Use Case para Autorizar Transações
 * 
 * RESPONSABILIDADES:
 * 1. Orquestrar lógica de negócio
 * 2. Gerenciar seção crítica (locks) ← IMPORTANTE
 * 3. Garantir idempotência
 * 4. Publicar eventos de domínio
 * 
 * O Lock está AQUI porque:
 * - Faz parte da lógica de negócio (concorrência)
 * - Toda entrada pro UseCase é protegida (HTTP, Lambda, message broker)
 * - Não é detalhe de HTTP (seria no Controller)
 * - É detalhe de implementação da autorização sincronizada
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizeTransactionUseCase {
    
    private final AuthorizationRepository authRepository;
    private final LimitRepository limitRepository;
    private final EventBridgePublisher eventPublisher;
    private final LockService lockService;  // ← Injetado aqui
    
    /**
     * Executa autorização dentro de seção crítica protegida por locks
     * 
     * @param idContrato ID do contrato
     * @param request Dados da transação
     * @param idempotencyKey Chave de idempotência
     * @return Resposta da autorização
     * @throws LockAcquisitionException se não consegue adquirir locks
     * @throws InsufficientLimitException se limite insuficiente
     */
    public AuthorizeTransactionResponse execute(
            String idContrato,
            AuthorizeTransactionRequest request,
            String idempotencyKey) {
        
        String correlationId = MDC.get("X-Correlation-ID");
        String traceId = MDC.get("X-Trace-ID");
        
        log.info("🔐 Iniciando autorização com locks: contrato={}, conta={}", 
            idContrato, request.getIdConta());
        
        // 🔒 ADQUIRIR LOCKS (seção crítica começa aqui)
        List<String> acquiredLocks = null;
        try {
            log.info("🔒 Adquirindo locks: conta={}, contrato={}", 
                request.getIdConta(), idContrato);
            
            acquiredLocks = lockService.acquireTransactionLocks(
                request.getIdConta(),  // Lock 1: por conta
                idContrato);           // Lock 2: por contrato
            
            log.info("✅ Locks adquiridos: {}", acquiredLocks);
            
            // 📌 SEÇÃO CRÍTICA PROTEGIDA
            return executeProtected(idContrato, request, idempotencyKey, correlationId, traceId);
            
        } finally {
            // 🔓 LIBERAR LOCKS (SEMPRE - seção crítica finalizada)
            if (acquiredLocks != null && !acquiredLocks.isEmpty()) {
                try {
                    log.info("🔓 Liberando locks: {}", acquiredLocks);
                    lockService.releaseLocks(acquiredLocks);
                    log.info("✅ Locks liberados");
                } catch (Exception e) {
                    log.error("⚠️ Erro ao liberar locks (TTL fará cleanup)", e);
                    // Não propaga (locks têm TTL 30s auto-cleanup)
                }
            }
        }
    }
    
    /**
     * Lógica protegida pela seção crítica (dentro de locks)
     */
    private AuthorizeTransactionResponse executeProtected(
            String idContrato,
            AuthorizeTransactionRequest request,
            String idempotencyKey,
            String correlationId,
            String traceId) {
        
        // 1. Verificar idempotência
        var existente = authRepository.findByIdempotencyKey(idempotencyKey);
        if (existente.isPresent()) {
            log.info("✅ Autorização já processada (idempotência): {}", idempotencyKey);
            // 🔄 Retorna 200 OK em vez de 201 Created
            return AuthorizeTransactionResponse.fromRepetition(existente.get());
        }
        
        // 2. Carregar limite
        Limit limit = limitRepository.findByContractId(idContrato)
            .orElseThrow(() -> new IllegalArgumentException("Contrato não encontrado"));
        
        // 3. Validar limite
        if (limit.getDisponivel().compareTo(request.getValor()) < 0) {
            log.warn("❌ Limite insuficiente: necessário={}, disponível={}", 
                request.getValor(), limit.getDisponivel());
            throw new InsufficientLimitException("Limite insuficiente");
        }
        
        // 4. Reservar limite (atomicamente, dentro de lock)
        limit.reserve(request.getValor());
        limitRepository.update(limit);
        
        // 5. Criar autorização
        Authorization auth = Authorization.criar(
            idContrato,
            request.getIdConta(),
            request.getValor(),
            request.getMoeda(),
            request.getTipoOperacao(),
            request.getIdEstabelecimento(),  // Novo campo
            request.getMetadata(),           // Novo campo
            limit.getDisponivel(),
            correlationId
        );
        
        // 6. Salvar autorização
        authRepository.save(auth);
        
        // 7. Publicar evento (CloudEvents validado)
        TransacaoAutorizadaEvent event = TransacaoAutorizadaEvent.from(auth, traceId);
        eventPublisher.publish(event);
        
        log.info("✅ Autorização aprovada: id={}, saldo={}", 
            auth.getIdAutorizacao(), limit.getDisponivel());
        
        return AuthorizeTransactionResponse.from(auth);
    }
}
