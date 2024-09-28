package com.skyflytech.accountservice.core.account.service;

import com.skyflytech.accountservice.core.account.model.Account;

import java.util.List;

/**
 * @Author pzr
 * @date:2024-09-27-18:21
 * @Description:
 **/
public interface AccountService {

    /**
     * 获取所有账户
     * @param accountSetId 账套ID
     * @return 账户列表
     */
    List<Account> getAllAccounts(String accountSetId);

    /**
     * 根据ID获取账户
     * @param id 账户ID
     * @return 账户对象
     */
    Account getAccountById(String id);

    /**
     * 创建账户
     * @param account 要创建的账户对象
     * @return 创建后的账户对象
     */
    Account createAccount(Account account);

    /**
     * 更新账户
     * @param account 要更新的账户对象
     * @return 更新后的账户对象
     */
    Account updateAccount(Account account);

    /**
     * 删除账户
     * @param id 要删除的账户ID
     */
    void deleteAccount(String id);

    /**
     * 模糊搜索账户
     * @param search 搜索关键词
     * @param accountSetId 账套ID
     * @return 匹配的账户列表
     */
    List<Account> searchAccounts(String search, String accountSetId);


    /**
     * 查找账户及其所有祖先
     * @param accountId 账户ID
     * @return 包含账户及其所有祖先的列表
     **/
    List<Account> findAccountAndAllAncestors(String accountId);

    /**
     * 根据账套ID删除所有账户
     * @param accountSetId 账套ID
     */
    void deleteAccountsByAccountSetId(String accountSetId);
}
