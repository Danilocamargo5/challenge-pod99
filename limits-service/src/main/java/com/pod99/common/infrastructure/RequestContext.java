package com.pod99.common.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * Context para acessar informações de autorização do API Gateway/Lambda Authorizer.
 *
 * Fluxos suportados:
 *
 * 1. LOCAL:
 *    API Gateway Simulator valida a requisição via Lambda Authorizer
 *    e encaminha:
 *    X-Account-Id: ACC-001
 *
 * 2. API GATEWAY + LAMBDA AUTHORIZER:
 *    Lambda Authorizer retorna:
 *    context.accountId = ACC-001
 *
 *    API Gateway converte para:
 *    X-Account-Id: ACC-001
 */
@Slf4j
@Component
public class RequestContext {

    private static final String ACCOUNT_ID_ATTRIBUTE = "X-Account-Id";
    private static final String ACCOUNT_ID_HEADER = "X-Account-Id";
    private static final String AUTHORIZER_PRINCIPAL_HEADER = "X-Authorizer-Principal-Id";

    /**
     * Retorna o Account ID do usuário autenticado.
     *
     * Ordem:
     * 1. request attribute X-Account-Id
     * 2. header X-Account-Id
     * 3. header X-Authorizer-Principal-Id
     */
    public static Optional<String> getAccountId() {
        HttpServletRequest request = getRequest();

        // ==============================================================
        // 1. Request attribute
        // ==============================================================

        Object attributeValue = request.getAttribute(ACCOUNT_ID_ATTRIBUTE);

        if (attributeValue instanceof String accountId
                && !accountId.isBlank()) {

            log.debug(
                "Account ID encontrado no request attribute: {}",
                accountId
            );

            return Optional.of(accountId);
        }

        // ==============================================================
        // 2. Header X-Account-Id
        // ==============================================================

        String accountIdHeader = request.getHeader(ACCOUNT_ID_HEADER);

        if (accountIdHeader != null && !accountIdHeader.isBlank()) {

            log.debug(
                "Account ID encontrado no header X-Account-Id: {}",
                accountIdHeader
            );

            return Optional.of(accountIdHeader);
        }

        // ==============================================================
        // 3. Principal ID do Lambda Authorizer
        // ==============================================================

        String principalId = request.getHeader(AUTHORIZER_PRINCIPAL_HEADER);

        if (principalId != null && !principalId.isBlank()) {

            /*
             * AuthorizerResponse retorna:
             *
             * principalId = user-ACC-001
             *
             * Para o domínio queremos somente:
             *
             * ACC-001
             */
            String accountId = principalId.startsWith("user-")
                ? principalId.substring("user-".length())
                : principalId;

            log.debug(
                "Account ID extraído do principalId: {}",
                accountId
            );

            return Optional.of(accountId);
        }

        // ==============================================================
        // Diagnóstico
        // ==============================================================

        log.warn("Account ID não encontrado na requisição.");

        if (log.isDebugEnabled()) {
            Collections.list(request.getHeaderNames())
                .forEach(headerName ->
                    log.debug(
                        "Header recebido: {}={}",
                        headerName,
                        request.getHeader(headerName)
                    )
                );
        }

        return Optional.empty();
    }

    /**
     * Retorna o correlation ID.
     */
    public static String getCorrelationId() {
        HttpServletRequest request = getRequest();

        Object attributeValue = request.getAttribute("X-Correlation-ID");

        if (attributeValue instanceof String value
                && !value.isBlank()) {
            return value;
        }

        String header = request.getHeader("X-Correlation-ID");

        if (header != null && !header.isBlank()) {
            return header;
        }

        header = request.getHeader("X-Amzn-Trace-Id");

        if (header != null && !header.isBlank()) {
            return header;
        }

        return UUID.randomUUID().toString();
    }

    /**
     * Retorna o trace ID.
     */
    public static String getTraceId() {
        HttpServletRequest request = getRequest();

        Object attributeValue = request.getAttribute("X-Trace-ID");

        if (attributeValue instanceof String value
                && !value.isBlank()) {
            return value;
        }

        String header = request.getHeader("X-Trace-ID");

        if (header != null && !header.isBlank()) {
            return header;
        }

        header = request.getHeader("traceparent");

        if (header != null && !header.isBlank()) {
            return header;
        }

        return UUID.randomUUID().toString();
    }

    /**
     * Indica se existe identidade autenticada no request.
     */
    public static boolean isAuthenticated() {
        return getAccountId().isPresent();
    }

    /**
     * Obtém Account ID ou lança UnauthorizedException.
     */
    public static String requireAccountId() {
        return getAccountId()
            .orElseThrow(() ->
                new UnauthorizedException(
                    "Account ID não encontrado. Autenticação obrigatória."
                )
            );
    }

    /**
     * Obtém HttpServletRequest da thread atual.
     */
    private static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            throw new IllegalStateException(
                "ServletRequestAttributes não encontrado. Contexto fora de request."
            );
        }

        return attributes.getRequest();
    }

    /**
     * Exceção para requisições sem identidade autenticada.
     */
    public static class UnauthorizedException extends RuntimeException {

        public UnauthorizedException(String message) {
            super(message);
        }
    }
}
