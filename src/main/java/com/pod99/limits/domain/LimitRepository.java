package com.pod99.limits.domain;

import java.util.Optional;

public interface LimitRepository {
    void save(Limit limit);
    void update(Limit limit);
    Optional<Limit> findByContractId(String idContrato);
}
