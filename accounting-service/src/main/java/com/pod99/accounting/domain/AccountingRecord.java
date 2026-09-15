package com.pod99.accounting.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountingRecord(
    String eventId,
    String idAutorizacao,
    String idContrato,
    String idConta,
    BigDecimal valor,
    String moeda,
    String status,
    Instant processedAt
) {

    public static AccountingRecord processed(
            String eventId,
            String idAutorizacao,
            String idContrato,
            String idConta,
            BigDecimal valor,
            String moeda) {

        return new AccountingRecord(
            eventId,
            idAutorizacao,
            idContrato,
            idConta,
            valor,
            moeda,
            "PROCESSED",
            Instant.now()
        );
    }
}