package com.pod99.common.exception;

/**
 * Exception lançada quando o rate limit é excedido
 * Mapeia para HTTP 429 Too Many Requests
 */
public class RateLimitExceededException extends RuntimeException {
    
    private final String accountId;
    private final int requestCount;
    private final int limitPerSecond;
    private final long retryAfterSeconds;
    
    public RateLimitExceededException(
            String accountId, 
            int requestCount, 
            int limitPerSecond,
            long retryAfterSeconds) {
        super(String.format(
            "Rate limit excedido para conta %s: %d requisições em %d segundos (limite: %d/s)",
            accountId, requestCount, retryAfterSeconds, limitPerSecond
        ));
        this.accountId = accountId;
        this.requestCount = requestCount;
        this.limitPerSecond = limitPerSecond;
        this.retryAfterSeconds = retryAfterSeconds;
    }
    
    public String getAccountId() {
        return accountId;
    }
    
    public int getRequestCount() {
        return requestCount;
    }
    
    public int getLimitPerSecond() {
        return limitPerSecond;
    }
    
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
