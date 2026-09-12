package com.pod99.common.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Authentication Filter para testar JWT localmente
 * 
 * Simula o comportamento do Lambda Authorizer do API Gateway
 * 
 * IMPORTANTE: Este filter é APENAS para ambiente local/test
 * Em PRODUÇÃO: usar API Gateway + Lambda Authorizer real
 * 
 * Comportamento:
 * - Header Authorization: Bearer <token> obrigatório
 * - Token validado: Bearer jwt-<account-id> ou Bearer test-*
 * - Sem token: 401 Unauthorized
 * - Token inválido: 403 Forbidden
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, 
            HttpServletResponse response, 
            FilterChain filterChain) throws ServletException, IOException {
        
        // 1️⃣ Extrair token do header Authorization: Bearer <token>
        String authHeader = request.getHeader("Authorization");
        
        // ℹ️ Skip pra health checks e paths públicos
        String path = request.getRequestURI();
        if (path.equals("/health") || path.equals("/actuator/health")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // 2️⃣ Validar presença do token
        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("⚠️ Sem Authorization header para {}", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("""
                {
                  "type": "https://api.pod99.com/errors/unauthorized",
                  "title": "Unauthorized",
                  "status": 401,
                  "detail": "Header Authorization obrigatório (Bearer <token>)",
                  "instance": "%s",
                  "correlationId": "%s",
                  "timestamp": "%s"
                }
                """.formatted(
                    path,
                    request.getAttribute("X-Correlation-ID"),
                    java.time.Instant.now()
                ));
            return;
        }
        
        // 3️⃣ Validar formato "Bearer <token>"
        if (!authHeader.startsWith("Bearer ")) {
            log.warn("⚠️ Formato inválido: {}", authHeader);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("""
                {
                  "type": "https://api.pod99.com/errors/forbidden",
                  "title": "Forbidden",
                  "status": 403,
                  "detail": "Token inválido ou formato incorreto",
                  "instance": "%s",
                  "correlationId": "%s",
                  "timestamp": "%s"
                }
                """.formatted(
                    path,
                    request.getAttribute("X-Correlation-ID"),
                    java.time.Instant.now()
                ));
            return;
        }
        
        // 4️⃣ Extrair e validar token
        String token = authHeader.substring(7); // Remove "Bearer "
        String accountId = validateAndExtractAccountId(token);
        
        if (accountId == null) {
            log.warn("⚠️ Token rejeitado");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("""
                {
                  "type": "https://api.pod99.com/errors/forbidden",
                  "title": "Forbidden",
                  "status": 403,
                  "detail": "Token expirado ou inválido",
                  "instance": "%s",
                  "correlationId": "%s",
                  "timestamp": "%s"
                }
                """.formatted(
                    path,
                    request.getAttribute("X-Correlation-ID"),
                    java.time.Instant.now()
                ));
            return;
        }
        
        log.info("✅ Token validado para conta: {}", accountId);
        
        // 5️⃣ Adicionar info no request pra controller usar
        request.setAttribute("X-Account-Id", accountId);
        request.setAttribute("X-Authorizer-Principal-Id", accountId);
        
        // 6️⃣ Passar pra próximo filtro
        filterChain.doFilter(request, response);
    }
    
    /**
     * Valida o token
     * 
     * FORMATO ACEITO (para teste local):
     * - "jwt-ACCOUNT-001" → valida como ACCOUNT-001
     * - "test-xyz" → valida como ACC-test
     * - "demo-123" → valida como demo-123
     * 
     * Em PRODUÇÃO: validar assinatura JWT com chave pública RS256
     */
    private String validateAndExtractAccountId(String token) {
        try {
            // ⚠️ APENAS PARA TESTE LOCAL
            // Em produção: fazer parse do JWT, validar expiração, assinatura, etc
            
            if (token.isEmpty() || token.length() < 3) {
                return null;
            }
            
            // Formato: "jwt-ACCOUNT-001"
            if (token.startsWith("jwt-")) {
                return token.substring(4); // "ACCOUNT-001"
            }
            
            // Formato: "test-xyz" ou "demo-123" (pra testes rápidos)
            if (token.startsWith("test-") || token.startsWith("demo-")) {
                return token.split("-")[1]; // Pega parte após "-"
            }
            
            // Fallback: aceita qualquer token (APENAS DEMO)
            // Não fazer em produção!
            log.warn("⚠️ Token não segue formato jwt-* ou test-*, aceito como DEMO");
            return "ACC-DEMO";
            
        } catch (Exception e) {
            log.error("Erro ao validar token", e);
            return null;
        }
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Não aplicar filtro em endpoints públicos
        String path = request.getRequestURI();
        return path.startsWith("/health") || 
               path.startsWith("/actuator") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/v1/contratos/authorize");  // Lambda Authorizer Handler
    }
}
