package com.skyflytech.accountservice.service;

import com.skyflytech.accountservice.domain.AccountingPeriod;
import com.skyflytech.accountservice.domain.Transaction;
import com.skyflytech.accountservice.domain.account.Account;
import com.skyflytech.accountservice.domain.account.AccountState;
import com.skyflytech.accountservice.domain.account.AccountType;
import com.skyflytech.accountservice.domain.account.AccountingDirection;
import com.skyflytech.accountservice.global.GlobalConst;
import com.skyflytech.accountservice.repository.AccountMongoRepository;
import com.skyflytech.accountservice.repository.AccountingPeriodRepository;
import com.skyflytech.accountservice.security.CurrentAccountSetIdHolder;
import com.skyflytech.accountservice.utils.Utils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.NotAcceptableStatusException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mongodb.internal.authentication.AwsCredentialHelper.LOGGER;

@Service
public class AccountService {

    private final AccountMongoRepository accountMongoRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final MongoTemplate mongoTemplate;
    private final CurrentAccountSetIdHolder currentAccountSetIdHolder;

    @Autowired
    public AccountService(AccountMongoRepository accountMongoRepository, 
                          AccountingPeriodRepository accountingPeriodRepository,
                          MongoTemplate mongoTemplate,
                          CurrentAccountSetIdHolder currentAccountSetIdHolder) {

        this.accountMongoRepository = accountMongoRepository;
        this.accountingPeriodRepository = accountingPeriodRepository;
        this.mongoTemplate = mongoTemplate;
        this.currentAccountSetIdHolder = currentAccountSetIdHolder;
    }

    @Transactional
    public void extractAccountsFromExcel(InputStream inputStream) {
        List<Account> accounts = new ArrayList<>();

        Workbook workbook = null;
        try {
            workbook = new XSSFWorkbook(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Sheet sheet = workbook.getSheetAt(0); // 假设科目表在第一个sheet中

        // 假设第一行是标题，数据从第二行开始
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String code = getCellValueAsString(row.getCell(0));
            String name = getCellValueAsString(row.getCell(1));
            AccountType type = AccountType.fromChineseName(getCellValueAsString(row.getCell(2)));
            AccountingDirection balanceDirection = AccountingDirection.fromChineseName(getCellValueAsString(row.getCell(3)));
            //AccountState state = AccountState.fromChineseName(getCellValueAsString(row.getCell(4)));

            // 创建Account实例，只传入必要的参数
            Account account = new Account(code, name, GlobalConst.ACCOUNT_SET_ID_FOR_TEST, type, balanceDirection, AccountState.ACTIVE);

            accounts.add(account);
        }

        try {
            workbook.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // 更新父子关系
        List<Account> saved = accountMongoRepository.saveAll(accounts);
        updateAccountHierarchy(saved);
        //save again  after set parentId
        accountMongoRepository.saveAll(saved);
    }


    public List<Account> getAllAccounts(String accountSetId) {


        List<Account> accounts = mongoTemplate.find(Query.query(Criteria.where("accountSetId").is(accountSetId)), Account.class);
        if (accounts.isEmpty()) {
            LOGGER.info("No accounts found.");
            return List.of(); // Return an immutable empty list if no accounts are found
        }
        return accounts;
    }

    public Account getAccountById(String id) {
        return accountMongoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("no such account: " + id));
    }


    public Account saveOrCreateAccount(Account account) {
        //check accountSetId
        checkAccountSetId(account);
        // 这里可以添加一些验证或其他处理逻辑
        if(!Utils.isNotEmpty(account.getParentId())){
            String parentCode = findParentCode(account.getCode());
            if(parentCode!=null){
                Account parentAccount = mongoTemplate.findOne(Query.query(Criteria.where("code").is(parentCode)), Account.class);
                if(parentAccount==null){
                    throw new NoSuchElementException("no such parent account ，code is: "+parentCode);
                }
                account.setParentId(parentAccount.getId());
            }
        }
        return accountMongoRepository.save(account);
    }

    public void updateAccount(Account updatedAccount) {
        //check accountSetId
        checkAccountSetId(updatedAccount);
        if(!Utils.isNotEmpty(updatedAccount.getId())){
            throw new NotAcceptableStatusException("try to update account,but its id is null!");
        }
        accountMongoRepository.save(updatedAccount);
    }

    public void deleteAccount(String id) {
        Account account = accountMongoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Account not found with id: " + id));
        if (Utils.isNotNullOrEmpty(accountMongoRepository.findByParentId(id))) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The account has children, not allowed to delete.");
        }
        //check accountSetId
        checkAccountSetId(account);
        if (Utils.isNotNullOrEmpty(mongoTemplate.find(Query.query(Criteria.where("accountId").is(id)), Transaction.class))) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The account has transactions, not allowed to delete.");
        }
        accountMongoRepository.delete(account);
    }


    /*fuzzy search by name or code*/
    public List<Account> searchAccounts(String search,String accountSetId) {
        // MongoDB 中有一个 `name`或者`code` 字段需要匹配查询条件
        Pattern pattern = Pattern.compile(Pattern.quote(search));
        Query query=Query.query(Criteria.where("accountSetId").is(accountSetId));
        query.addCriteria(Criteria.where("name").regex(pattern));
        query.addCriteria(Criteria.where("code").regex(pattern));
        return mongoTemplate.find(query, Account.class);
     
    }

    public List<Account> getLeafSubAccounts(String accountId) {
        List<Account> leafSubAccounts = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();
        queue.offer(accountId);

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            List<Account> directSubAccounts = accountMongoRepository.findByParentId(currentId);
            
            if (directSubAccounts.isEmpty()) {
                // 这是一个叶子节点，包括初始的账户
                accountMongoRepository.findById(currentId).ifPresent(leafSubAccounts::add);
            } else {
                directSubAccounts.forEach(account -> queue.offer(account.getId()));
            }
        }

        return leafSubAccounts;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
            default:
                return "";
        }
    }

    private void updateAccountHierarchy(List<Account> accounts) {
        // 使用Map将code映射到对应的Account对象，方便快速查找
        Map<String, Account> accountMap = new HashMap<>();
        for (Account account : accounts) {
            accountMap.put(account.getCode(), account);
        }

        // 更新parentAccountId
        for (Account account : accounts) {
            String code = account.getCode();
            String parentCode = findParentCode(code);
            if (parentCode != null) {
                Account parentAccount = accountMap.get(parentCode);
                account.setParentId(parentAccount.getId());
            }
        }
    }

    private String findParentCode(String code) {
        int sum_len=0;
        for(int i=0;i<GlobalConst.ACCOUNT_Code_LENGTH.length-1;i++){
            sum_len+=GlobalConst.ACCOUNT_Code_LENGTH[i];
            if(code.length()==sum_len){
                if(i-1>=0){
                    return code.substring(0,sum_len-GlobalConst.ACCOUNT_Code_LENGTH[i]);
                }else {
                    return null;
                }
            }
        }
        return null; // 没有找到父级ID
    }

    public BigDecimal calculateAccountBalanceAtDate(String accountId, LocalDate date) {
        Account account = accountMongoRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        String accountSetId = account.getAccountSetId();
        LocalDate periodStartDate = YearMonth.from(date).atDay(1);

        AccountingPeriod currentPeriod = accountingPeriodRepository
                .findByAccountSetIdAndStartDate(accountSetId, periodStartDate)
                .orElseThrow(() -> new RuntimeException("No accounting period found for the given date"));

        BigDecimal openingBalance = currentPeriod.getOpeningBalances().getOrDefault(accountId, BigDecimal.ZERO);

        Query query = new Query();
        query.addCriteria(Criteria.where("accountId").is(accountId)
                .and("createdDate").gte(currentPeriod.getStartDate()).lte(date));

        List<Transaction> transactions = mongoTemplate.find(query, Transaction.class);

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (Transaction transaction : transactions) {
            totalDebit = totalDebit.add(transaction.getDebit());
            totalCredit = totalCredit.add(transaction.getCredit());
        }

        if (account.getBalanceDirection() == AccountingDirection.DEBIT) {
            return openingBalance.add(totalDebit).subtract(totalCredit);
        } else {
            return openingBalance.subtract(totalDebit).add(totalCredit);
        }
    }

    public Map<String, BigDecimal> calculateAllLeafAccountBalancesForMonth(String accountSetId, YearMonth month) {
        LocalDate endOfMonth = month.atEndOfMonth();
        
        // 获取该账套下的所有账户
        List<Account> allAccounts = accountMongoRepository.findByAccountSetId(accountSetId);
        
        // 找出所有叶子账户
        List<Account> leafAccounts = getLeafAccounts(allAccounts);

        Map<String, BigDecimal> balances = new HashMap<>();

        for (Account leafAccount : leafAccounts) {
            BigDecimal balance = calculateAccountBalanceAtDate(leafAccount.getId(), endOfMonth);
            balances.put(leafAccount.getId(), balance);
        }

        return balances;
    }

    public List<Account> getLeafAccounts(List<Account> allAccounts) {
        // 创建一个Set来存储所有的父账户ID
        Set<String> parentIds = new HashSet<>();

        // 第一次遍历：填充parentIds和accountMap
        for (Account account : allAccounts) {
            if (account.getParentId() != null) {
                parentIds.add(account.getParentId());
            }
        }
        
        // 创建结果列表
        List<Account> leafAccounts = new ArrayList<>();
        
        // 第二次遍历：找出叶子账户
        for (Account account : allAccounts) {
            if (!parentIds.contains(account.getId())) {
                leafAccounts.add(account);
            }
        }
        
        return leafAccounts;
    }

    public Map<String, BigDecimal> calculateAllAccountBalancesForMonth(String accountSetId, YearMonth month) {
        // 获取该账套下的所有账户
        List<Account> allAccounts = accountMongoRepository.findByAccountSetId(accountSetId);
        
        // 计算所有叶子账户的余额
        Map<String, BigDecimal> leafBalances = calculateAllLeafAccountBalancesForMonth(accountSetId, month);
        
        // 创建一个map来存储所有账户的余额
        Map<String, BigDecimal> allBalances = new HashMap<>(leafBalances);
        
        // 创建一个map来存储账户ID到Account对象的映射，方便快速查找
        Map<String, Account> accountMap = allAccounts.stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        
        // 从叶子节点开始，向上计算父节点的余额
        for (Map.Entry<String, BigDecimal> entry : leafBalances.entrySet()) {
            Account account = accountMap.get(entry.getKey());
            String parentId = account.getParentId();
            while (parentId != null) {
                allBalances.merge(parentId, entry.getValue(), BigDecimal::add);
                parentId = accountMap.get(parentId).getParentId();
            }
        }

        return allBalances;
    }

    private void checkAccountSetId(Account account){
        String accountSetId = currentAccountSetIdHolder.getCurrentAccountSetId();
        if(account.getAccountSetId()==null){
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is null.");
        }
        if(!account.getAccountSetId().equals(accountSetId)){
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is not match."); 
        }
    }
}