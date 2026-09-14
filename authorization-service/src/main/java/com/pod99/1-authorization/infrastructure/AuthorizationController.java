package com.pod99.authorization.infrastructure;

import com.pod99.authorization.application.AuthorizeTransactionRequest;
import com.pod99.authorization.application.AuthorizeTransactionResponse;
import com.pod99.authorization.application.AuthorizeTransactionUseCase;
import com.pod99.common.exception.InsufficientLimitException;
import com.pod99.common.exception.LockAcquisitionException;
import com.pod99.common.exception.ProblemDetail;
import com.pod99.common.exception.RateLimitExceededException;
import com.pod99.common.infrastructure.RequestContext;
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
    private final JwtValidator jwtValidator;
    
    
    @PostMapping("/{idContrato}/autorizacoes")
    public ResponseEntity<?> authorize(
            @PathVariable String idContrato,
            @RequestBody AuthorizeTransactionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        
        // 🔐 Validar autenticação
        String accountId = RequestContext.requireAccountId();
        log.info("✅ Usuário autenticado: {}", accountId);
        
        // 📍 Correlation ID e Trace ID
        String correlationId = RequestContext.getCorrelationId();
        String traceId = RequestContext.getTraceId();
        
        String instance = String.format("/v1/contratos/%s/autorizacoes", idContrato);
        
        MDC.put("X-Correlation-ID", correlationId);
        MDC.put("X-Trace-ID", traceId);
        MDC.put("X-Account-Id", accountId);
        
        log.info("📡 POST /v1/contratos/{}/autorizacoes | key={} | conta={} | account={}", 
            idContrato, idempotencyKey, request.getIdConta(), accountId);
        
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
        
        } catch (RequestContext.UnauthorizedException e) {
            log.warn("🔐 Autorização: {}", e.getMessage());
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ProblemDetail.of(
                    "https://api.pod99.com/errors/unauthorized",
                    "Unauthorized",
                    401,
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
    
    
    /**
     * 🔐 LAMBDA AUTHORIZER ENDPOINT
     * 
     * Recebe evento do API Gateway e retorna IAM Policy
     * 
     * POST /authorize
     * {
     *   "authorizationToken": "Bearer jwt-ACC-001",
     *   "methodArn": "arn:aws:execute-api:us-east-1:123456789012:api-id/stage/method/resource"
     * }
     * 
     * Response:
     * {
     *   "principalId": "user-ACC-001",
     *   "policyDocument": { ... },
     *   "context": { "accountId": "ACC-001" }
     * }
     */
    @PostMapping("/authorize")
    public ResponseEntity<?> authorize(@RequestBody AuthorizerEvent event) {
        String authToken = event.getAuthorizationToken();
        String methodArn = event.getMethodArn();
        
        log.info("🔐 Lambda Authorizer | token={} | method_arn={}", 
            authToken.replaceAll("jwt-.*", "jwt-***"), methodArn);
        
        try {
            // Validar JWT e extrair account ID
            var accountIdOptional = jwtValidator.validateAndExtractAccountId(authToken);
            
            if (accountIdOptional.isEmpty()) {
                log.warn("❌ Autorização falhou: token inválido");
                return ResponseEntity.ok(
                    AuthorizerResponse.deny("user-unauthorized", methodArn)
                );
            }
            
            String accountId = accountIdOptional.get();
            log.info("✅ Autorização concedida | account_id={}", accountId);
            
            return ResponseEntity.ok(
                AuthorizerResponse.allow(accountId, methodArn)
            );
            
        } catch (Exception e) {
            log.error("❌ Erro ao processar autorização", e);
            return ResponseEntity.ok(
                AuthorizerResponse.deny("user-error", methodArn)
            );
        }
    }
}
