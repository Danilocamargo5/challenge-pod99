package com.pod99.accounting.application;

import com.pod99.accounting.domain.AccountingRecord;
import com.pod99.accounting.infrastructure.DynamoDbAccountingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingService {

    private final DynamoDbAccountingRepository repository;

    public void process(AccountingRecord record) {

        log.info(
            "📊 Processando lançamento contábil | eventId={} | authorizationId={}",
            record.eventId(),
            record.idAutorizacao()
        );

        repository.save(record);

        log.info(
            "✅ Lançamento contábil processado | eventId={}",
            record.eventId()
        );
    }
}