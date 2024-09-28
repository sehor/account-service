package com.skyflytech.accountservice.core.journalEntry.service.imp;

import com.skyflytech.accountservice.core.accountingPeriod.model.AccountingPeriod;
import com.skyflytech.accountservice.core.journalEntry.model.JournalEntry;
import com.skyflytech.accountservice.core.journalEntry.model.JournalEntryView;
import com.skyflytech.accountservice.core.journalEntry.repository.EntryMongoRepository;
import com.skyflytech.accountservice.core.journalEntry.service.JournalEntryService;
import com.skyflytech.accountservice.core.transaction.model.Transaction;
import com.skyflytech.accountservice.security.model.CurrentAccountSetIdHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author pzr
 * @date:2024-08-15-7:07
 * @Description:
 **/
@Service
@Transactional(rollbackFor = Exception.class)
public class JournalEntryServiceImp implements JournalEntryService {

    private final EntryMongoRepository journalEntryRepository;
    private final CurrentAccountSetIdHolder currentAccountSetIdHolder;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public JournalEntryServiceImp(EntryMongoRepository journalEntryRepository,
                                  CurrentAccountSetIdHolder currentAccountSetIdHolder,
                                  MongoTemplate mongoTemplate) {
        this.journalEntryRepository = journalEntryRepository;
        this.currentAccountSetIdHolder = currentAccountSetIdHolder;
        this.mongoTemplate = mongoTemplate;
    }

    
    @Transactional
    public void deleteEntry(JournalEntry journalEntry) {
        checkAccountSetId(journalEntry);
        deleteByJourneyEntry(journalEntry);
        journalEntryRepository.delete(journalEntry);
    }

    public List<JournalEntry> getAllJournalEntries(String accountSetId) {
        return journalEntryRepository.findAllByAccountSetId(accountSetId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<JournalEntryView> getJournalEntriesByPeriod(String accountSetId, LocalDate startDate, LocalDate endDate) {
        // 获取指定日期范围内的所有记账凭证
        List<JournalEntry> journalEntries = journalEntryRepository.findByAccountSetIdAndModifiedDateBetween(accountSetId, startDate, endDate);
        
        // 收集所有交易ID
        Set<String> allTransactionIds = journalEntries.stream()
                .flatMap(je -> je.getTransactionIds().stream())
                .collect(Collectors.toSet());

        // 批量查询所有相关交易
        Query query = new Query(Criteria.where("_id").in(allTransactionIds));
        List<Transaction> allTransactions = mongoTemplate.find(query, Transaction.class);

        // 创建交易ID到交易对象的映射
        Map<String, Transaction> transactionMap = allTransactions.stream()
                .collect(Collectors.toMap(Transaction::getId, t -> t));

        // 构建JournalEntryView列表
        return journalEntries.stream()
                .map(journalEntry -> {
                    List<Transaction> transactions = journalEntry.getTransactionIds().stream()
                            .map(transactionMap::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    return new JournalEntryView(journalEntry, transactions);
                })
                .collect(Collectors.toList());
    }

    //获取某个会计期间的所有凭证
    public List<JournalEntry> getJournalEntriesByPeriod(String accountSetId, AccountingPeriod accountingPeriod) {
        return journalEntryRepository.findByAccountSetIdAndModifiedDateBetween(accountSetId, accountingPeriod.getStartDate(), accountingPeriod.getEndDate());
    }

    @Transactional
    public void deleteJournalEntriesByAccountSetId(String accountSetId) {
        journalEntryRepository.deleteByAccountSetId(accountSetId);
    }

 //找到某个会计期间的最大凭证号
 public int findMaxVoucherNum(String accountSetId, LocalDate date) {
    Query query = new Query(Criteria.where("accountSetId").is(accountSetId).and("modifiedDate").lte(date));
    query.with(Sort.by(Sort.Direction.ASC, "voucherNum"));
    query.limit(1);
    JournalEntry journalEntry = mongoTemplate.findOne(query, JournalEntry.class);
    if(journalEntry==null){
        return 0;
    }
    return journalEntry.getVoucherNum();
}

//按时间顺序重新排序并重设凭证号
public void reorderVoucherNum(String accountSetId, LocalDate startDate,LocalDate endDate) {
    Query query = new Query(Criteria.where("accountSetId").is(accountSetId).and("modifiedDate").lte(endDate).gte(startDate));
    query.with(Sort.by(Sort.Direction.ASC, "modifiedDate"));
    List<JournalEntry> journalEntries = mongoTemplate.find(query, JournalEntry.class);
    for (int i = 0; i < journalEntries.size(); i++) {
        journalEntries.get(i).setVoucherNum(i + 1);
    }
    journalEntryRepository.saveAll(journalEntries);
} 

//某个会计期间凭证号是否连续
public boolean isVoucherNumContinuous(AccountingPeriod accountingPeriod) {
    String accountSetId=accountingPeriod.getAccountSetId();
    LocalDate startDate=accountingPeriod.getStartDate();
    LocalDate endDate=accountingPeriod.getEndDate();
    Query query = new Query(Criteria.where("accountSetId").is(accountSetId).and("modifiedDate").lte(endDate).gte(startDate));
    query.with(Sort.by(Sort.Direction.ASC, "voucherNum"));
    List<JournalEntry> journalEntries = mongoTemplate.find(query, JournalEntry.class);
    for (int i = 0; i < journalEntries.size() - 1; i++) {
        if (journalEntries.get(i).getVoucherNum() + 1 != journalEntries.get(i + 1).getVoucherNum()) {
            return false;
        }
    }
    return true;
}   




    private void checkAccountSetId(JournalEntry journalEntry){
        String accountSetId = currentAccountSetIdHolder.getCurrentAccountSetId();
        if(journalEntry.getAccountSetId()==null){
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is null.");
        }
        if(!journalEntry.getAccountSetId().equals(accountSetId)){
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is not match."); 
        }
    }



    @Transactional
     void deleteByJourneyEntry(JournalEntry entry) {

        if(!entry.getAccountSetId().equals(currentAccountSetIdHolder.getCurrentAccountSetId())){
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is not match.");
        }
        Set<String> transactionIds = entry.getTransactionIds();
        mongoTemplate.findAllAndRemove(new Query(Criteria.where("id").in(transactionIds)),
                Transaction.class);
    }
}
