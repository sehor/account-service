package com.skyflytech.accountservice.domain;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @Author pzr
 * @date:2024-08-24-6:00
 * @Description:
 **/

@Data
public class PreviousTotal {

   private BigDecimal totalDebit;

   private BigDecimal totalCredit;

}
