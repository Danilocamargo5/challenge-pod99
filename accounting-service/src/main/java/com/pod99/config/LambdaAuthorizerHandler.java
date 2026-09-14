package com.pod99.config;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Lambda Authorizer para API Gateway
 * 
 * Valida JWT simples no header Authorization: Bearer <token>
 * 
 * Para DEMONSTRAÇÃO: aceita qualquer token no formato Bearer <algo>
 * Em PRODUÇÃO: validar assinatura JWT com chave pública
 * 
 * Fluxo:
 * 1. API Gateway intercepta request
 * 2. Chama Lambda Authorizer
 * 3. Lambda valida JWT
 * 4. Retorna policy (Allow/Deny)
 * 5. Se Allow: request vai pra aplicação
 * 6. Se Deny: API Gateway retorna 403
 */
@Slf4j
public class LambdaAuthorizerHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String UNAUTHORIZED = "Unauthorized";
    
    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        log.info("🔐 Lambda Authorizer iniciado");
        
        try {
            // 1. Extrair token do header
            String token = extractToken(event);
            
            // 2. Validar token
            if (token == null || token.isEmpty()) {
                log.warn("⚠️ Token não fornecido");
                return denyPolicy(event, UNAUTHORIZED);
            }
            
            // 3. Parse do token (simples para demonstração)
            // Em produção: validar assinatura JWT com RS256
            String accountId = validateAndExtractAccountId(token);
            
            if (accountId == null) {
                log.warn("⚠️ Token inválido");
                return denyPolicy(event, UNAUTHORIZED);
            }
            
            log.info("✅ Token válido para conta: {}", accountId);
            
            // 4. Retornar policy de permissão
            return allowPolicy(event, accountId);
            
        } catch (Exception e) {
            log.error("❌ Erro ao validar token", e);
            return denyPolicy(event, UNAUTHORIZED);
        }
    }
    
    /**
     * Extrai o token do header Authorization: Bearer <token>
     */
    private String extractToken(Map<String, Object> event) {
        try {
            // Event structure:
            // {
            //   "type": "TOKEN",
            //   "authorizationToken": "Bearer eyJhbGciOiJIUzI1NiIsInR...",
            //   "methodArn": "arn:aws:execute-api:region:account-id:api-id/stage/METHOD/resource-path"
            // }
            
            String authorizationToken = (String) event.get("authorizationToken");
            if (authorizationToken == null || authorizationToken.isEmpty()) {
                return null;
            }
            
            // Esperado: "Bearer <token>"
            if (!authorizationToken.startsWith("Bearer ")) {
                return null;
            }
            
            return authorizationToken.substring(7); // Remove "Bearer "
            
        } catch (Exception e) {
            log.error("Erro ao extrair token", e);
            return null;
        }
    }
    
    /**
     * Valida o token e extrai o ID da conta
     * 
     * Para DEMONSTRAÇÃO: aceita qualquer token Bearer <algo>
     * Em PRODUÇÃO: 
     * - Verificar assinatura JWT com chave pública
     * - Validar expiração
     * - Validar issuer/audience
     */
    private String validateAndExtractAccountId(String token) {
        try {
            // Para DEMONSTRAÇÃO:
            // Token simples: "jwt-<account-id>" ou "test-account-123"
            // Em PRODUÇÃO: fazer parse do JWT e validar assinatura
            
            // ⚠️ DEMONSTRAÇÃO: aceita qualquer token
            if (token.isEmpty()) {
                return null;
            }
            
            // Extrair account ID do token (formato: "jwt-ACCOUNT-001" ou similar)
            if (token.startsWith("jwt-")) {
                return token.substring(4); // "ACCOUNT-001"
            }
            
            // Fallback: usar token como account ID direto
            // (não fazer em produção!)
            return "ACC-" + token.substring(0, Math.min(10, token.length()));
            
        } catch (Exception e) {
            log.error("Erro ao validar token", e);
            return null;
        }
    }
    
    /**
     * Retorna policy de PERMISSÃO
     */
    private Map<String, Object> allowPolicy(Map<String, Object> event, String principalId) {
        return authPolicy(event, "Allow", principalId);
    }
    
    /**
     * Retorna policy de NEGAÇÃO
     */
    private Map<String, Object> denyPolicy(Map<String, Object> event, String principalId) {
        return authPolicy(event, "Deny", principalId);
    }
    
    /**
     * Constrói o response de autorização do Lambda Authorizer
     * 
     * Formato esperado pelo API Gateway:
     * {
     *   "principalId": "user-123",
     *   "policyDocument": {
     *     "Version": "2012-10-17",
     *     "Statement": [
     *       {
     *         "Action": "execute-api:Invoke",
     *         "Effect": "Allow|Deny",
     *         "Resource": "arn:aws:execute-api:region:account-id:api-id/stage/METHOD/resource-path"
     *       }
     *     ]
     *   },
     *   "context": {
     *     "accountId": "ACC-001"
     *   }
     * }
     */
    private Map<String, Object> authPolicy(Map<String, Object> event, String effect, String principalId) {
        String methodArn = (String) event.get("methodArn");
        
        // methodArn: arn:aws:execute-api:region:account-id:api-id/stage/METHOD/resource-path
        // Extrair wildcards pra cobrir toda a API
        String apiGatewayArn = methodArn.substring(0, methodArn.lastIndexOf("/"));
        String apiGatewayArnPartial = apiGatewayArn.substring(0, apiGatewayArn.lastIndexOf("/")) + "/*";
        
        Map<String, Object> authResponse = new HashMap<>();
        authResponse.put("principalId", principalId);
        
        // Policy document
        Map<String, Object> policyDocument = new HashMap<>();
        policyDocument.put("Version", "2012-10-17");
        
        // Statement (permite/nega execute-api:Invoke)
        Map<String, Object> statement = new HashMap<>();
        statement.put("Action", "execute-api:Invoke");
        statement.put("Effect", effect); // "Allow" ou "Deny"
        statement.put("Resource", apiGatewayArnPartial);
        
        policyDocument.put("Statement", Collections.singletonList(statement));
        authResponse.put("policyDocument", policyDocument);
        
        // Context (passado pro controller via $context.authorizer)
        Map<String, Object> context = new HashMap<>();
        context.put("accountId", principalId);
        context.put("requestTimeEpoch", System.currentTimeMillis());
        authResponse.put("context", context);
        
        return authResponse;
    }
}
