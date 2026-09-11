package com.pod99.authorization.infrastructure;

import com.pod99.authorization.application.AuthorizeTransactionRequest;
import com.pod99.authorization.application.AuthorizeTransactionResponse;
import com.pod99.authorization.application.AuthorizeTransactionUseCase;
import com.pod99.common.exception.InsufficientLimitException;
import com.pod99.common.exception.LockAcquisitionException;
import com.pod99.common.exception.ProblemDetail;
import com.pod99.common.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    
    
    @PostMapping("/{idContrato}/autorizacoes")
    public ResponseEntity<?> authorize(
            @PathVariable String idContrato,
            @RequestBody AuthorizeTransactionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        
        String correlationId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        String instance = String.format("/v1/contratos/%s/autorizacoes", idContrato);
        
        MDC.put("X-Correlation-ID", correlationId);
        MDC.put("X-Trace-ID", traceId);
        
        log.info("📡 POST /v1/contratos/{}/autorizacoes | key={} | conta={}", 
            idContrato, idempotencyKey, request.getIdConta());
        
        try {
            AuthorizeTransactionResponse response = authorizeUseCase.execute(
                idContrato, request, idempotencyKey);
            
            log.info("✅ Autorização aprovada: {}", response.getIdAutorizacao());
            
            // ℹ️ Status 200 se for repetição (idempotência), 201 se for novo
            HttpStatus status = response.isRepetition() ? HttpStatus.OK : HttpStatus.CREATED;
            
            return ResponseEntity
                .status(status)
                .body(response);
                
        } catch (RateLimitExceededException e) {
            log.warn("🚦 Rate limit excedido para conta {}: {}", e.getAccountId(), e.getMessage());
            return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
                .body(ProblemDetail.rateLimitExceeded(
                    String.format("Rate limit excedido: máximo %d requisições por segundo", e.getLimitPerSecond()),
                    instance,
                    correlationId
                ));
        
        } catch (LockAcquisitionException e) {
            log.warn("⚠️ Conflito: {}", e.getMessage());
            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ProblemDetail.conflict(
                    "Conflito de concorrência ao processar autorização",
                    instance,
                    correlationId
                ));
                
        } catch (InsufficientLimitException e) {
            log.warn("❌ Limite insuficiente: {}", e.getMessage());
            return ResponseEntity
                .status(HttpStatus.PAYMENT_REQUIRED)
                .body(ProblemDetail.insufficientLimit(
                    e.getMessage(),
                    instance,
                    correlationId
                ));
                
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Validação: {}", e.getMessage());
            return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ProblemDetail.validationError(
                    e.getMessage(),
                    instance,
                    correlationId
                ));
                
        } catch (Exception e) {
            log.error("❌ Erro ao processar autorização", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ProblemDetail.of(
                    "https://api.pod99.com/errors/internal-error",
                    "Internal Server Error",
                    500,
                    "Erro interno ao processar autorização",
                    instance,
                    correlationId
                ));
                
        } finally {
            MDC.clear();
        }
    }
}
