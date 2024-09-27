package com.skyflytech.accountservice.core.transaction.model;

import com.skyflytech.accountservice.core.account.model.AccountingDirection;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

@Data
@NoArgsConstructor
@Document(collection = "transactions")
@CompoundIndex(name = "account_date_idx", def = "{'accountId': 1, 'modifiedDate': 1}")
public class Transaction  {

    @Id
    private String id;

    private String accountId;

    private String accountSetId;

    private LocalDate createdDate;

    private LocalDate modifiedDate;

    private String description;

    private String vouchWord;
    private AccountingDirection balanceDirection;

    private BigDecimal debit = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private BigDecimal credit = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    // Setter for debit with rounding to 2 decimal places
    public void setDebit(BigDecimal debit) {
        this.debit = Objects.requireNonNullElse(debit, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    // Setter for credit with rounding to 2 decimal places
    public void setCredit(BigDecimal credit) {
        this.credit = Objects.requireNonNullElse(credit, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

}
