package com.pod99.authorization.infrastructure;

import com.pod99.authorization.domain.Authorization;
import com.pod99.authorization.domain.AuthorizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DynamoDBAuthorizationRepository implements AuthorizationRepository {
    
    private final DynamoDbClient dynamoDbClient;
    private final String TABLE_NAME = "pod99-authorizations";
    
    @Override
    public void save(Authorization authorization) {
        PutItemRequest request = PutItemRequest.builder()
            .tableName(TABLE_NAME)
            .item(Map.of(
                "id_autorizacao", AttributeValue.builder().s(authorization.getIdAutorizacao()).build(),
                "idempotency_key", AttributeValue.builder().s(authorization.getIdempotencyKey()).build(),
                "id_contrato", AttributeValue.builder().s(authorization.getIdContrato()).build(),
                "valor", AttributeValue.builder().n(authorization.getValor().toPlainString()).build(),
                "saldo_reservado", AttributeValue.builder().n(authorization.getSaldoReservado().toPlainString()).build(),
                "status", AttributeValue.builder().s(authorization.getStatus().toString()).build(),
                "criado_em", AttributeValue.builder().s(authorization.getCriadoEm().toString()).build(),
                "correlation_id", AttributeValue.builder().s(authorization.getCorrelationId()).build()
            ))
            .build();
        
        dynamoDbClient.putItem(request);
        log.debug("✅ Autorização salva: {}", authorization.getIdAutorizacao());
    }
    
    @Override
    public Optional<Authorization> findById(String idAutorizacao) {
        return Optional.empty();
    }
    
    @Override
    public Optional<Authorization> findByIdempotencyKey(String idempotencyKey) {
        try {
            ScanRequest request = ScanRequest.builder()
                .tableName(TABLE_NAME)
                .filterExpression("idempotency_key = :key")
                .expressionAttributeValues(Map.of(
                    ":key", AttributeValue.builder().s(idempotencyKey).build()
                ))
                .build();
            
            var response = dynamoDbClient.scan(request);
            
            if (response.items().isEmpty()) {
                return Optional.empty();
            }
            
            var item = response.items().get(0);
            Authorization auth = Authorization.builder()
                .idAutorizacao(item.get("id_autorizacao").s())
                .idempotencyKey(item.get("idempotency_key").s())
                .idContrato(item.get("id_contrato").s())
                .valor(new BigDecimal(item.get("valor").n()))
                .saldoReservado(new BigDecimal(item.get("saldo_reservado").n()))
                .status(Authorization.Status.valueOf(item.get("status").s()))
                .criadoEm(LocalDateTime.parse(item.get("criado_em").s()))
                .correlationId(item.get("correlation_id").s())
                .build();
            
            return Optional.of(auth);
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar por idempotencyKey: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
