package com.pod99.authorization.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Serviço para validar JWT e extrair account ID
 * 
 * ⚠️ IMPORTANTE: Isto é um STUB para desenvolvimento local.
 * Em produção, usar um JWT provider real (Cognito, Auth0, Keycloak, etc)
 */
@Slf4j
@Service
public class JwtValidator {
    
    /**
     * Valida o token e extrai o account ID
     * 
     * Formato esperado: "Bearer jwt-ACC-001"
     * 
     * @param authorizationHeader valor do header "Authorization"
     * @return Optional com account ID se válido, vazio se inválido
     */
    public Optional<String> validateAndExtractAccountId(String authorizationHeader) {
        try {
            // Validar formato "Bearer ..."
            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                log.warn("❌ Authorization header inválido ou ausente");
                return Optional.empty();
            }
            
            // Extrair token
            String token = authorizationHeader.substring("Bearer ".length());
            
            if (token.isEmpty()) {
                log.warn("❌ Token vazio");
                return Optional.empty();
            }
            
            // ⚠️ STUB: Extrair account ID do token
            // Formato esperado: "jwt-ACC-001"
            // Em produção, validar assinatura + claims
            
            if (token.contains("jwt-")) {
                String accountId = token.replace("jwt-", "");
                
                // Validar formato ACC-XXX
                if (accountId.matches("ACC-\\d{3}")) {
                    log.info("✅ Token validado | account_id={}", accountId);
                    return Optional.of(accountId);
                }
            }
            
            log.warn("❌ Token inválido ou account ID não encontrado");
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("❌ Erro ao validar JWT", e);
            return Optional.empty();
        }
    }
}
