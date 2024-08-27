package com.skyflytech.accountservice.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "accounting_periods")
public class AccountingPeriod {
    @Id
    private String id;

    @NotBlank(message = "会计期间名称不能为空")
    private String name;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    @NotNull(message = "账套ID不能为空")
    @Indexed
    private String accountSetId;

    private boolean isClosed;

    @NotNull(message = "期初余额不能为空")
    private Map<String, BigDecimal> openingBalances;

    @NotNull(message = "期末余额不能为空")
    private Map<String, BigDecimal> closingBalances;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ... 其他字段和方法 ...
}