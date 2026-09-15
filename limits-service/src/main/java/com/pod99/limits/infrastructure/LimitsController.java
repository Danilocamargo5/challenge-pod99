package com.pod99.limits.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller para Serviço de Limites
 * 
 * Recebe chamadas HTTP síncronas do authorization-service
 * para validar e reservar limites
 */
@Slf4j
@RestController
@RequestMapping("/v1/limites")
public class LimitsController {
    
    /**
     * POST /v1/limites/{idContrato}/reservar
     * 
     * Chamada HTTP síncrona de authorization-service (8080) → limits-service (8082)
     * 
     * Body:
     * {
     *   "valor": 100.00,
     *   "idempotencyKey": "uuid"
     * }
     */
    @PostMapping("/{idContrato}/reservar")
    public ResponseEntity<?> reservarLimite(
            @PathVariable String idContrato,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Trace-ID", required = false) String traceId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        
        Double valor = ((Number) request.get("valor")).doubleValue();
        String idempotencyKey = (String) request.get("idempotencyKey");
        
        if (traceId != null) MDC.put("X-Trace-ID", traceId);
        if (correlationId != null) MDC.put("X-Correlation-ID", correlationId);
        
        try {
            // 📥 LOG DE ENTRADA
            log.info("┌─────────────────────────────────────────────────────────────────┐");
            log.info("│ 🔵 AUTHORIZATION → LIMITS SERVICE - Validar + Reservar           │");
            log.info("├─────────────────────────────────────────────────────────────────┤");
            log.info("│ Contrato: {} | Valor: {}", idContrato, valor);
            log.info("│ Idempotency-Key: {} | Trace: {}", idempotencyKey, traceId);
            log.info("└─────────────────────────────────────────────────────────────────┘");
            
            // TODO: Chamar repository para validar limite real
            // Por enquanto, apenas retorna sucesso
            Double saldoAtual = 10000.0 - valor; // Mock
            
            // 📤 LOG DE SAÍDA
            log.info("┌─────────────────────────────────────────────────────────────────┐");
            log.info("│ 🟢 LIMITS SERVICE - Limite validado e reservado com sucesso      │");
            log.info("├─────────────────────────────────────────────────────────────────┤");
            log.info("│ Contrato: {} | Valor Reservado: {}", 
                idContrato, valor);
            log.info("│ Status: APPROVED | Saldo Atual: {} | Trace: {}", saldoAtual, traceId);
            log.info("└─────────────────────────────────────────────────────────────────┘");
            
            return ResponseEntity.ok(Map.of(
                "id", idContrato,
                "saldoAnterior", 10000.0,
                "saldoAtual", saldoAtual,
                "reservado", valor
            ));
            
        } catch (Exception e) {
            log.error("❌ Erro ao processar reserva de limite para contrato: {}", idContrato, e);
            return ResponseEntity
                .status(HttpStatus.PAYMENT_REQUIRED)
                .body(Map.of(
                    "id", idContrato,
                    "status", "REJECTED",
                    "erro", e.getMessage()
                ));
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * GET /v1/limits/health
     * Verificar saúde do serviço
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        log.info("🔵 [LIMITS] HEALTH CHECK");
        return ResponseEntity.ok(Map.of(
            "service", "limits-service",
            "status", "UP",
            "port", 8082
        ));
    }
}
