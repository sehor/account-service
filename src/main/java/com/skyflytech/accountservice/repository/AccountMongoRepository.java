package com.skyflytech.accountservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import com.skyflytech.accountservice.domain.account.Account;

import java.util.List;

@Repository
public interface AccountMongoRepository extends MongoRepository<Account, String> {
    // 如果需要，可以在这里添加自定义的查询方法

    @Query("{ '$or': [ { 'name': { '$regex': ?0, '$options': 'i' } }, { 'code': { '$regex': ?0, '$options': 'i' } } ] }")
    List<Account> findByQuery(String query);

    List<Account> findByParentId(String parentId);

    List<Account> findByAccountSetId(String accountSetId);
}