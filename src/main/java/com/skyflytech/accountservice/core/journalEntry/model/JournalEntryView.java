package com.skyflytech.accountservice.core.journalEntry.model;

import com.skyflytech.accountservice.core.transaction.model.Transaction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JournalEntryView {

private JournalEntry journalEntry;
private List<Transaction> transactions;
}
