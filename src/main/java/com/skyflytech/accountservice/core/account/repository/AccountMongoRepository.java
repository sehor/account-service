package com.skyflytech.accountservice.core.account.repository;

import com.skyflytech.accountservice.core.account.model.Account;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountMongoRepository extends MongoRepository<Account, String> {
    // 如果需要，可以在这里添加自定义的查询方法

    List<Account> findByParentId(String parentId);

    List<Account> findByAccountSetId(String accountSetId);

    void deleteByAccountSetId(String accountSetId);
}