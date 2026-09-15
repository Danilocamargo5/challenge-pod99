package com.pod99.accounting.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pod99.accounting.application.AccountingService;
import com.pod99.accounting.domain.AccountingRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountingEventListenerTest {

    private AccountingService accountingService;
    private AccountingEventListener listener;

    @BeforeEach
    void setUp() {
        accountingService = mock(AccountingService.class);

        listener = new AccountingEventListener(
                new ObjectMapper(),
                accountingService
        );
    }

    @Test
    void testHandleTransacaoAutorizada_EventBridgeDetail() {

        String message = """
                {
                  "detail": {
                    "id": "AUTH-001",
                    "idContrato": "CONTA-001",
                    "idConta": "ACC-001",
                    "valor": 100.50,
                    "moeda": "BRL"
                  }
                }
                """;

        listener.handleTransacaoAutorizada(message);

        ArgumentCaptor<AccountingRecord> captor =
                ArgumentCaptor.forClass(AccountingRecord.class);

        verify(accountingService).process(captor.capture());

        AccountingRecord record = captor.getValue();

        assertEquals("AUTH-001", record.eventId());
        assertEquals("AUTH-001", record.idAutorizacao());
        assertEquals("CONTA-001", record.idContrato());
        assertEquals("ACC-001", record.idConta());

        assertEquals(
                0,
                BigDecimal.valueOf(100.50)
                        .compareTo(record.valor())
        );

        assertEquals("BRL", record.moeda());
        assertEquals("PROCESSED", record.status());
        assertNotNull(record.processedAt());
    }

    @Test
    void testHandleTransacaoAutorizada_WithoutDetail() {

        String message = """
                {
                  "id": "AUTH-002",
                  "idContrato": "CONTA-002",
                  "idConta": "ACC-002",
                  "valor": 200,
                  "moeda": "BRL"
                }
                """;

        listener.handleTransacaoAutorizada(message);

        ArgumentCaptor<AccountingRecord> captor =
                ArgumentCaptor.forClass(AccountingRecord.class);

        verify(accountingService).process(captor.capture());

        AccountingRecord record = captor.getValue();

        assertEquals("AUTH-002", record.idAutorizacao());
        assertEquals("CONTA-002", record.idContrato());
        assertEquals("ACC-002", record.idConta());
    }

    @Test
    void testHandleTransacaoAutorizada_DefaultCurrencyBRL() {

        String message = """
                {
                  "id": "AUTH-003",
                  "idContrato": "CONTA-003",
                  "idConta": "ACC-003",
                  "valor": 300
                }
                """;

        listener.handleTransacaoAutorizada(message);

        ArgumentCaptor<AccountingRecord> captor =
                ArgumentCaptor.forClass(AccountingRecord.class);

        verify(accountingService).process(captor.capture());

        assertEquals(
                "BRL",
                captor.getValue().moeda()
        );
    }

    @Test
    void testHandleTransacaoAutorizada_InvalidJson() {

        String message = "{ json-invalido }";

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> listener.handleTransacaoAutorizada(message)
                );

        assertEquals(
                "Falha ao processar evento",
                exception.getMessage()
        );

        verifyNoInteractions(accountingService);
    }
}