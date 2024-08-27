package com.skyflytech.accountservice.repository;

import com.skyflytech.accountservice.domain.journalEntry.JournalEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * @Author pzr
 * @date:2024-08-15-7:12
 * @Description:
 **/
@Repository
public interface EntryMongoRepository extends MongoRepository<JournalEntry,String> {
}
