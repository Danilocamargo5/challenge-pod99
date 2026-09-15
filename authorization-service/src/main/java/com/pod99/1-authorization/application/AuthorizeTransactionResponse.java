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
}
