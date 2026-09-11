package com.pod99.authorization.application;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizeTransactionRequest {
    private String idConta;
    private BigDecimal valor;
    private String moeda;
    private String tipoOperacao;
    private String idEstabelecimento;
    private java.util.Map<String, Object> metadata;
}
