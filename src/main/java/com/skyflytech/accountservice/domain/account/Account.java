package com.skyflytech.accountservice.domain.account;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.skyflytech.accountservice.security.AccountSetIdAware;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Data
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@Document(collection = "accounts")
@CompoundIndex(name = "accountSet_date_idx", def = "{'accountSetId': 1}")
public class Account implements AccountSetIdAware{

    @Id
    private String id;
    private String code;

    private String accountSetId;

    private String name;

    private AccountType type;

    private String parentId;
    private AccountingDirection balanceDirection;

    private AccountState state;  // 账号状态

    private BigDecimal initialBalance=BigDecimal.ZERO;

    private Integer level;

    public Account(String code, String name, String accountSetId, AccountType type, String parentId, AccountingDirection balanceDirection, AccountState state) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.parentId = parentId;
        this.balanceDirection = balanceDirection;
        this.state = state;
    }

    public Account(String accountId, String name,String accountSetId, AccountType type, AccountingDirection balanceDirection, AccountState state) {
        this(accountId, name, accountSetId,type, null, balanceDirection, state);
    }

    public void setInitialBalance(BigDecimal initialBalance){
        this.initialBalance= Objects.requireNonNullElse(initialBalance,BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

}
