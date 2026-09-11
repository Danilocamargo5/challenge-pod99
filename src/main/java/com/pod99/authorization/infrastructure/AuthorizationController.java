package com.pod99.authorization.infrastructure;

import com.pod99.authorization.application.AuthorizeTransactionRequest;
import com.pod99.authorization.application.AuthorizeTransactionResponse;
import com.pod99.authorization.application.AuthorizeTransactionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/contratos")
@RequiredArgsConstructor
public class AuthorizationController {
    
    private final AuthorizeTransactionUseCase authorizeUseCase;
    
    @PostMapping("/{idContrato}/autorizacoes")
    public ResponseEntity<?> authorize(
            @PathVariable String idContrato,
            @RequestBody AuthorizeTransactionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        
        String correlationId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        
        MDC.put("X-Correlation-ID", correlationId);
        MDC.put("X-Trace-ID", traceId);
        
        log.info("POST /v1/contratos/{}/autorizacoes | key={}", idContrato, idempotencyKey);
        
        try {
            AuthorizeTransactionResponse response = authorizeUseCase.execute(
                idContrato, request, idempotencyKey);
            
            return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
                
        } catch (IllegalArgumentException e) {
            log.warn("Validação: {}", e.getMessage());
            return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                    "error_code", "VALIDATION_ERROR",
                    "message", e.getMessage()
                ));
                
        } catch (com.pod99.common.exception.InsufficientLimitException e) {
            log.warn("Limite insuficiente: {}", e.getMessage());
            return ResponseEntity
                .status(HttpStatus.PAYMENT_REQUIRED)
                .body(Map.of(
                    "error_code", "INSUFFICIENT_LIMIT",
                    "message", e.getMessage()
                ));
                
        } catch (Exception e) {
            log.error("Erro ao autorizar", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error_code", "INTERNAL_ERROR",
                    "message", "Erro interno ao processar autorização"
                ));
        } finally {
            MDC.clear();
        }
    }
}
