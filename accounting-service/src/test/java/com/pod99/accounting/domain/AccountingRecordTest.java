package com.pod99.accounting.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountingRecordTest {

    @Test
    void testProcessed_CreateRecord() {

        AccountingRecord record = AccountingRecord.processed(
                "EVENT-001",
                "AUTH-001",
                "CONTA-001",
                "ACC-001",
                BigDecimal.valueOf(150.50),
                "BRL"
        );

        assertEquals("EVENT-001", record.eventId());
        assertEquals("AUTH-001", record.idAutorizacao());
        assertEquals("CONTA-001", record.idContrato());
        assertEquals("ACC-001", record.idConta());

        assertEquals(
                0,
                BigDecimal.valueOf(150.50)
                        .compareTo(record.valor())
        );

        assertEquals("BRL", record.moeda());
        assertEquals("PROCESSED", record.status());
        assertNotNull(record.processedAt());
    }
}