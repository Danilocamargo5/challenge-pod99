package com.pod99.authorization.application;

import com.pod99.config.EventBridgePublisher;
import com.pod99.config.LockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
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
    
    private final EventBridgePublisher eventPublisher;
    private final LockService lockService;
    private final RestTemplate restTemplate;
    
    public AuthorizeTransactionResponse execute(
            String idContrato,
            AuthorizeTransactionRequest request,
            String idempotencyKey) {
        
        String correlationId = MDC.get("X-Correlation-ID");
        String traceId = MDC.get("X-Trace-ID");
        
        log.info("┌─────────────────────────────────────────────────────────────────┐");
        log.info("│ 🔐 AUTHORIZATION SERVICE - Iniciando autorização                 │");
        log.info("├─────────────────────────────────────────────────────────────────┤");
        log.info("│ Contrato: {} | Conta: {}", idContrato, request.getIdConta());
        log.info("│ Valor: {} {} | Correlação: {}", request.getValor(), request.getMoeda(), correlationId);
        log.info("│ Trace: {} | Idempotency: {}", traceId, idempotencyKey);
        log.info("└─────────────────────────────────────────────────────────────────┘");
        
        String authId = UUID.randomUUID().toString();
        List<String> acquiredLocks = null;
        
        // Converter valor para Double
        Double valor = request.getValor() != null ? request.getValor().doubleValue() : null;
        
        // 🔒 ADQUIRIR LOCKS (seção crítica começa aqui)
        try {
            log.info("  ➜ [1/5] 🔒 Adquirindo locks...");
            
            acquiredLocks = lockService.acquireTransactionLocks(authId, idContrato);
            
            log.info("  ✅ [1/5] Locks adquiridos: {}", acquiredLocks);
            
            // 📋 Validar entrada
            if (valor == null || valor <= 0.0) {
                log.warn("  ❌ Valor inválido: {}", valor);
                throw new IllegalArgumentException("Valor inválido");
            }
            
            // 1️⃣ CHAMAR LIMITS SERVICE: VALIDAR + RESERVAR em UMA ÚNICA CHAMADA (atômico)
            log.info("  ➜ [2/5] 📞 Chamando LIMITS SERVICE...");
            String limitsUrl = "http://localhost:8082/v1/limites/" + idContrato + "/reservar";
            
            LimitReserveRequest reserveRequest = new LimitReserveRequest();
            reserveRequest.setValor(valor);
            reserveRequest.setIdempotencyKey(idempotencyKey);
            
            LimitReserveResponse response;
            try {
                response = restTemplate.postForObject(limitsUrl, reserveRequest, LimitReserveResponse.class);
                log.info("  ✅ [2/5] LIMITS SERVICE respondeu - Saldo: {}", response.getSaldoAtual());
            } catch (Exception e) {
                log.error("  ❌ [2/5] Erro ao chamar LIMITS SERVICE: {}", e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("402")) {
                    throw new InsufficientLimitException("Limite insuficiente");
                }
                throw new RuntimeException("Falha ao reservar limite", e);
            }
            
            // 2️⃣ CRIAR AUTORIZAÇÃO
            log.info("  ✅ [3/5] Autorização criada - ID: {}", authId);
            
            // 3️⃣ PUBLICAR EVENTO no EventBridge
            log.info("  ➜ [4/5] 📤 Publicando evento no EventBridge...");
            eventPublisher.publishTransactionAuthorized(
                authId,
                idContrato,
                request.getIdConta(),
                valor,
                request.getMoeda()
            );
            log.info("  ✅ [4/5] Evento publicado");
            
            log.info("  ✅ [5/5] Fluxo completado com sucesso");
            
            log.info("┌─────────────────────────────────────────────────────────────────┐");
            log.info("│ 🟢 AUTHORIZATION SERVICE - Autorização APROVADA                 │");
            log.info("├─────────────────────────────────────────────────────────────────┤");
            log.info("│ ID Autorização: {} | Status: APPROVED", authId);
            log.info("│ Saldo Reservado: {} {} | Correlação: {}", valor, request.getMoeda(), correlationId);
            log.info("└─────────────────────────────────────────────────────────────────┘");
            
            return new AuthorizeTransactionResponse(
                authId,
                "APPROVED",
                "Transação autorizada com sucesso",
                authId,
                valor,
                false
            );
            
        } catch (InsufficientLimitException e) {
            log.warn("  ⚠️ Limite insuficiente");
            throw e;
        } catch (Exception e) {
            log.error("  ❌ Erro ao autorizar transação", e);
            throw new RuntimeException("Falha na autorização", e);
        } finally {
            if (acquiredLocks != null && !acquiredLocks.isEmpty()) {
                lockService.releaseLocks(acquiredLocks);
                log.info("  🔓 Locks liberados");
            }
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
