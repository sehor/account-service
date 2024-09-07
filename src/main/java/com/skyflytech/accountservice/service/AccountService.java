package com.skyflytech.accountservice.service;

import com.skyflytech.accountservice.domain.AccountAmountHolder;
import com.skyflytech.accountservice.domain.AccountSet;
import com.skyflytech.accountservice.domain.AccountingPeriod;
import com.skyflytech.accountservice.domain.Transaction;
import com.skyflytech.accountservice.domain.account.Account;
import com.skyflytech.accountservice.global.GlobalConst;
import com.skyflytech.accountservice.repository.AccountMongoRepository;
import com.skyflytech.accountservice.repository.AccountingPeriodRepository;
import com.skyflytech.accountservice.repository.TransactionMongoRepository;
import com.skyflytech.accountservice.security.CurrentAccountSetIdHolder;
import com.skyflytech.accountservice.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.NotAcceptableStatusException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

import static com.mongodb.internal.authentication.AwsCredentialHelper.LOGGER;

@Service
public class AccountService {

    private final AccountMongoRepository accountMongoRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final MongoTemplate mongoTemplate;
    private final CurrentAccountSetIdHolder currentAccountSetIdHolder;
    private final TransactionMongoRepository transactionMongoRepository;

    @Autowired
    public AccountService(AccountMongoRepository accountMongoRepository,
            AccountingPeriodRepository accountingPeriodRepository,
            MongoTemplate mongoTemplate,
            CurrentAccountSetIdHolder currentAccountSetIdHolder,
            TransactionMongoRepository transactionMongoRepository) {

        this.accountMongoRepository = accountMongoRepository;
        this.accountingPeriodRepository = accountingPeriodRepository;
        this.mongoTemplate = mongoTemplate;
        this.currentAccountSetIdHolder = currentAccountSetIdHolder;
        this.transactionMongoRepository = transactionMongoRepository;
    }

    public List<Account> getAllAccounts(String accountSetId) {

        List<Account> accounts = mongoTemplate.find(Query.query(Criteria.where("accountSetId").is(accountSetId)),
                Account.class);
        if (accounts.isEmpty()) {
            LOGGER.info("No accounts found.");
            return List.of(); // Return an immutable empty list if no accounts are found
        }
        // sort by code dictionary order
        accounts.sort(Comparator.comparing(Account::getCode));
        return accounts;
    }

    public Account getAccountById(String id) {
        return accountMongoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("no such account: " + id));
    }

    @Transactional
    public Account createAccount(Account account) {
        // check accountSetId
        checkAccountSetId(account);

        // 检查名称、类型和代码是否重复
        checkDuplicateAccount(account);
        validateAndSetAccountLevel(account);
        // 检查父账户
        if (account.getLevel() <= 1) {
            return accountMongoRepository.save(account);
        }
        Account parentAccount = getParentAndCheckParentId(account);
        checkParentCode(account, parentAccount);
        account.setParentId(parentAccount.getId());
        if (parentAccount.isLeaf()) {
            // transfer transactions belong to parentAccount to it's child account
            List<Transaction> transactions = mongoTemplate
                    .find(Query.query(Criteria.where("accountId").is(parentAccount.getId())), Transaction.class);
            for (Transaction transaction : transactions) {
                transaction.setAccountId(account.getId());
            }
            transactionMongoRepository.saveAll(transactions);
            parentAccount.setLeaf(false);
            account.setLeaf(true);
        }
        mongoTemplate.save(parentAccount);
        return accountMongoRepository.save(account);
    }

    @Transactional
    public Account updateAccount(Account updatedAccount) {
        validateAndSetAccountLevel(updatedAccount);
        // 检查 accountSetId
        checkAccountSetId(updatedAccount);
        if (!Utils.isNotEmpty(updatedAccount.getId())) {
            throw new NotAcceptableStatusException("试图更新账户，但其ID为空！");
        }

        // 获取原始账户
        Account originalAccount = accountMongoRepository.findById(updatedAccount.getId())
                .orElseThrow(() -> new NoSuchElementException("未找到ID为 " + updatedAccount.getId() + " 的账户"));

        // 检查名称、类型和代码是否重复（排除当前账户）
        checkDuplicateAccount(updatedAccount, originalAccount.getId());

        // 检查代码是否发生变化
        if (!originalAccount.getCode().equals(updatedAccount.getCode())) {
            // 只有在代码发生变化时才检查子账户
            List<Account> childAccounts = accountMongoRepository.findByParentId(updatedAccount.getId());
            if (!childAccounts.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "该账户有子账户，不允许更改代码。");
            }
        }

        // 检查父账户
        if (updatedAccount.getLevel() <= 1) {
            return accountMongoRepository.save(updatedAccount);
        }
        Account parentAccount = getParentAndCheckParentId(updatedAccount);
        checkParentCode(updatedAccount, parentAccount);
        return accountMongoRepository.save(updatedAccount);
    }

    @Transactional
    public void deleteAccount(String id) {
        Account account = accountMongoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Account not found with id: " + id));
        if (Utils.isNotNullOrEmpty(accountMongoRepository.findByParentId(id))) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE,
                    "The account has children, not allowed to delete.");
        }
        // check accountSetId
        checkAccountSetId(account);
        if (Utils.isNotNullOrEmpty(
                mongoTemplate.find(Query.query(Criteria.where("accountId").is(id)), Transaction.class))) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE,
                    "The account has transactions, not allowed to delete.");
        }
        accountMongoRepository.delete(account);
        // if it's parentAccount has no other children,set it's parentAccount to leaf
        if (account.getParentId() != null) {
            Account parentAccount = mongoTemplate.findById(account.getParentId(), Account.class);
            if (parentAccount != null && isLeaf(parentAccount)) {
                parentAccount.setLeaf(true);
                mongoTemplate.save(parentAccount);
            }
        }
    }

    /* fuzzy search by name or code */
    public List<Account> searchAccounts(String search, String accountSetId) {
        // MongoDB 中有一个 `name`或者`code` 字段需要匹配查询条件
        Pattern pattern = Pattern.compile(Pattern.quote(search));
        Query query = Query.query(Criteria.where("accountSetId").is(accountSetId));
        query.addCriteria(Criteria.where("name").regex(pattern));
        query.addCriteria(Criteria.where("code").regex(pattern));
        // sort by code dictionary order
        List<Account> accounts = mongoTemplate.find(query, Account.class);
        accounts.sort(Comparator.comparing(Account::getCode));
        return accounts;
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


    @Transactional
    public void initializeOpeningBalances(String accountSetId, Map<String, BigDecimal> openingBalances) {
        if (accountSetId == null || !accountSetId.equals(currentAccountSetIdHolder.getCurrentAccountSetId())) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is not match.");
        }
        // find accountSet
        AccountSet accountSet = mongoTemplate.findById(accountSetId, AccountSet.class);
        if (accountSet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AccountSet not found");
        }
        // changes of accountSet initialAccountBalance items
        Map<String, BigDecimal> changes = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : openingBalances.entrySet()) {
            String accountId = entry.getKey();
            BigDecimal oldBalance = accountSet.getInitialAccountBalance().get(accountId);
            if (oldBalance == null) {
                // add new item
                accountSet.getInitialAccountBalance().put(accountId, entry.getValue());
                changes.put(accountId, entry.getValue());
            } else {
                BigDecimal change = entry.getValue().subtract(oldBalance);
                changes.put(accountId, change);
            }
        }
        // update all following accountingPeriods
        List<AccountingPeriod> accountingPeriods = accountingPeriodRepository.findByAccountSetId(accountSetId);
        for (AccountingPeriod accountingPeriod : accountingPeriods) {
            for (Map.Entry<String, BigDecimal> entry : changes.entrySet()) {
                AccountAmountHolder accountAmountHolder = accountingPeriod.getAmountHolders().get(entry.getKey());
                if (accountAmountHolder != null) {
                    accountAmountHolder.setBalance(accountAmountHolder.getBalance().add(entry.getValue()));
                } else {
                    // add new item
                    accountAmountHolder = new AccountAmountHolder();
                    accountAmountHolder.setBalance(entry.getValue());
                    accountingPeriod.getAmountHolders().put(entry.getKey(), accountAmountHolder);
                }
            }
        }
        // 保存accountingPeriods改变
        accountingPeriodRepository.saveAll(accountingPeriods);
        // 保存accountSet
        for (Map.Entry<String, BigDecimal> entry : openingBalances.entrySet()) {
            accountSet.getInitialAccountBalance().put(entry.getKey(), entry.getValue());
        }
        mongoTemplate.save(accountSet);
    }

    /**
     * 查找一个账户的所有祖先（包括它自己）
     * 
     * @param accountId 要查找的账户ID
     * @return 包含所有祖先账户（包括自身）的列表
     */
    public List<Account> findAccountAndAllAncestors(String accountId) {
        List<Account> ancestors = new ArrayList<>();
        Account currentAccount = mongoTemplate.findById(accountId, Account.class);

        while (currentAccount != null) {
            ancestors.add(currentAccount);
            if (currentAccount.getParentId() == null) {
                break;
            }
            currentAccount = mongoTemplate.findById(currentAccount.getParentId(), Account.class);
        }

        return ancestors;
    }

    // check a acount if is a leaf
    public boolean isLeaf(Account account) {
        if (account.getLevel() >= GlobalConst.ACCOUNT_CODE_LEVEL_MAP.get(account.getCode().length())) {
            return true;
        } else {
            List<Account> childAccounts = mongoTemplate
                    .find(Query.query(Criteria.where("parentId").is(account.getId())), Account.class);
            return childAccounts.isEmpty();
        }

    }

    // private methods
    private void checkAccountSetId(Account account) {
        String accountSetId = currentAccountSetIdHolder.getCurrentAccountSetId();
        if (account.getAccountSetId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is null.");
        }
        if (!account.getAccountSetId().equals(accountSetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is not match.");
        }
    }

    private void checkDuplicateAccount(Account account) {
        checkDuplicateAccount(account, null);
    }

    private void checkDuplicateAccount(Account account, String excludeId) {
        // 检查名称和类型是否重复
        Criteria nameTypeCriteria = Criteria.where("accountSetId").is(account.getAccountSetId())
                .and("name").is(account.getName())
                .and("type").is(account.getType());

        if (excludeId != null) {
            nameTypeCriteria.and("id").ne(excludeId);
        }

        Query nameTypeQuery = new Query(nameTypeCriteria);
        if (mongoTemplate.exists(nameTypeQuery, Account.class)) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "已存在相同名称和类型的账户。");
        }

        // 检查代码是否重复
        Criteria codeCriteria = Criteria.where("accountSetId").is(account.getAccountSetId())
                .and("code").is(account.getCode());

        if (excludeId != null) {
            codeCriteria.and("id").ne(excludeId);
        }

        Query codeQuery = new Query(codeCriteria);
        if (mongoTemplate.exists(codeQuery, Account.class)) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "已存在相同代码的账户。");
        }
    }

    public Integer validateAndSetAccountLevel(Account account) {
        String code = account.getCode();

        // 检查code是否为空
        if (code == null) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "账户编码为空。");
        }

        Integer level = account.getLevel();

        // 检查level是否为null以及level是否在有效范围内
        if (level != null) {
            if (level < 1 || level > GlobalConst.ACCOUNT_Code_LENGTH.length) {
                throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "账户级别无效。");
            }

            if (code.length() != GlobalConst.ACCOUNT_Code_LENGTH[level - 1]) {
                throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "账户编码长度与设定的级别不匹配。");
            }
        }

        // 确定编码的级别
        int calculatedLevel = GlobalConst.ACCOUNT_CODE_LEVEL_MAP.getOrDefault(code.length(), -1);

        if (calculatedLevel == -1) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "账户编码长度不符合要求。");
        }

        account.setLevel(calculatedLevel);
        return calculatedLevel;
    }

    // if account's level>1,get parentAccount and set
    private Account getParentAndCheckParentId(Account account) {
        Account parentAccount = null;
        if (account.getParentId() != null) {
            parentAccount = mongoTemplate.findById(account.getParentId(), Account.class);
            if (parentAccount == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent account not found");
            }
        } else {
            parentAccount = mongoTemplate.findById(account.getCode().substring(0, account.getLevel() - 1),
                    Account.class);
            if (parentAccount == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent account not found");
            } else {
                account.setParentId(parentAccount.getId());
            }
        }
        return parentAccount;
    }

    // check account's parent ,if account's level>1 ,set it's parentId
    private void checkParentCode(Account account, Account parentAccount) {
        // check code match
        if (!account.getCode().substring(0, account.getLevel() - 1).equals(parentAccount.getCode())) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Parent account code is not match.");
        }
    }

    @Transactional
    public void deleteAccountsByAccountSetId(String accountSetId) {
        accountMongoRepository.deleteByAccountSetId(accountSetId);
    }

}