package com.pod99.common.infrastructure;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;

/**
 * Context para acessar informações de autorização do API Gateway/Lambda Authorizer
 * 
 * Funciona em 2 ambientes:
 * 
 * 1️⃣ LOCAL (com JwtAuthenticationFilter):
 *    - Filter coloca info em request.setAttribute()
 *    - RequestContext acessa via getAttribute()
 * 
 * 2️⃣ PRODUÇÃO (com API Gateway + Lambda Authorizer):
 *    - Lambda Authorizer valida JWT
 *    - API Gateway passa headers especiais:
 *      - x-account-id (ou x-authorizer-principal-id)
 *      - x-correlation-id (ou x-amzn-trace-id)
 *    - RequestContext acessa via getHeader()
 * 
 * Uso:
 *    String accountId = RequestContext.getAccountId()
 *                          .orElseThrow(() -> new UnauthorizedException());
 */
@Component
@Getter
@RequiredArgsConstructor
public class RequestContext {
    
    /**
     * Retorna o account ID do usuário autenticado
     * 
     * Tenta em ordem:
     * 1. request.getAttribute("X-Account-Id") (local, JwtAuthenticationFilter)
     * 2. header "x-account-id" (API Gateway)
     * 3. header "x-authorizer-principal-id" (Lambda Authorizer)
     */
    public static Optional<String> getAccountId() {
        HttpServletRequest request = getRequest();
        
        // 1. Tentar getAttribute (local)
        Object attributeValue = request.getAttribute("X-Account-Id");
        if (attributeValue instanceof String) {
            return Optional.of((String) attributeValue);
        }
        
        // 2. Tentar header x-account-id (API Gateway)
        String header = request.getHeader("x-account-id");
        if (header != null && !header.isEmpty()) {
            return Optional.of(header);
        }
        
        // 3. Tentar header x-authorizer-principal-id (Lambda Authorizer)
        header = request.getHeader("x-authorizer-principal-id");
        if (header != null && !header.isEmpty()) {
            return Optional.of(header);
        }
        
        return Optional.empty();
    }
    
    /**
     * Retorna o correlation ID para tracing distribuído
     * 
     * Tenta em ordem:
     * 1. request.getAttribute("X-Correlation-ID")
     * 2. header "x-correlation-id"
     * 3. header "x-amzn-trace-id" (AWS X-Ray)
     * 4. Gera novo UUID se não encontrar
     */
    public static String getCorrelationId() {
        HttpServletRequest request = getRequest();
        
        // 1. getAttribute
        Object attributeValue = request.getAttribute("X-Correlation-ID");
        if (attributeValue instanceof String) {
            return (String) attributeValue;
        }
        
        // 2. Header x-correlation-id
        String header = request.getHeader("x-correlation-id");
        if (header != null && !header.isEmpty()) {
            return header;
        }
        
        // 3. Header x-amzn-trace-id (AWS X-Ray)
        header = request.getHeader("x-amzn-trace-id");
        if (header != null && !header.isEmpty()) {
            return header;
        }
        
        // 4. Gerar novo
        return UUID.randomUUID().toString();
    }
    
    /**
     * Retorna o trace ID para W3C tracing
     * 
     * Tenta em ordem:
     * 1. request.getAttribute("X-Trace-ID")
     * 2. header "x-trace-id"
     * 3. header "traceparent" (W3C Trace Context)
     * 4. Gera novo UUID se não encontrar
     */
    public static String getTraceId() {
        HttpServletRequest request = getRequest();
        
        // 1. getAttribute
        Object attributeValue = request.getAttribute("X-Trace-ID");
        if (attributeValue instanceof String) {
            return (String) attributeValue;
        }
        
        // 2. Header x-trace-id
        String header = request.getHeader("x-trace-id");
        if (header != null && !header.isEmpty()) {
            return header;
        }
        
        // 3. Header traceparent (W3C Trace Context)
        header = request.getHeader("traceparent");
        if (header != null && !header.isEmpty()) {
            return header;
        }
        
        // 4. Gerar novo
        return UUID.randomUUID().toString();
    }
    
    /**
     * Valida se request está autenticado
     * 
     * @return true se account ID foi encontrado
     */
    public static boolean isAuthenticated() {
        return getAccountId().isPresent();
    }
    
    /**
     * Obtém account ID ou lança exceção
     * 
     * @return account ID
     * @throws UnauthorizedException se não autenticado
     */
    public static String requireAccountId() {
        return getAccountId()
            .orElseThrow(() -> new UnauthorizedException("Account ID não encontrado. Autenticação obrigatória."));
    }
    
    /**
     * Obtém HttpServletRequest do contexto da thread
     */
    private static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes == null) {
            throw new IllegalStateException("ServletRequestAttributes não encontrado. Contexto fora de request.");
        }
        
        return attributes.getRequest();
    }
    
    /**
     * Exceção de autorização
     */
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}
