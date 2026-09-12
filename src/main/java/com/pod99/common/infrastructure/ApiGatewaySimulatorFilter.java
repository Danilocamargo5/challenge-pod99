package com.pod99.common.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Simula o API Gateway passando contexto do Lambda Authorizer
 * 
 * Em PRODUÇÃO:
 * - API Gateway valida JWT via Lambda Authorizer
 * - API Gateway passa headers de contexto:
 *   - x-account-id (principal ID)
 *   - x-correlation-id (trace ID)
 *   - x-trace-id (W3C trace)
 * 
 * Este filtro simula esse comportamento em ambiente local/test
 * Extrai o token do header Authorization e passa como x-account-id
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiGatewaySimulatorFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, 
            HttpServletResponse response, 
            FilterChain filterChain) throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        // Skip em endpoints públicos
        if (shouldNotFilter(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // 1️⃣ Extrair token do header Authorization (simular request pré-validado do API Gateway)
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String accountId = extractAccountIdFromToken(token);
            
            if (accountId != null) {
                // 2️⃣ Simular API Gateway passando contexto como header
                // Cria wrapper que adiciona header x-account-id
                HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(request) {
                    private final String extractedAccountId = accountId;
                    
                    @Override
                    public String getHeader(String name) {
                        if ("x-account-id".equalsIgnoreCase(name)) {
                            return extractedAccountId;
                        }
                        if ("x-correlation-id".equalsIgnoreCase(name)) {
                            String correlationId = super.getHeader("x-correlation-id");
                            return correlationId != null ? correlationId : UUID.randomUUID().toString();
                        }
                        return super.getHeader(name);
                    }
                };
                
                log.info("✅ Simulando API Gateway: Account {} (token extraído)", accountId);
                filterChain.doFilter(wrappedRequest, response);
                return;
            }
        }
        
        // 3️⃣ Se não tiver Authorization, deixar passar
        // (RequestContext vai tentar outros headers ou vai falhar com 401)
        log.debug("⚠️ Sem Authorization header para {}", path);
        filterChain.doFilter(request, response);
    }
    
    /**
     * Extrai account ID do token
     * Formato: "jwt-ACC-001" → "ACC-001"
     */
    private String extractAccountIdFromToken(String token) {
        try {
            if (token.startsWith("jwt-")) {
                return token.substring(4);
            }
            if (token.startsWith("test-")) {
                return "ACC-TEST";
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/health") || 
               path.startsWith("/actuator") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/v1/contratos/authorize");  // Lambda Authorizer Handler
    }
}
