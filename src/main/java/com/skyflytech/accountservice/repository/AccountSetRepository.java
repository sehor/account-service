package com.skyflytech.accountservice.repository;

import com.skyflytech.accountservice.domain.AccountSet;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountSetRepository extends MongoRepository<AccountSet, String> {
    Optional<AccountSet> findByName(String name);
}