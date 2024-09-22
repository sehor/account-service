package com.skyflytech.accountservice.domain.journalEntry;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.skyflytech.accountservice.domain.account.AccountingDirection;
import com.skyflytech.accountservice.report.AccountingOperation;

import lombok.Data;


@Data
@Document(collection = "auto_entry_templates")
public class AutoEntryTemplate {
    @Id
    private String id;
    private String accountSetId;
    private String name;
    private String description;
    //确定是借方或者贷方的数据是根据对方科目的数据生成的
    private AccountingDirection otherSide;
    private String otherSideAccountId;
    private List<AccountingOperation> operations;
}
