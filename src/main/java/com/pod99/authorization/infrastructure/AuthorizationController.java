package com.pod99.authorization.infrastructure;

import com.pod99.authorization.application.AuthorizeTransactionRequest;
import com.pod99.authorization.application.AuthorizeTransactionResponse;
import com.pod99.authorization.application.AuthorizeTransactionUseCase;
import com.pod99.common.exception.InsufficientLimitException;
import com.pod99.common.exception.LockAcquisitionException;
import com.pod99.common.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP Adapter para Autorização de Transações
 * 
 * RESPONSABILIDADES:
 * - Converter HTTP request → Objects
 * - Validar headers (Idempotency-Key)
 * - Chamar UseCase (que cuida dos locks)
 * - Converter Objects → HTTP response
 * - Mapear exceções → HTTP status codes
 * 
 * NÃO é responsável por:
 * - Gerenciar locks (UseCase cuida)
 * - Orquestrar lógica de negócio (UseCase cuida)
 * - Validar limites (UseCase/Domain cuida)
 */
@Slf4j
@RestController
@RequestMapping("/v1/contratos")
@RequiredArgsConstructor
public class AuthorizationController {
    
    private final AuthorizeTransactionUseCase authorizeUseCase;
    
    @PostMapping("/test")
    public ResponseEntity<?> test(@RequestHeader("Idempotency-Key") String key) {
        log.info("✅ Test endpoint funcionando! key={}", key);
        return ResponseEntity.ok(Map.of("status", "OK", "message", "Test endpoint funcionando"));
    }
    
    @PostMapping("/test2")
    public ResponseEntity<?> test2(@RequestBody AuthorizeTransactionRequest request) {
        log.info("✅ Test2 recebeu request: {}", request);
        return ResponseEntity.ok(request);
    }
    
    @PostMapping("/test3")
    public ResponseEntity<?> test3(@RequestBody Map<String, Object> request) {
        log.info("✅ Test3 recebeu Map: {}", request);
        return ResponseEntity.ok(request);
    }
    
    @PostMapping("/{idContrato}/autorizacoes")
    public ResponseEntity<?> authorize(
            @PathVariable String idContrato,
            @RequestBody AuthorizeTransactionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        
        log.info("🔍 DEBUG: Recebido request - idContrato={}, request={}, key={}", 
            idContrato, request, idempotencyKey);
        
        String correlationId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        
        MDC.put("X-Correlation-ID", correlationId);
        MDC.put("X-Trace-ID", traceId);
        
        log.info("📡 POST /v1/contratos/{}/autorizacoes | key={} | conta={}", 
            idContrato, idempotencyKey, request.getIdConta());
        
        try {
            // ✅ UseCase cuida de:
            // - Adquirir locks
            // - Validar limites
            // - Atualizar estado
            // - Publicar eventos
            // - Liberar locks
            AuthorizeTransactionResponse response = authorizeUseCase.execute(
                idContrato, request, idempotencyKey);
            
            log.info("✅ Autorização aprovada: {}", response.getIdAutorizacao());
            
            return ResponseEntity
                .status(HttpStatus.CREATED)  // 201
                .body(response);
                
        } catch (RateLimitExceededException e) {
            // 🚦 Rate limit excedido
            log.warn("🚦 Rate limit excedido para conta {}: {}", e.getAccountId(), e.getMessage());
            return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)  // 429
                .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
                .body(Map.of(
                    "error_code", "RATE_LIMIT_EXCEEDED",
                    "message", String.format(
                        "Rate limit excedido: máximo %d requisições por segundo",
                        e.getLimitPerSecond()
                    ),
                    "retry_after_seconds", e.getRetryAfterSeconds(),
                    "correlation_id", correlationId
                ));
        
        } catch (LockAcquisitionException e) {
            // 🔒 Conflito de concorrência (não conseguiu adquirir lock)
            log.warn("⚠️ Conflito: {}", e.getMessage());
            return ResponseEntity
                .status(HttpStatus.CONFLICT)  // 409
                .body(Map.of(
                    "error_code", "CONFLICT",
                    "message", "Conflito de concorrência ao processar autorização",
                    "correlation_id", correlationId
                ));
                
        } catch (InsufficientLimitException e) {
            // ❌ Limite insuficiente
            log.warn("❌ Limite insuficiente: {}", e.getMessage());
            return ResponseEntity
                .status(HttpStatus.PAYMENT_REQUIRED)  // 402
                .body(Map.of(
                    "error_code", "INSUFFICIENT_LIMIT",
                    "message", e.getMessage(),
                    "correlation_id", correlationId
                ));
                
        } catch (IllegalArgumentException e) {
            // ⚠️ Validação falhou
            log.warn("⚠️ Validação: {}", e.getMessage());
            return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)  // 422
                .body(Map.of(
                    "error_code", "VALIDATION_ERROR",
                    "message", e.getMessage(),
                    "correlation_id", correlationId
                ));
                
        } catch (Exception e) {
            // 💥 Erro interno
            log.error("❌ Erro ao processar autorização", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)  // 500
                .body(Map.of(
                    "error_code", "INTERNAL_ERROR",
                    "message", "Erro interno ao processar autorização",
                    "correlation_id", correlationId
                ));
                
        } finally {
            MDC.clear();
        }
    }
}
