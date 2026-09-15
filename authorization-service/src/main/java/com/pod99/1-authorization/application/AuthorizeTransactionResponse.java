package com.pod99.authorization.application;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizeTransactionResponse {
    private String authorizationId;
    private String status;
    private String message;
    private String idAutorizacao;
    private Double saldoReservado;
    private boolean repetition;
    
    // Getters para compatibilidade com campos específicos
    public String getIdAutorizacao() {
        return idAutorizacao != null ? idAutorizacao : authorizationId;
    }
    
    public Double getSaldoReservado() {
        return saldoReservado;
    }
    
    public boolean isRepetition() {
        return repetition;
    }
}
