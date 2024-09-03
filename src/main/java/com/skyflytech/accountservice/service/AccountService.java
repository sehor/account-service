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
import org.springdoc.core.converters.models.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0); // 假设科目表在第一个sheet中

            // 假设第一行是标题，数据从第二行开始
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null)
                    continue;

                String code = getCellValueAsString(row.getCell(0));
                String name = getCellValueAsString(row.getCell(1));
                AccountType type = AccountType.fromChineseName(getCellValueAsString(row.getCell(2)));
                AccountingDirection balanceDirection = AccountingDirection
                        .fromChineseName(getCellValueAsString(row.getCell(3)));
                // AccountState state =
                // AccountState.fromChineseName(getCellValueAsString(row.getCell(4)));

                // 创建Account实例，只传入必要的参数
                Account account = new Account(code, name, GlobalConst.Current_AccountSet_Id_Test, type,
                        balanceDirection, AccountState.ACTIVE);
                account.setAccountSetId(currentAccountSetIdHolder.getCurrentAccountSetId());
                accounts.add(account);
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "导入失败，请检查文件格式是否正确。");
        }

        //set id to account
        for (Account account : accounts) {
            // check if already exists
            Account existingAccount = mongoTemplate.findOne(Query.query(
                    Criteria.where("code").is(account.getCode()).and("accountSetId").is(account.getAccountSetId())),
                    Account.class);
            if (existingAccount != null) {
                account.setId(existingAccount.getId());
            } else {
                account = accountMongoRepository.save(account);
            }
        }

        // set level and set code to account map
        Map<String, Account> accountMap = new HashMap<>();
        for (Account account : accounts) {
            account.setLevel(validateAndSetAccountLevel(account));
            accountMap.put(account.getCode(), account);
        }
        // set parentId
        for (Account account : accounts) {
            int parentLevel = account.getLevel() - 1;
            if (parentLevel > 0) {
                String parentCode = account.getCode().substring(0, GlobalConst.ACCOUNT_Code_LENGTH[parentLevel-1]);
                account.setParentId(accountMap.get(parentCode).getId());
            }
        }
        accountMongoRepository.saveAll(accounts);
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

    public Account createAccount(Account account) {
        // check accountSetId
        checkAccountSetId(account);

        // 检查名称、类型和代码是否重复
        checkDuplicateAccount(account);
        validateAndSetAccountLevel(account);
        return accountMongoRepository.save(account);
    }

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

        // 更新账户
        return accountMongoRepository.save(updatedAccount);
    }

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

    private Integer validateAndSetAccountLevel(Account account) {
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

    @Transactional
    public void initializeOpeningBalances(String accountSetId, Map<String, BigDecimal> openingBalances) {
        if (accountSetId == null || !accountSetId.equals(currentAccountSetIdHolder.getCurrentAccountSetId())) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is not match.");
        }
        // update initialBalances
        for (Map.Entry<String, BigDecimal> entry : openingBalances.entrySet()) {
            String accountId = entry.getKey();
            BigDecimal balance = entry.getValue();
            mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(accountId)),
                    Update.update("initialBalance", balance), Account.class);
        }
    }

    /**
     * 查找一个账户的所有祖先（包括它自己）
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

}