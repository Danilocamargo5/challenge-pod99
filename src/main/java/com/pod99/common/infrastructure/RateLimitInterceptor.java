package com.pod99.common.infrastructure;

import com.pod99.common.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * Interceptor que aplica Rate Limiting por conta (id_conta)
 * 
 * Extrai o id_conta do JSON body e valida contra rate limit
 * PULA requisições internas (Lambda Authorizer, health checks, etc)
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
        
        // Pular requisições internas (Lambda Authorizer, health checks)
        String path = request.getRequestURI();
        if (path.equals("/v1/contratos/authorize") || 
            path.startsWith("/actuator") ||
            path.startsWith("/health")) {
            log.debug("⏭️ Skipping rate limit for internal path: {}", path);
            return true;
        }
        
        try {
            // Extrair id_conta da requisição
            String requestBody = getRequestBody(request);
            log.debug("📋 Request body: {} bytes", requestBody != null ? requestBody.length() : 0);
            
            String accountId = extractAccountId(requestBody, request);
            log.info("🔍 Extracted accountId: {} from path: {}", accountId, path);
            
            if (accountId != null && !accountId.isEmpty()) {
                // Verificar rate limit
                rateLimitService.checkRateLimit(accountId, defaultLimitPerSecond);
                log.debug("✅ Rate limit check passed for account: {}", accountId);
            } else {
                log.warn("⚠️ Could not extract accountId from request - skipping rate limit check");
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
            if (requestBody != null && !requestBody.isEmpty() && requestBody.contains("\"idConta\"")) {
                int startIndex = requestBody.indexOf("\"idConta\":");
                int endIndex = requestBody.indexOf("\"", startIndex + 11);
                if (endIndex > startIndex) {
                    String accountId = requestBody.substring(startIndex + 11, endIndex)
                        .replace("\"", "").trim();
                    log.debug("✅ Extracted accountId from body: {}", accountId);
                    return accountId;
                }
            }
        } catch (Exception e) {
            log.debug("Não foi possível extrair account_id do body: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Helper para ler body da requisição
     */
    private String getRequestBody(HttpServletRequest request) {
        try {
            if (request instanceof ContentCachingRequestWrapper) {
                ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
                byte[] buf = wrapper.getContentAsByteArray();
                return new String(buf);
            }
        } catch (Exception e) {
            log.debug("Error reading request body: {}", e.getMessage());
        }
        return "";
    }
}
