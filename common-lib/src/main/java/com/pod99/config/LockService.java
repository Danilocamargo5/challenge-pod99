package com.pod99.config;

import com.pod99.common.exception.LockAcquisitionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LockService {
    
    private final DynamoDbClient dynamoDbClient;
    private final String LOCK_TABLE = "pod99-locks";
    private final long LOCK_TTL = 30L;  // segundos
    private final int MAX_RETRIES = 5;
    private final long BASE_RETRY_DELAY = 100L;  // ms
    
    /**
     * Adquire locks em ordem (FIFO) com retry automático
     * Usa ConditionExpression para atomicidade
     */
    public List<String> acquireTransactionLocks(
            String idAutorizacao,
            String idContrato) {
        
        List<String> lockKeys = Arrays.asList(idAutorizacao, idContrato);
        List<String> acquiredLocks = new ArrayList<>();
        
        try {
            for (String lockKey : lockKeys) {
                log.info("🔒 Tentando adquirir lock: {}", lockKey);
                
                boolean acquired = false;
                for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                    try {
                        acquireSingleLock(lockKey);
                        acquiredLocks.add(lockKey);
                        log.info("✅ Lock adquirido: {} (tentativa {}/{})", lockKey, attempt, MAX_RETRIES);
                        acquired = true;
                        break;
                    } catch (ConditionalCheckFailedException e) {
                        if (attempt < MAX_RETRIES) {
                            long delay = BASE_RETRY_DELAY * attempt;
                            log.warn("⏳ Contenção no lock {}, aguardando {}ms (tentativa {}/{})", 
                                lockKey, delay, attempt, MAX_RETRIES);
                            Thread.sleep(delay);
                        }
                    }
                }
                
                if (!acquired) {
                    throw new LockAcquisitionException(
                        "Falha ao adquirir lock após " + MAX_RETRIES + " tentativas: " + lockKey);
                }
            }
            
            log.info("🔐 Todos os locks adquiridos: {}", acquiredLocks);
            return acquiredLocks;
            
        } catch (Exception e) {
            log.warn("❌ Erro ao adquirir locks, liberando os já obtidos...");
            releaseLocks(acquiredLocks);
            throw new LockAcquisitionException("Falha na aquisição de locks", e);
        }
    }
    
    /**
     * Adquire um lock individual usando PutItem + ConditionExpression
     */
    private void acquireSingleLock(String lockKey) throws ConditionalCheckFailedException {
        long expiryTime = Instant.now().getEpochSecond() + LOCK_TTL;
        
        PutItemRequest request = PutItemRequest.builder()
            .tableName(LOCK_TABLE)
            .item(Map.of(
                "lock_key", AttributeValue.builder().s(lockKey).build(),
                "owner_id", AttributeValue.builder().s("authorization-service").build(),
                "expiry_time", AttributeValue.builder().n(String.valueOf(expiryTime)).build(),
                "acquired_at", AttributeValue.builder().s(Instant.now().toString()).build()
            ))
            .conditionExpression("attribute_not_exists(lock_key)")  // ← ATÔMICO
            .build();
        
        dynamoDbClient.putItem(request);
    }
    
    /**
     * Libera locks em ordem REVERSA (LIFO) para evitar deadlock
     */
    public void releaseLocks(List<String> locks) {
        List<String> reversed = new ArrayList<>(locks);
        Collections.reverse(reversed);
        
        reversed.forEach(lockKey -> {
            try {
                releaseSingleLock(lockKey);
            } catch (Exception e) {
                log.error("⚠️ Erro ao liberar lock {}: {}", lockKey, e.getMessage());
            }
        });
    }
    
    /**
     * Libera um lock individual deletando da tabela
     */
    private void releaseSingleLock(String lockKey) {
        DeleteItemRequest request = DeleteItemRequest.builder()
            .tableName(LOCK_TABLE)
            .key(Map.of("lock_key", AttributeValue.builder().s(lockKey).build()))
            .build();
        
        dynamoDbClient.deleteItem(request);
        log.info("🔓 Lock liberado: {}", lockKey);
    }
}
