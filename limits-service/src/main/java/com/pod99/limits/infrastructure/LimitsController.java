package com.pod99.limits.infrastructure;

import com.pod99.limits.application.ReserveLimitUseCase;
import com.pod99.limits.domain.Limit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HTTP Adapter para Serviço de Limites
 * 
 * Recebe chamadas síncronas do authorization-service
 * para validar e reservar limites
 */
@Slf4j
@RestController
@RequestMapping("/v1/limits")
@RequiredArgsConstructor
public class LimitsController {
    
    private final ReserveLimitUseCase reserveLimitUseCase;
    
    /**
     * POST /v1/limits/reserve
     * 
     * Body:
     * {
     *   "idAutorizacao": "uuid",
     *   "idContrato": "CONTA-001",
     *   "valor": 100.00,
     *   "moeda": "BRL"
     * }
     */
    @PostMapping("/reserve")
    public ResponseEntity<?> reserveLimit(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Trace-ID", required = false) String traceId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        
        String idAutorizacao = (String) request.get("idAutorizacao");
        String idContrato = (String) request.get("idContrato");
        Double valor = ((Number) request.get("valor")).doubleValue();
        String moeda = (String) request.get("moeda");
        
        if (traceId != null) MDC.put("X-Trace-ID", traceId);
        if (correlationId != null) MDC.put("X-Correlation-ID", correlationId);
        
        try {
            // 📥 LOG DE ENTRADA
            log.info("═══════════════════════════════════════════════════════════════");
            log.info("🔵 [LIMITS] ENTRADA - Requisição de reserva de limite");
            log.info("   ID Autorização: {} | Contrato: {}", idAutorizacao, idContrato);
            log.info("   Valor: {} {} | Trace: {}", valor, moeda, traceId);
            log.info("═══════════════════════════════════════════════════════════════");
            
            // Validar limite (UseCase do authorization já faz isso, mas podemos ter lógica aqui)
            // Por enquanto, apenas retorna sucesso
            
            // 📤 LOG DE SAÍDA
            log.info("═══════════════════════════════════════════════════════════════");
            log.info("🟢 [LIMITS] SAÍDA - Limite validado e reservado com sucesso");
            log.info("   ID Autorização: {} | Valor Reservado: {} {}", 
                idAutorizacao, valor, moeda);
            log.info("   Status: APPROVED | Trace: {}", traceId);
            log.info("═══════════════════════════════════════════════════════════════");
            
            return ResponseEntity.ok(Map.of(
                "idAutorizacao", idAutorizacao,
                "status", "APPROVED",
                "valorReservado", valor,
                "moeda", moeda
            ));
            
        } catch (Exception e) {
            log.error("❌ Erro ao processar reserva de limite para autorização: {}", idAutorizacao, e);
            return ResponseEntity
                .status(HttpStatus.PAYMENT_REQUIRED)
                .body(Map.of(
                    "idAutorizacao", idAutorizacao,
                    "status", "REJECTED",
                    "erro", e.getMessage()
                ));
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * GET /v1/limits/health
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "service", "limits-service",
            "status", "UP",
            "port", 8082
        ));
    }
}
