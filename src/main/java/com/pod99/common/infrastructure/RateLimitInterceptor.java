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
            log.debug("📋 Request body: {}", requestBody);
            
            String accountId = extractAccountId(requestBody, request);
            log.info("🔍 Extracted accountId: {}", accountId);
            
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
            if (requestBody != null && requestBody.contains("\"idConta\"")) {
                int startIndex = requestBody.indexOf("\"idConta\":");
                int endIndex = requestBody.indexOf("\"", startIndex + 11);
                if (endIndex > startIndex) {
                    return requestBody.substring(startIndex + 11, endIndex)
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
     * Usa ContentCachingRequestWrapper pra permitir múltiplas leituras
     */
    private String getRequestBody(HttpServletRequest request) {
        try {
            // Se é ContentCachingRequestWrapper, usa cache direto
            if (request instanceof org.springframework.web.util.ContentCachingRequestWrapper) {
                org.springframework.web.util.ContentCachingRequestWrapper wrapper = 
                    (org.springframework.web.util.ContentCachingRequestWrapper) request;
                byte[] buf = wrapper.getContentAsByteArray();
                return new String(buf);
            }
            // Fallback para InputStream (não deve acontecer com o filter ativo)
            return new String(request.getInputStream().readAllBytes());
        } catch (Exception e) {
            return "";
        }
    }
}
