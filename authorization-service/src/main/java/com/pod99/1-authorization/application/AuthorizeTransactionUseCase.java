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
 * 2. Chamar POST /limits-service/v1/limites/{idContrato}/reservar (VALIDAR + RESERVAR em UMA chamada)
 * 3. Se OK: Criar autorização
 * 4. Se OK: Publicar evento no EventBridge
 * 5. Liberar locks
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
            
            // 1️⃣ CHAMAR LIMITS SERVICE: VALIDAR + RESERVAR em UMA ÚNICA CHAMADA
            log.info("📞 Chamando POST /limits-service/v1/limites/{}/reservar (validar + reservar)", idContrato);
            String limitsUrl = "http://localhost:8082/v1/limites/" + idContrato + "/reservar";
            
            LimitReserveRequest reserveRequest = new LimitReserveRequest();
            reserveRequest.setValor(request.getValor());
            reserveRequest.setIdempotencyKey(idempotencyKey);
            
            LimitReserveResponse response;
            try {
                response = restTemplate.postForObject(limitsUrl, reserveRequest, LimitReserveResponse.class);
                log.info("✅ Limite validado e reservado atomicamente: valor={}", request.getValor());
            } catch (Exception e) {
                log.error("❌ Erro ao validar/reservar limite", e);
                if (e.getMessage().contains("402")) {
                    throw new InsufficientLimitException("Limite insuficiente");
                }
                throw new RuntimeException("Falha ao reservar limite", e);
            }
            
            // 2️⃣ CRIAR AUTORIZAÇÃO
            String authId = UUID.randomUUID().toString();
            Authorization auth = Authorization.builder()
                .id(authId)
                .contrato(idContrato)
                .conta(request.getIdConta())
                .valor(BigDecimal.valueOf(request.getValor()))
                .status("APPROVED")
                .correlationId(correlationId)
                .traceId(traceId)
                .idempotencyKey(idempotencyKey)
                .build();
            
            authRepository.save(auth);
            log.info("✅ Autorização criada: {}", authId);
            
            // 3️⃣ PUBLICAR EVENTO
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
    
    // Request/Response para limits-service
    public static class LimitReserveRequest {
        private Double valor;
        private String idempotencyKey;
        
        public Double getValor() { return valor; }
        public void setValor(Double valor) { this.valor = valor; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    }
    
    public static class LimitReserveResponse {
        private String id;
        private Double saldoAnterior;
        private Double saldoAtual;
        private Double reservado;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public Double getSaldoAnterior() { return saldoAnterior; }
        public void setSaldoAnterior(Double saldoAnterior) { this.saldoAnterior = saldoAnterior; }
        public Double getSaldoAtual() { return saldoAtual; }
        public void setSaldoAtual(Double saldoAtual) { this.saldoAtual = saldoAtual; }
        public Double getReservado() { return reservado; }
        public void setReservado(Double reservado) { this.reservado = reservado; }
    }
    
    public static class InsufficientLimitException extends RuntimeException {
        public InsufficientLimitException(String message) {
            super(message);
        }
    }
}
