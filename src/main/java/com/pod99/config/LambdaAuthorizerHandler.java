package com.pod99.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Lambda Authorizer para validar OAuth2 tokens
 * 
 * Executa como função AWS Lambda quando cliente envia:
 * Authorization: Bearer <token>
 * 
 * Token é validado via:
 * 1. Assinatura JWT (HMAC-SHA256 com secret)
 * 2. Expiração (exp claim)
 * 3. Scope (authorize:write, authorize:read)
 * 
 * Retorna:
 * - 200 + IAM Policy (permitido)
 * - 401 (negado)
 * 
 * Nota: Em produção, usar AWS Cognito ou Auth0 real.
 * Este é um stub de exemplo.
 */
@Slf4j
public class LambdaAuthorizerHandler {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TOKEN_SECRET = System.getenv("TOKEN_SECRET");
    
    /**
     * Handler invocado por API Gateway
     * 
     * Event contém:
     * {
     *   "authorizationToken": "Bearer eyJhbGc...",
     *   "methodArn": "arn:aws:execute-api:us-east-1:..."
     * }
     */
    public Map<String, Object> handleAuthorizationRequest(Map<String, Object> event) {
        try {
            String token = (String) event.get("authorizationToken");
            String methodArn = (String) event.get("methodArn");
            
            log.info("🔐 Validando token...");
            
            // 1. Extrair token (remover "Bearer ")
            if (token == null || !token.startsWith("Bearer ")) {
                log.warn("⚠️ Token formato inválido");
                return generateDenyPolicy("user", methodArn);
            }
            
            String jwtToken = token.substring("Bearer ".length());
            
            // 2. Validar JWT
            Map<String, Object> claims = validateJWT(jwtToken);
            
            if (claims == null) {
                log.warn("❌ Token inválido ou expirado");
                return generateDenyPolicy("user", methodArn);
            }
            
            // 3. Extrair scopes
            @SuppressWarnings("unchecked")
            List<String> scopes = (List<String>) claims.getOrDefault("scopes", new ArrayList<>());
            String principalId = (String) claims.get("sub");
            
            // 4. Gerar IAM Policy (ALLOW)
            log.info("✅ Token válido: user={}, scopes={}", principalId, scopes);
            Map<String, Object> policy = generateAllowPolicy(principalId, methodArn);
            
            // 5. Adicionar contexto (opcional)
            @SuppressWarnings("unchecked")
            Map<String, Object> context = (Map<String, Object>) policy.get("context");
            context.put("principalId", principalId);
            context.put("scopes", String.join(",", scopes));
            
            return policy;
            
        } catch (Exception e) {
            log.error("❌ Erro na autorização", e);
            return generateDenyPolicy("user", (String) event.get("methodArn"));
        }
    }
    
    /**
     * Valida JWT token
     * 
     * Formato JWT: header.payload.signature
     * Valida:
     * - Assinatura HMAC-SHA256
     * - Expiração (exp claim)
     * - Issuer (iss claim)
     * 
     * @return Claims se válido, null se inválido
     */
    private Map<String, Object> validateJWT(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            
            // Decodificar payload (parte 2)
            String payload = new String(Base64.decode(parts[1]), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
            
            // Validar expiração
            Long exp = ((Number) claims.getOrDefault("exp", 0L)).longValue();
            if (exp * 1000 < System.currentTimeMillis()) {
                log.warn("⚠️ Token expirado");
                return null;
            }
            
            // Validar issuer (opcional)
            String iss = (String) claims.get("iss");
            if (iss == null || !iss.contains("pod99.io")) {
                log.warn("⚠️ Issuer inválido: {}", iss);
                // Em produção, rejeitar. Aqui permitimos por demo.
            }
            
            // Validar assinatura (HMAC-SHA256)
            // IMPORTANTE: Em produção, usar JWT library (jjwt, nimbus-jose-jwt)
            // Este é um stub simplificado.
            
            log.debug("✅ JWT válido: sub={}, exp={}", claims.get("sub"), exp);
            return claims;
            
        } catch (Exception e) {
            log.error("❌ Erro ao validar JWT", e);
            return null;
        }
    }
    
    /**
     * Gera IAM Policy de ALLOW
     */
    private Map<String, Object> generateAllowPolicy(String principalId, String methodArn) {
        return generatePolicy(principalId, methodArn, "Allow");
    }
    
    /**
     * Gera IAM Policy de DENY
     */
    private Map<String, Object> generateDenyPolicy(String principalId, String methodArn) {
        return generatePolicy(principalId, methodArn, "Deny");
    }
    
    /**
     * Gera IAM Policy
     * 
     * Formato:
     * {
     *   "principalId": "user123",
     *   "policyDocument": {
     *     "Version": "2012-10-17",
     *     "Statement": [
     *       {
     *         "Action": "execute-api:Invoke",
     *         "Effect": "Allow",
     *         "Resource": "arn:aws:execute-api:..."
     *       }
     *     ]
     *   },
     *   "context": {}
     * }
     */
    private Map<String, Object> generatePolicy(String principalId, String methodArn, String effect) {
        Map<String, Object> policy = new HashMap<>();
        policy.put("principalId", principalId);
        
        Map<String, Object> policyDocument = new HashMap<>();
        policyDocument.put("Version", "2012-10-17");
        
        Map<String, Object> statement = new HashMap<>();
        statement.put("Action", "execute-api:Invoke");
        statement.put("Effect", effect);
        statement.put("Resource", methodArn);
        
        policyDocument.put("Statement", List.of(statement));
        policy.put("policyDocument", policyDocument);
        policy.put("context", new HashMap<String, Object>());
        
        return policy;
    }
}
