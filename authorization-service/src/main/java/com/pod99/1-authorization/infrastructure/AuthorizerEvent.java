package com.pod99.authorization.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para evento recebido do API Gateway quando usa Lambda Authorizer
 * 
 * Estrutura:
 * {
 *   "authorizationToken": "Bearer jwt-ACC-001",
 *   "methodArn": "arn:aws:execute-api:region:account:api/stage/method/resource"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizerEvent {
    
    @JsonProperty("authorizationToken")
    private String authorizationToken;
    
    @JsonProperty("methodArn")
    private String methodArn;
}
