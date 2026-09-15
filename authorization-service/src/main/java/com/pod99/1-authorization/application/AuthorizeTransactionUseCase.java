package com.pod99.authorization.application;

import com.pod99.authorization.domain.Authorization;
import com.pod99.authorization.domain.AuthorizationRepository;
import com.pod99.config.EventBridgePublisher;
import com.pod99.config.LockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Use Case para Autorizar Transações
 * 
 * FLUXO:
 * 1. Adquirir locks (seção crítica)
 * 2. Chamar GET /limits-service/v1/limites/{idContrato}
 * 3. Validar se valor <= limite.disponivel
 * 4. Se OK: Chamar PUT /limits-service/v1/limites/{idContrato} para reservar
 * 5. Se OK: Criar autorização
 * 6. Se OK: Publicar evento no EventBridge
 * 7. Liberar locks
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizeTransactionUseCase {
    
    private final AuthorizationRepository authRepository;
    private final EventBridgePublisher eventPublisher;
    private final LockService lockService;
    private final RestTemplate restTemplate;
    
    public AuthorizeTransactionResponse execute(
            String idContrato,
            AuthorizeTransactionRequest request,
            String idempotencyKey) {
        
        String correlationId = MDC.get("X-Correlation-ID");
        String traceId = MDC.get("X-Trace-ID");
        
        log.info("🔐 Iniciando autorização: contrato={}, conta={}, valor={}", 
            idContrato, request.getIdConta(), request.getValor());
        
        // 🔒 ADQUIRIR LOCKS (seção crítica começa aqui)
        try {
            lockService.acquireLock("AUTH:" + idContrato, 5000);
            log.info("✅ Lock adquirido para contrato: {}", idContrato);
            
            // 📋 Validar entrada
            if (request.getValor() == null || request.getValor() <= 0) {
                log.warn("❌ Valor inválido: {}", request.getValor());
                throw new IllegalArgumentException("Valor inválido");
            }
            
            // 1️⃣ CHAMAR LIMITS SERVICE para pegar limite
            log.info("📞 Chamando GET /limits-service/v1/limites/{}", idContrato);
            String limitsUrl = "http://localhost:8082/v1/limites/" + idContrato;
            
            LimitDTO limit;
            try {
                limit = restTemplate.getForObject(limitsUrl, LimitDTO.class);
                log.info("✅ Limite obtido: disponível={}", limit.getDisponivel());
            } catch (Exception e) {
                log.error("❌ Erro ao chamar limits-service", e);
                throw new RuntimeException("Falha ao validar limite", e);
            }
            
            // 2️⃣ VALIDAR LIMITE
            if (limit.getDisponivel() < request.getValor()) {
                log.warn("❌ Limite insuficiente: necessário={}, disponível={}", 
                    request.getValor(), limit.getDisponivel());
                throw new InsufficientLimitException("Limite insuficiente");
            }
            
            // 3️⃣ RESERVAR LIMITE (chamar PUT em limits-service)
            log.info("📞 Chamando PUT /limits-service/v1/limites/{} para reservar", idContrato);
            LimitReserveRequest reserveRequest = new LimitReserveRequest();
            reserveRequest.setValor(request.getValor());
            
            try {
                restTemplate.put(limitsUrl, reserveRequest);
                log.info("✅ Limite reservado: valor={}", request.getValor());
            } catch (Exception e) {
                log.error("❌ Erro ao reservar limite", e);
                throw new RuntimeException("Falha ao reservar limite", e);
            }
            
            // 4️⃣ CRIAR AUTORIZAÇÃO
            String authId = UUID.randomUUID().toString();
            Authorization auth = Authorization.builder()
                .id(authId)
                .contrato(idContrato)
                .conta(request.getIdConta())
                .valor(BigDecimal.valueOf(request.getValor()))
                .status("APPROVED")
                .correlationId(correlationId)
                .traceId(traceId)
                .build();
            
            authRepository.save(auth);
            log.info("✅ Autorização criada: {}", authId);
            
            // 5️⃣ PUBLICAR EVENTO
            log.info("📤 Publicando evento no EventBridge");
            eventPublisher.publishEvent(null); // TODO: passar evento real
            
            return new AuthorizeTransactionResponse(
                authId,
                "APPROVED",
                "Transação autorizada com sucesso",
                authId,
                request.getValor(),
                false
            );
            
        } catch (InsufficientLimitException e) {
            log.warn("⚠️ Limite insuficiente");
            throw e;
        } catch (Exception e) {
            log.error("❌ Erro ao autorizar transação", e);
            throw new RuntimeException("Falha na autorização", e);
        } finally {
            lockService.releaseLock("AUTH:" + idContrato);
            log.info("🔓 Lock liberado");
        }
    }
    
    // DTOs para comunicação com limits-service
    public static class LimitDTO {
        private String id;
        private Double disponivel;
        
        public Double getDisponivel() { return disponivel; }
        public void setDisponivel(Double disponivel) { this.disponivel = disponivel; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }
    
    public static class LimitReserveRequest {
        private Double valor;
        
        public Double getValor() { return valor; }
        public void setValor(Double valor) { this.valor = valor; }
    }
    
    public static class InsufficientLimitException extends RuntimeException {
        public InsufficientLimitException(String message) {
            super(message);
        }
    }
}
