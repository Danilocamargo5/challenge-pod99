package com.pod99.limits.infrastructure;

import com.pod99.limits.domain.Limit;
import com.pod99.limits.domain.LimitRepository;
import com.pod99.common.exception.InsufficientLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DynamoDBLimitRepository implements LimitRepository {
    
    private final DynamoDbClient dynamoDbClient;
    private final String TABLE_NAME = "pod99-limits";
    
    @Override
    public void save(Limit limit) {
        PutItemRequest request = PutItemRequest.builder()
            .tableName(TABLE_NAME)
            .item(Map.of(
                "id_contrato", AttributeValue.builder().s(limit.getIdContrato()).build(),
                "limite", AttributeValue.builder().n(limit.getLimite().toPlainString()).build(),
                "disponivel", AttributeValue.builder().n(limit.getDisponivel().toPlainString()).build(),
                "reservado", AttributeValue.builder().n(limit.getReservado().toPlainString()).build()
            ))
            .build();
        
        dynamoDbClient.putItem(request);
        log.debug("✅ Limite criado: {}", limit.getIdContrato());
    }
    
    @Override
    public void update(Limit limit) {
        try {
            // Atualiza APENAS se disponivel >= 0 (Conditional Update)
            UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("id_contrato", AttributeValue.builder().s(limit.getIdContrato()).build()))
                .updateExpression("SET disponivel = :disp, reservado = :res, #v = #v + :inc")
                .expressionAttributeNames(Map.of("#v", "version"))
                .expressionAttributeValues(Map.of(
                    ":disp", AttributeValue.builder().n(limit.getDisponivel().toPlainString()).build(),
                    ":res", AttributeValue.builder().n(limit.getReservado().toPlainString()).build(),
                    ":inc", AttributeValue.builder().n("1").build()
                ))
                .conditionExpression("disponivel >= :zero")
                .expressionAttributeValues(Map.of(
                    ":zero", AttributeValue.builder().n("0").build()
                ))
                .build();
            
            dynamoDbClient.updateItem(request);
            log.debug("✅ Limite atualizado: {}", limit.getIdContrato());
            
        } catch (ConditionalCheckFailedException e) {
            throw new InsufficientLimitException("Limite insuficiente (race condition)");
        }
    }
    
    @Override
    public Optional<Limit> findByContractId(String idContrato) {
        GetItemRequest request = GetItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Map.of("id_contrato", AttributeValue.builder().s(idContrato).build()))
            .build();
        
        var response = dynamoDbClient.getItem(request);
        
        if (!response.hasItem()) {
            return Optional.empty();
        }
        
        var item = response.item();
        Limit limit = Limit.builder()
            .idContrato(item.get("id_contrato").s())
            .limite(new BigDecimal(item.get("limite").n()))
            .disponivel(new BigDecimal(item.get("disponivel").n()))
            .reservado(new BigDecimal(item.get("reservado").n()))
            .version(Long.parseLong(item.getOrDefault("version", AttributeValue.builder().n("0").build()).n()))
            .build();
        
        return Optional.of(limit);
    }
}
