package com.skyflytech.accountservice.core.journalEntry.repository;

import com.skyflytech.accountservice.core.journalEntry.model.JournalEntry;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * @Author pzr
 * @date:2024-08-15-7:12
 * @Description:
 **/
@Repository
public interface EntryMongoRepository extends MongoRepository<JournalEntry,String> {

    List<JournalEntry> findAllByAccountSetId(String accountSetId);
    List<JournalEntry> findByAccountSetIdAndModifiedDateBetween(String accountSetId, LocalDate startDate, LocalDate endDate);
    
    void deleteByAccountSetId(String accountSetId);
}
