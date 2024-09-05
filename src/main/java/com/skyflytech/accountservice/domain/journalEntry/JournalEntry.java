package com.skyflytech.accountservice.domain.journalEntry;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@Document(collection = "journalEntries")
@CompoundIndex(name = "accountSet_date_idx", def = "{'accountSetId': 1, 'modifiedDate': 1}")
public class JournalEntry  {

    @Id
    private String id;

    private String accountSetId;

    private LocalDate createdDate;


    private LocalDate modifiedDate;

    private String voucherWord;

    private int voucherNum;
    private List<String> attachmentIds;

    private String bookkeeper;

    private String auditor;

    private String accountingSupervisor;

    private Set<String> transactionIds=new HashSet<>();


}
