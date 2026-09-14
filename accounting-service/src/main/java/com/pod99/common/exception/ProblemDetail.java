package com.pod99.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * RFC 7807 - Problem Details for HTTP APIs
 * Resposta padronizada para erros
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetail {
    
    /**
     * URI que identifica o tipo do problema
     * Ex: https://api.pod99.com/errors/insufficient-limit
     */
    private String type;
    
    /**
     * Título legível do problema
     * Ex: "Insufficient Limit"
     */
    private String title;
    
    /**
     * Status HTTP
     */
    private Integer status;
    
    /**
     * Descrição detalhada do problema
     */
    private String detail;
    
    /**
     * URI da instância específica que gerou o erro
     * Ex: "/v1/contratos/ABC123/autorizacoes"
     */
    private String instance;
    
    /**
     * Correlation ID para rastreamento
     */
    private String correlationId;
    
    /**
     * Timestamp do erro
     */
    private Instant timestamp;
    
    /**
     * Cria um ProblemDetail para INSUFFICIENT_LIMIT (402)
     */
    public static ProblemDetail insufficientLimit(String detail, String instance, String correlationId) {
        return ProblemDetail.builder()
            .type("https://api.pod99.com/errors/insufficient-limit")
            .title("Insufficient Limit")
            .status(402)
            .detail(detail)
            .instance(instance)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Cria um ProblemDetail para CONFLICT (409)
     */
    public static ProblemDetail conflict(String detail, String instance, String correlationId) {
        return ProblemDetail.builder()
            .type("https://api.pod99.com/errors/conflict")
            .title("Conflict")
            .status(409)
            .detail(detail)
            .instance(instance)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Cria um ProblemDetail para VALIDATION_ERROR (422)
     */
    public static ProblemDetail validationError(String detail, String instance, String correlationId) {
        return ProblemDetail.builder()
            .type("https://api.pod99.com/errors/validation-error")
            .title("Validation Error")
            .status(422)
            .detail(detail)
            .instance(instance)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Cria um ProblemDetail para RATE_LIMIT_EXCEEDED (429)
     */
    public static ProblemDetail rateLimitExceeded(String detail, String instance, String correlationId) {
        return ProblemDetail.builder()
            .type("https://api.pod99.com/errors/rate-limit-exceeded")
            .title("Rate Limit Exceeded")
            .status(429)
            .detail(detail)
            .instance(instance)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Cria um ProblemDetail genérico
     */
    public static ProblemDetail of(String type, String title, Integer status, String detail, 
                                   String instance, String correlationId) {
        return ProblemDetail.builder()
            .type(type)
            .title(title)
            .status(status)
            .detail(detail)
            .instance(instance)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
    }
}
