package com.skyflytech.accountservice.core.journalEntry.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.skyflytech.accountservice.core.account.model.AccountingDirection;
import com.skyflytech.accountservice.core.report.model.AccountingOperation;

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
