package com.skyflytech.accountservice.repository;

import com.skyflytech.accountservice.domain.account.Account;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountMongoRepository extends MongoRepository<Account, String> {
    // 如果需要，可以在这里添加自定义的查询方法

    List<Account> findByParentId(String parentId);

    List<Account> findByAccountSetId(String accountSetId);
}