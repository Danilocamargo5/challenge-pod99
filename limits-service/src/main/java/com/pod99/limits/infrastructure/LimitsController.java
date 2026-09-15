package com.pod99.limits.infrastructure;

import com.pod99.limits.application.ReserveLimitUseCase;
import com.pod99.common.exception.InsufficientLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/limites")
@RequiredArgsConstructor
public class LimitsController {

    private final ReserveLimitUseCase reserveLimitUseCase;

    @PostMapping("/{idContrato}/reservar")
    public ResponseEntity<?> reservarLimite(
            @PathVariable String idContrato,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Trace-ID", required = false) String traceId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

        BigDecimal valor = new BigDecimal(request.get("valor").toString());
        String idempotencyKey = (String) request.get("idempotencyKey");

        if (traceId != null) {
            MDC.put("X-Trace-ID", traceId);
        }

        if (correlationId != null) {
            MDC.put("X-Correlation-ID", correlationId);
        }

        try {
            log.info(
                "🔵 Validando limite: contrato={}, valor={}, idempotencyKey={}",
                idContrato,
                valor,
                idempotencyKey
            );

            reserveLimitUseCase.reserve(idContrato, valor);

            log.info(
                "🟢 Limite reservado: contrato={}, valor={}",
                idContrato,
                valor
            );

            return ResponseEntity.ok(Map.of(
                "id", idContrato,
                "status", "APPROVED",
                "reservado", valor
            ));

        } catch (InsufficientLimitException e) {

            log.warn("❌ Limite insuficiente: contrato={}", idContrato);

            return ResponseEntity
                .status(HttpStatus.PAYMENT_REQUIRED)
                .body(Map.of(
                    "id", idContrato,
                    "status", "REJECTED",
                    "erro", e.getMessage()
                ));

        } catch (IllegalArgumentException e) {

            log.warn(
                "❌ Reserva rejeitada: contrato={}, motivo={}",
                idContrato,
                e.getMessage()
            );

            if ("Limite insuficiente".equals(e.getMessage())) {
                return ResponseEntity
                    .status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of(
                        "id", idContrato,
                        "status", "REJECTED",
                        "erro", e.getMessage()
                    ));
            }

            return ResponseEntity
                .unprocessableEntity()
                .body(Map.of(
                    "id", idContrato,
                    "status", "REJECTED",
                    "erro", e.getMessage()
                ));

        } finally {
            MDC.clear();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "service", "limits-service",
            "status", "UP",
            "port", 8082
        ));
    }
}