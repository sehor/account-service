package com.skyflytech.accountservice.core.accountSet.repository;

import com.skyflytech.accountservice.core.accountSet.model.AccountSet;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountSetRepository extends MongoRepository<AccountSet, String> {
    Optional<AccountSet> findByName(String name);
}