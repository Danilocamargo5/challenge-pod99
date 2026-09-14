package com.pod99.accounting.infrastructure;

import com.pod99.accounting.domain.AccountingEntry;
import com.pod99.accounting.domain.AccountingRepository;
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
public class DynamoDBAccountingRepository implements AccountingRepository {
    
    private final DynamoDbClient dynamoDbClient;
    private final String TABLE_NAME = "pod99-accounting";
    
    @Override
    public void save(AccountingEntry entry) {
        PutItemRequest request = PutItemRequest.builder()
            .tableName(TABLE_NAME)
            .item(Map.of(
                "event_id", AttributeValue.builder().s(entry.getEventId()).build(),
                "id_autorizacao", AttributeValue.builder().s(entry.getIdAutorizacao()).build(),
                "valor", AttributeValue.builder().n(entry.getValor().toPlainString()).build(),
                "tipo_lancamento", AttributeValue.builder().s(entry.getTipoLancamento()).build(),
                "registrado_em", AttributeValue.builder().s(entry.getRegistradoEm().toString()).build()
            ))
            .build();
        
        dynamoDbClient.putItem(request);
        log.debug("✅ Lançamento contábil salvo: {}", entry.getEventId());
    }
    
    @Override
    public Optional<AccountingEntry> findByEventId(String eventId) {
        GetItemRequest request = GetItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Map.of("event_id", AttributeValue.builder().s(eventId).build()))
            .build();
        
        var response = dynamoDbClient.getItem(request);
        return response.hasItem() ? Optional.of(new AccountingEntry()) : Optional.empty();
    }
}
