package com.skyflytech.accountservice.core.account.service;

import com.skyflytech.accountservice.core.account.service.imp.AccountServiceImp;
import com.skyflytech.accountservice.core.account.model.Account;
import com.skyflytech.accountservice.core.account.model.AccountState;
import com.skyflytech.accountservice.core.account.model.AccountType;
import com.skyflytech.accountservice.core.account.model.AccountingDirection;
import com.skyflytech.accountservice.global.GlobalConst;
import com.skyflytech.accountservice.core.account.repository.AccountMongoRepository;
import com.skyflytech.accountservice.security.model.CurrentAccountSetIdHolder;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.util.*;

@Service
public class ExcelImportService {

    private final AccountMongoRepository accountMongoRepository;
    private final AccountServiceImp accountServiceImp;

    @Autowired
    public ExcelImportService(AccountMongoRepository accountMongoRepository,
            CurrentAccountSetIdHolder currentAccountSetIdHolder,
            AccountServiceImp accountServiceImp) {
        this.accountMongoRepository = accountMongoRepository;
        this.accountServiceImp = accountServiceImp;
    }

    @Transactional
    public void extractAccountsFromExcel(InputStream inputStream, String accountSetId) {
        List<Account> accounts = new ArrayList<>();
        // check if match accountSetId
        // if (!accountSetId.equals(currentAccountSetIdHolder.getCurrentAccountSetId())) {
        //     throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "账户集不匹配。");
        // }

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

                Account account = new Account(code, name, GlobalConst.Current_AccountSet_Id_Test, type,
                        balanceDirection, AccountState.ACTIVE);
                account.setAccountSetId(accountSetId);
                accounts.add(account);
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "导入失败，请检查文件格式是否正确。");
        }

        processImportedAccounts(accounts, accountSetId);
    }

    @Transactional
    private void processImportedAccounts(List<Account> accounts, String accountSetId) {
        // find all accounts in this accountSet
        List<Account> allOriginalAccounts = accountMongoRepository.findByAccountSetId(accountSetId);
        //create a code map for allOriginalAccounts
        Map<String, Account> codeMap = new HashMap<>();
        for (Account account : allOriginalAccounts) {
            codeMap.put(account.getCode(), account);
        }
        // list accounts to be updated and a list to be created
        List<Account> accountsToBeUpdated = new ArrayList<>();
        List<Account> accountsToBeCreated = new ArrayList<>();
        // set id,initialBalance to account
        for (Account account : accounts) {
            Account existingAccount = codeMap.getOrDefault(account.getCode(), null);
            if (existingAccount != null) {
                account.setId(existingAccount.getId());
                account.setInitialBalance(existingAccount.getInitialBalance());
                account.setLeaf(existingAccount.isLeaf());
                account.setParentId(existingAccount.getParentId());
                account.setLevel(existingAccount.getLevel());
                account.setType(existingAccount.getType());
                account.setState(existingAccount.getState());
                accountsToBeUpdated.add(account);
            } else {
                accountServiceImp.validateAndSetAccountLevel(account);
                account.setState(AccountState.ACTIVE);
                accountsToBeCreated.add(account);
            }
        }
        // set id to accountsToBeCreated by saving
        accountsToBeCreated = accountMongoRepository.saveAll(accountsToBeCreated);
        //clear the map
        codeMap.clear();

        // merge accountsToBeUpdated and accountsToBeCreated to accounts
        accounts.clear();
        accounts.addAll(accountsToBeUpdated);
        accounts.addAll(accountsToBeCreated);

        // set level and set code to account map
        Map<String, Account> accountMap = new HashMap<>();
        for (Account account : accounts) {
            accountMap.put(account.getCode(), account);
        }
        // set parentId f for accountsToBeCreated
        for (Account account : accountsToBeCreated) {
            int parentLevel = account.getLevel() - 1;
            if (parentLevel > 0) {
                String parentCode = account.getCode().substring(0, GlobalConst.ACCOUNT_Code_LENGTH[parentLevel - 1]);
                Account parentAccount = accountMap.get(parentCode);
                if (parentAccount != null) {
                    account.setParentId(parentAccount.getId());
                } else {
                    throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "编码错误，父级科目不存在。");
                }
            }
        }

        //clear the map
        accountMap.clear();
        // set isLeaf for accountsToBeCreated
        for (Account account : accountsToBeCreated) {

            Account sonAccount = accounts.stream()
                    .filter(a -> a.getParentId()!=null && a.getParentId().equals(account.getId()))
                    .findFirst()
                    .orElse(null);
            if (sonAccount == null) {
                account.setLeaf(true);

            }
        }
        accountMongoRepository.saveAll(accountsToBeCreated);
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

}