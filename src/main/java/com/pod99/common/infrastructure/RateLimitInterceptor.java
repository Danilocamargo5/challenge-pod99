package com.pod99.common.infrastructure;

import com.pod99.common.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor que aplica Rate Limiting por conta (id_conta)
 * 
 * Extrai o id_conta do path params e valida contra rate limit
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {
    
    private final RateLimitService rateLimitService;
    
    @Value("${app.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;
    
    @Value("${app.rate-limit.tps:100}")
    private int defaultLimitPerSecond;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {
        
        if (!rateLimitEnabled) {
            return true;  // Skip rate limiting se desabilitado
        }
        
        try {
            // Extrair id_conta da requisição
            // Ex: POST /v1/contratos/{idContrato}/autorizacoes
            String requestBody = getRequestBody(request);
            String accountId = extractAccountId(requestBody, request);
            
            if (accountId != null && !accountId.isEmpty()) {
                // Verificar rate limit
                rateLimitService.checkRateLimit(accountId, defaultLimitPerSecond);
                log.debug("✅ Rate limit check passed for account: {}", accountId);
            }
            
            return true;
            
        } catch (RateLimitExceededException e) {
            // Retornar 429 Too Many Requests
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(e.getRetryAfterSeconds()));
            response.getWriter().write(String.format(
                "{\"error_code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"%s\",\"retry_after_seconds\":%d}",
                e.getMessage(),
                e.getRetryAfterSeconds()
            ));
            log.warn("⚠️ Rate limit exceeded: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Extrai id_conta do request (JSON body ou path params)
     */
    private String extractAccountId(String requestBody, HttpServletRequest request) {
        try {
            // Tentar extrair do JSON body
            if (requestBody.contains("\"id_conta\"")) {
                int startIndex = requestBody.indexOf("\"id_conta\":");
                int endIndex = requestBody.indexOf("\"", startIndex + 12);
                if (endIndex > startIndex) {
                    return requestBody.substring(startIndex + 12, endIndex)
                        .replace("\"", "").trim();
                }
            }
            
            // Tentar extrair de path params (se houver)
            String pathInfo = request.getPathInfo();
            if (pathInfo != null && pathInfo.contains("contas/")) {
                String[] parts = pathInfo.split("/");
                for (int i = 0; i < parts.length - 1; i++) {
                    if ("contas".equals(parts[i])) {
                        return parts[i + 1];
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Não foi possível extrair account_id: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Helper para ler body da requisição (sem consumir o stream)
     */
    private String getRequestBody(HttpServletRequest request) {
        try {
            return new String(request.getInputStream().readAllBytes());
        } catch (Exception e) {
            return "";
        }
    }
}
