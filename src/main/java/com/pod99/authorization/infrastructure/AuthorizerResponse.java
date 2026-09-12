package com.pod99.authorization.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * DTO para resposta do Lambda Authorizer (IAM Policy)
 * 
 * Estrutura:
 * {
 *   "principalId": "user-1234",
 *   "policyDocument": {
 *     "Version": "2012-10-17",
 *     "Statement": [
 *       {
 *         "Action": "execute:Invoke",
 *         "Effect": "Allow",
 *         "Resource": "arn:..."
 *       }
 *     ]
 *   },
 *   "context": {
 *     "accountId": "ACC-001"
 *   }
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthorizerResponse {
    
    @JsonProperty("principalId")
    private String principalId;
    
    @JsonProperty("policyDocument")
    private PolicyDocument policyDocument;
    
    @JsonProperty("context")
    private Map<String, Object> context;
    
    /**
     * Factory method para criar resposta de ALLOW
     */
    public static AuthorizerResponse allow(String accountId, String methodArn) {
        String principalId = "user-" + accountId;
        
        return AuthorizerResponse.builder()
            .principalId(principalId)
            .policyDocument(PolicyDocument.allow(methodArn))
            .context(Map.of(
                "accountId", accountId
            ))
            .build();
    }
    
    /**
     * Factory method para criar resposta de DENY
     */
    public static AuthorizerResponse deny(String principalId, String methodArn) {
        return AuthorizerResponse.builder()
            .principalId(principalId)
            .policyDocument(PolicyDocument.deny(methodArn))
            .context(Collections.emptyMap())
            .build();
    }
    
    /**
     * Política IAM para autorização
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PolicyDocument {
        
        @JsonProperty("Version")
        private String version = "2012-10-17";
        
        @JsonProperty("Statement")
        private List<Statement> statement;
        
        public static PolicyDocument allow(String methodArn) {
            return PolicyDocument.builder()
                .version("2012-10-17")
                .statement(List.of(
                    Statement.builder()
                        .action("execute:Invoke")
                        .effect("Allow")
                        .resource(methodArn)
                        .build()
                ))
                .build();
        }
        
        public static PolicyDocument deny(String methodArn) {
            return PolicyDocument.builder()
                .version("2012-10-17")
                .statement(List.of(
                    Statement.builder()
                        .action("execute:Invoke")
                        .effect("Deny")
                        .resource(methodArn)
                        .build()
                ))
                .build();
        }
    }
    
    /**
     * Statement dentro da PolicyDocument
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Statement {
        
        @JsonProperty("Action")
        private String action;
        
        @JsonProperty("Effect")
        private String effect;
        
        @JsonProperty("Resource")
        private String resource;
    }
}
