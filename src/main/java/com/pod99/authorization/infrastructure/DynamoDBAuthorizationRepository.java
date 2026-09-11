package com.pod99.authorization.infrastructure;

import com.pod99.authorization.domain.Authorization;
import com.pod99.authorization.domain.AuthorizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

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
                "id_contrato", AttributeValue.builder().s(authorization.getIdContrato()).build(),
                "valor", AttributeValue.builder().n(authorization.getValor().toPlainString()).build(),
                "status", AttributeValue.builder().s(authorization.getStatus().toString()).build(),
                "criado_em", AttributeValue.builder().s(authorization.getCriadoEm().toString()).build()
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
        return Optional.empty();  // Simplificado para o desafio
    }
}
