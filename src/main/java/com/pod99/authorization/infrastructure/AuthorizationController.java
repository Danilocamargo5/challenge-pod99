package com.pod99.authorization.infrastructure;

import com.pod99.authorization.application.AuthorizeTransactionRequest;
import com.pod99.authorization.application.AuthorizeTransactionResponse;
import com.pod99.authorization.application.AuthorizeTransactionUseCase;
import com.pod99.common.exception.InsufficientLimitException;
import com.pod99.common.exception.LockAcquisitionException;
import com.pod99.config.LockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP Adapter para Autorização de Transações
 * 
 * Fluxo:
 * 1. Valida Idempotency-Key header
 * 2. Adquire locks distribuídos (LockService)
 * 3. Executa lógica de autorização (seção crítica)
 * 4. Libera locks (LIFO, sempre)
 * 5. Retorna HTTP 201 ou erro apropriado
 */
@Slf4j
@RestController
@RequestMapping("/v1/contratos")
@RequiredArgsConstructor
public class AuthorizationController {
    
    private final AuthorizeTransactionUseCase authorizeUseCase;
    private final LockService lockService;
    
    @PostMapping("/{idContrato}/autorizacoes")
    public ResponseEntity<?> authorize(
            @PathVariable String idContrato,
            @RequestBody AuthorizeTransactionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        
        String correlationId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        
        MDC.put("X-Correlation-ID", correlationId);
        MDC.put("X-Trace-ID", traceId);
        
        log.info("🔐 Iniciando autorização: contrato={}, conta={}, key={}", 
            idContrato, request.getIdConta(), idempotencyKey);
        
        List<String> acquiredLocks = null;
        
        try {
            // 1️⃣ ADQUIRIR LOCKS (seção crítica)
            log.info("🔒 Adquirindo locks: conta={}, contrato={}", 
                request.getIdConta(), idContrato);
            
            acquiredLocks = lockService.acquireTransactionLocks(
                request.getIdConta(),  // Lock 1: por conta
                idContrato);           // Lock 2: por contrato
            
            log.info("✅ Locks adquiridos: {}", acquiredLocks);
            
            // 2️⃣ EXECUTAR USE CASE (dentro de seção crítica)
            AuthorizeTransactionResponse response = authorizeUseCase.execute(
                idContrato, request, idempotencyKey);
            
            log.info("✅ Autorização aprovada: id={}", response.getIdAutorizacao());
            
            return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
                
        } catch (LockAcquisitionException e) {
            // 3️⃣ CONFLITO: Não conseguiu adquirir lock (race condition)
            log.warn("⚠️ Conflito de concorrência (lock): {}", e.getMessage());
            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                    "error_code", "CONFLICT",
                    "message", "Conflito de concorrência: não foi possível adquirir lock",
                    "correlation_id", MDC.get("X-Correlation-ID")
                ));
                
        } catch (InsufficientLimitException e) {
            // 4️⃣ LIMITE INSUFICIENTE
            log.warn("❌ Limite insuficiente: {}", e.getMessage());
            return ResponseEntity
                .status(HttpStatus.PAYMENT_REQUIRED)
                .body(Map.of(
                    "error_code", "INSUFFICIENT_LIMIT",
                    "message", e.getMessage(),
                    "correlation_id", MDC.get("X-Correlation-ID")
                ));
                
        } catch (IllegalArgumentException e) {
            // 5️⃣ VALIDAÇÃO FALHOU
            log.warn("⚠️ Validação falhou: {}", e.getMessage());
            return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                    "error_code", "VALIDATION_ERROR",
                    "message", e.getMessage(),
                    "correlation_id", MDC.get("X-Correlation-ID")
                ));
                
        } catch (Exception e) {
            // 6️⃣ ERRO INTERNO
            log.error("❌ Erro ao autorizar transação", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error_code", "INTERNAL_ERROR",
                    "message", "Erro interno ao processar autorização",
                    "correlation_id", MDC.get("X-Correlation-ID")
                ));
                
        } finally {
            // 7️⃣ LIBERAR LOCKS (SEMPRE - seção crítica finalizada)
            if (acquiredLocks != null && !acquiredLocks.isEmpty()) {
                try {
                    log.info("🔓 Liberando locks: {}", acquiredLocks);
                    lockService.releaseLocks(acquiredLocks);
                    log.info("✅ Locks liberados");
                } catch (Exception e) {
                    log.error("⚠️ Erro ao liberar locks", e);
                    // Não propaga erro (locks têm TTL 30s)
                }
            }
            MDC.clear();
        }
    }
}
