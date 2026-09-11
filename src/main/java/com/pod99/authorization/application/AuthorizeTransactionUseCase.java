package com.pod99.authorization.application;

import com.pod99.authorization.domain.Authorization;
import com.pod99.authorization.domain.AuthorizationRepository;
import com.pod99.authorization.domain.TransacaoAutorizadaEvent;
import com.pod99.common.exception.InsufficientLimitException;
import com.pod99.limits.domain.Limit;
import com.pod99.limits.domain.LimitRepository;
import com.pod99.config.EventBridgePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizeTransactionUseCase {
    
    private final AuthorizationRepository authRepository;
    private final LimitRepository limitRepository;
    private final EventBridgePublisher eventPublisher;
    
    public AuthorizeTransactionResponse execute(
            String idContrato,
            AuthorizeTransactionRequest request,
            String idempotencyKey) {
        
        String correlationId = MDC.get("X-Correlation-ID");
        String traceId = MDC.get("X-Trace-ID");
        
        log.info("🔐 Iniciando autorização: contrato={}, valor={}, key={}", 
            idContrato, request.getValor(), idempotencyKey);
        
        // Verifica idempotência
        var existente = authRepository.findByIdempotencyKey(idempotencyKey);
        if (existente.isPresent()) {
            log.info("✅ Autorização já processada (idempotência): {}", idempotencyKey);
            return AuthorizeTransactionResponse.from(existente.get());
        }
        
        // Carrega limite
        Limit limit = limitRepository.findByContractId(idContrato)
            .orElseThrow(() -> new IllegalArgumentException("Contrato não encontrado"));
        
        // Valida limite
        if (limit.getDisponivel().compareTo(request.getValor()) < 0) {
            log.warn("❌ Limite insuficiente: necessário={}, disponível={}", 
                request.getValor(), limit.getDisponivel());
            throw new InsufficientLimitException("Limite insuficiente");
        }
        
        // Reserva limite
        limit.reserve(request.getValor());
        limitRepository.update(limit);
        
        // Cria autorização
        Authorization auth = Authorization.criar(
            idContrato,
            request.getIdConta(),
            request.getValor(),
            request.getMoeda(),
            request.getTipoOperacao(),
            limit.getDisponivel(),
            correlationId
        );
        
        authRepository.save(auth);
        
        // Publica evento
        TransacaoAutorizadaEvent event = TransacaoAutorizadaEvent.from(auth, traceId);
        eventPublisher.publish(event);
        
        log.info("✅ Autorização aprovada: id={}", auth.getIdAutorizacao());
        return AuthorizeTransactionResponse.from(auth);
    }
}
