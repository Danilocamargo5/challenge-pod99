package com.pod99.common.infrastructure;

import com.pod99.common.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Rate Limit Service usando DynamoDB
 * 
 * Estratégia: Token Bucket + Sliding Window (leaky bucket)
 * - Por conta (id_conta)
 * - Limite configurável de TPS
 * - Janela de 1 segundo
 * 
 * Segurança: Usa DynamoDB para garantir atomicidade entre instâncias
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {
    
    private final DynamoDbClient dynamoDb;
    
    @Value("${app.rate-limit.tps:100}")
    private int defaultLimitPerSecond;
    
    @Value("${app.rate-limit.table-name:pod99-rate-limit}")
    private String rateLimitTableName;
    
    /**
     * Verifica se a requisição deve ser aceita pelo rate limiter
     * Usa sliding window (1 segundo)
     * 
     * @param accountId ID da conta
     * @param limitPerSecond Limite de TPS (se 0, usa default)
     * @return true se deve prosseguir, false se deve rejeitar
     * @throws RateLimitExceededException se excedido
     */
    public void checkRateLimit(String accountId, int limitPerSecond) {
        if (limitPerSecond <= 0) {
            limitPerSecond = defaultLimitPerSecond;
        }
        
        try {
            long now = System.currentTimeMillis();
            long windowStart = now - 1000;  // Janela de 1 segundo
            
            // Tentar atualizar o contador atomicamente
            UpdateItemResponse response = dynamoDb.updateItem(
                UpdateItemRequest.builder()
                    .tableName(rateLimitTableName)
                    .key(Map.of("account_id", AttributeValue.builder().s(accountId).build()))
                    .updateExpression("SET request_count = if_not_exists(request_count, :zero) + :one, " +
                                     "window_start = if_not_exists(window_start, :now), " +
                                     "#ttl = :ttl")
                    .expressionAttributeNames(Map.of(
                        "#ttl", "ttl"  // Escape para palavra-chave reservada
                    ))
                    .expressionAttributeValues(Map.of(
                        ":zero", AttributeValue.builder().n("0").build(),
                        ":one", AttributeValue.builder().n("1").build(),
                        ":now", AttributeValue.builder().n(String.valueOf(now)).build(),
                        ":ttl", AttributeValue.builder().n(String.valueOf(now / 1000 + 3600)).build()  // TTL: 1 hora
                    ))
                    .returnValues(ReturnValue.ALL_NEW)
                    .build()
            );
            
            // Verificar se a janela expirou e resetar se necessário
            long windowStart_stored = Long.parseLong(
                response.attributes().get("window_start").n());
            int requestCount = Integer.parseInt(
                response.attributes().get("request_count").n());
            
            if (now - windowStart_stored > 1000) {
                // Janela expirou, resetar contador
                dynamoDb.updateItem(
                    UpdateItemRequest.builder()
                        .tableName(rateLimitTableName)
                        .key(Map.of("account_id", AttributeValue.builder().s(accountId).build()))
                        .updateExpression("SET request_count = :one, window_start = :now")
                        .expressionAttributeValues(Map.of(
                            ":one", AttributeValue.builder().n("1").build(),
                            ":now", AttributeValue.builder().n(String.valueOf(now)).build()
                        ))
                        .build()
                );
                return;  // ✅ Aceitar
            }
            
            // Verificar se excedeu o limite
            if (requestCount > limitPerSecond) {
                long windowEndTime = windowStart_stored + 1000;
                long retryAfter = Math.max(1, (windowEndTime - now) / 1000);
                
                log.warn("⚠️ Rate limit excedido para conta {}: {} requisições em 1s (limite: {})",
                    accountId, requestCount, limitPerSecond);
                
                throw new RateLimitExceededException(
                    accountId,
                    requestCount,
                    limitPerSecond,
                    retryAfter
                );
            }
            
            log.debug("✅ Rate limit OK para {}: {} requisições de {}/s",
                accountId, requestCount, limitPerSecond);
                
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Erro ao verificar rate limit para conta {}: {}", accountId, e.getMessage());
            // ⚠️ Falhar aberto (permitir requisição) para evitar indisponibilidade
            // Em produção, considerar falhar fechado dependendo do SLA
        }
    }
    
    /**
     * Reset manual do rate limit (para testes)
     */
    public void resetRateLimit(String accountId) {
        dynamoDb.deleteItem(
            DeleteItemRequest.builder()
                .tableName(rateLimitTableName)
                .key(Map.of("account_id", AttributeValue.builder().s(accountId).build()))
                .build()
        );
        log.info("🔄 Rate limit resetado para {}", accountId);
    }
}
