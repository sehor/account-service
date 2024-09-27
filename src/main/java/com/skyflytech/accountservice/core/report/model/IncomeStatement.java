package com.skyflytech.accountservice.core.report.model;

import com.skyflytech.accountservice.core.accountingPeriod.model.AccountingPeriod;
import lombok.Data;
import lombok.NonNull;
import java.math.BigDecimal;


@Data
public class IncomeStatement {
    @NonNull
    private String accountSetId;
    @NonNull
    private AccountingPeriod beginPeriod;
    @NonNull
    private AccountingPeriod endPeriod;

    // 主要项目
    private ReportItem operatingRevenue;
    private ReportItem operatingCost;
    private ReportItem taxesAndSurcharges;
    private ReportItem sellingExpenses;
    private ReportItem administrativeExpenses;
    private ReportItem financialExpenses;
    private ReportItem investmentIncome;
    private ReportItem nonOperatingIncome;
    private ReportItem nonOperatingExpenses;
    private ReportItem incomeTaxExpense;


    // 细分项目
    private ReportItem consumptionTax;
    private ReportItem businessTax;
    private ReportItem urbanMaintenanceTax;
    private ReportItem resourceTax;
    private ReportItem landAppreciationTax;
    private ReportItem propertyRelatedTax;
    private ReportItem educationSurcharge;
    private ReportItem repairCost;
    private ReportItem advertisingCost;
    private ReportItem setupCost;
    private ReportItem entertainmentCost;
    private ReportItem researchCost;
    private ReportItem interestExpense;
    private ReportItem governmentGrants;
    private ReportItem badDebtLoss;
    private ReportItem longTermBondInvestmentLoss;
    private ReportItem longTermEquityInvestmentLoss;
    private ReportItem forceMajeureLoss;
    private ReportItem taxLateFee;

    
    // 计算项目
    private ReportItem grossProfit;
    private ReportItem operatingProfit;
    private ReportItem totalProfit;
    private ReportItem netProfit;

    // 毛利
    public ReportItem getGrossProfit() {
        BigDecimal amount = operatingRevenue.getAmount()
                .subtract(operatingCost.getAmount())
                .subtract(taxesAndSurcharges.getAmount());
        grossProfit.setAmount(amount);
        return grossProfit;
    }

    public ReportItem getOperatingProfit() {
        BigDecimal amount = getGrossProfit().getAmount()
                .subtract(sellingExpenses.getAmount())
                .subtract(administrativeExpenses.getAmount())
                .subtract(financialExpenses.getAmount())
                .add(investmentIncome.getAmount());
        operatingProfit.setAmount(amount);
        return operatingProfit;
    }

    public ReportItem getTotalProfit() {
        BigDecimal amount = getOperatingProfit().getAmount()
                .add(nonOperatingIncome.getAmount())
                .subtract(nonOperatingExpenses.getAmount());
        totalProfit.setAmount(amount);
        return totalProfit;
    }

    public ReportItem getNetProfit() {
        BigDecimal amount = getTotalProfit().getAmount()
                .subtract(incomeTaxExpense.getAmount());
        netProfit.setAmount(amount);
        return netProfit;
    }

    public void display() {
        System.out.println("利润表");
        // 使用反射获取所有 ReportItem 类型的字段并打印
        for (java.lang.reflect.Field field : this.getClass().getDeclaredFields()) {
            if (field.getType() == ReportItem.class) {
                try {
                    ReportItem item = (ReportItem) field.get(this);
                    if (item != null) {
                        System.out.println(item.toString());
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
