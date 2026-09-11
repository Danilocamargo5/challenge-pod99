package com.pod99.accounting.domain;

import java.util.Optional;

public interface AccountingRepository {
    void save(AccountingEntry entry);
    Optional<AccountingEntry> findByEventId(String eventId);
}
