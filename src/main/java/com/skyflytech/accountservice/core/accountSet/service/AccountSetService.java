package com.skyflytech.accountservice.core.accountSet.service;

import com.skyflytech.accountservice.core.accountSet.model.AccountSet;
import com.skyflytech.accountservice.security.model.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * @Author pzr
 * @date:2024-09-27-18:23
 * @Description:
 **/
public interface AccountSetService {

    /**
     * 创建账套
     * @param accountSet 要创建的账套
     * @param userName 用户名
     * @return 创建后的账套
     */
    AccountSet createAccountSet(AccountSet accountSet, String userName);

    /**
     * 获取所有账套
     * @return 所有账套列表
     */
    List<AccountSet> getAllAccountSets();

    /**
     * 删除账套
     * @param id 要删除的账套ID
     * @param user 当前用户
     * @return 更新后的用户对象
     */
    User deleteAccountSet(String id, User user);

    /**
     * 切换账套
     * @param id 要切换到的账套ID
     */
    void switchAccountSet(String id);

    /**
     * 根据ID列表获取账套
     * @param ids 账套ID列表
     * @return 匹配的账套列表
     */
    List<AccountSet> getAccountSetsByIds(List<String> ids);

    /**
     * 根据ID获取账套
     * @param id 账套ID
     * @return 匹配的账套
     */
    AccountSet getAccountSetById(String id);

    void initializeOpeningBalances(String accountSetId, Map<String, BigDecimal> openingBalances);
}
