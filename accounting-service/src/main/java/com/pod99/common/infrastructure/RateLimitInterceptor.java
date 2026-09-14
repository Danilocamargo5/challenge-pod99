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
 * Interceptor que aplica Rate Limiting por conta
 * Extrai o accountId do header X-Account-Id (setado pelo Lambda Authorizer)
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
            return true;
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
            // Extrair accountId do header X-Account-Id (setado pelo Lambda Authorizer)
            String accountId = request.getHeader("X-Account-Id");
            log.info("🔍 Extracted accountId from X-Account-Id header: {}", accountId);
            
            if (accountId != null && !accountId.isEmpty()) {
                // Verificar rate limit
                rateLimitService.checkRateLimit(accountId, defaultLimitPerSecond);
                log.debug("✅ Rate limit check passed for account: {}", accountId);
            } else {
                log.warn("⚠️ X-Account-Id header not found - skipping rate limit check");
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
            log.warn("⚠️ Rate limit exceeded for account: {}", e.getMessage());
            return false;
        }
    }
}
