package com.pod99.accounting.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingEntry {
    private String eventId;
    private String idAutorizacao;
    private String idContrato;
    private BigDecimal valor;
    private String tipoLancamento;
    private LocalDateTime dataOperacao;
    private LocalDateTime registradoEm;
}
