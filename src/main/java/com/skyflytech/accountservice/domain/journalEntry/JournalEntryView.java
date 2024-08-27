package com.skyflytech.accountservice.domain.journalEntry;

import com.skyflytech.accountservice.domain.Transaction;
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
