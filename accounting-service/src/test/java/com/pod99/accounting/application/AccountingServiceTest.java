package com.pod99.accounting.application;

import com.pod99.accounting.domain.AccountingRecord;
import com.pod99.accounting.infrastructure.DynamoDbAccountingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountingServiceTest {

    @Mock
    private DynamoDbAccountingRepository repository;

    @InjectMocks
    private AccountingService service;

    @Test
    void testProcess_Success() {

        AccountingRecord record = new AccountingRecord(
                "EVENT-001",
                "AUTH-001",
                "CONTA-001",
                "ACC-001",
                BigDecimal.valueOf(100),
                "BRL",
                "PROCESSED",
                Instant.now()
        );

        service.process(record);

        verify(repository, times(1)).save(record);
    }

    @Test
    void testProcess_RepositoryFailure() {

        AccountingRecord record = new AccountingRecord(
                "EVENT-001",
                "AUTH-001",
                "CONTA-001",
                "ACC-001",
                BigDecimal.valueOf(100),
                "BRL",
                "PROCESSED",
                Instant.now()
        );

        doThrow(new RuntimeException("DynamoDB indisponível"))
                .when(repository)
                .save(record);

        assertThrows(
                RuntimeException.class,
                () -> service.process(record)
        );

        verify(repository).save(record);
    }
}