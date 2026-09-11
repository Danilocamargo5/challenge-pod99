package com.pod99.authorization.domain;

import java.util.Optional;

public interface AuthorizationRepository {
    void save(Authorization authorization);
    Optional<Authorization> findById(String idAutorizacao);
    Optional<Authorization> findByIdempotencyKey(String idempotencyKey);
}
