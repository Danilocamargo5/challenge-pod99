package com.pod99.accounting.infrastructure;

import com.pod99.accounting.domain.AccountingRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DynamoDbAccountingRepository {

    private static final String TABLE_NAME = "pod99-accounting";

    private final DynamoDbClient dynamoDbClient;

    public void save(AccountingRecord record) {

        Map<String, AttributeValue> item = new HashMap<>();

        item.put("event_id", AttributeValue.builder()
            .s(record.eventId())
            .build());

        item.put("id_autorizacao", AttributeValue.builder()
            .s(record.idAutorizacao())
            .build());

        item.put("id_contrato", AttributeValue.builder()
            .s(record.idContrato())
            .build());

        item.put("id_conta", AttributeValue.builder()
            .s(record.idConta())
            .build());

        item.put("valor", AttributeValue.builder()
            .n(record.valor().toPlainString())
            .build());

        item.put("moeda", AttributeValue.builder()
            .s(record.moeda())
            .build());

        item.put("status", AttributeValue.builder()
            .s(record.status())
            .build());

        item.put("processed_at", AttributeValue.builder()
            .s(record.processedAt().toString())
            .build());

        PutItemRequest request = PutItemRequest.builder()
            .tableName(TABLE_NAME)
            .item(item)

            // Impede o mesmo evento de ser contabilizado duas vezes.
            .conditionExpression("attribute_not_exists(event_id)")
            .build();

        dynamoDbClient.putItem(request);

        log.info(
            "💾 Accounting salvo no DynamoDB | eventId={} | authorizationId={}",
            record.eventId(),
            record.idAutorizacao()
        );
    }
}