package com.pod99.accounting.infrastructure;

import com.pod99.accounting.domain.AccountingRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamoDbAccountingRepositoryTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @InjectMocks
    private DynamoDbAccountingRepository repository;

    @Test
    void testSave_Success() {

        Instant processedAt = Instant.parse("2026-09-15T12:00:00Z");

        AccountingRecord record = new AccountingRecord(
                "EVENT-001",
                "AUTH-001",
                "CONTA-001",
                "ACC-001",
                new BigDecimal("150.50"),
                "BRL",
                "PROCESSED",
                processedAt
        );

        repository.save(record);

        ArgumentCaptor<PutItemRequest> captor =
                ArgumentCaptor.forClass(PutItemRequest.class);

        verify(dynamoDbClient, times(1))
                .putItem(captor.capture());

        PutItemRequest request = captor.getValue();

        assertEquals(
                "pod99-accounting",
                request.tableName()
        );

        assertEquals(
                "attribute_not_exists(event_id)",
                request.conditionExpression()
        );

        assertEquals(
                "EVENT-001",
                request.item().get("event_id").s()
        );

        assertEquals(
                "AUTH-001",
                request.item().get("id_autorizacao").s()
        );

        assertEquals(
                "CONTA-001",
                request.item().get("id_contrato").s()
        );

        assertEquals(
                "ACC-001",
                request.item().get("id_conta").s()
        );

        assertEquals(
                "150.50",
                request.item().get("valor").n()
        );

        assertEquals(
                "BRL",
                request.item().get("moeda").s()
        );

        assertEquals(
                "PROCESSED",
                request.item().get("status").s()
        );

        assertEquals(
                processedAt.toString(),
                request.item().get("processed_at").s()
        );

        assertEquals(8, request.item().size());
    }
}