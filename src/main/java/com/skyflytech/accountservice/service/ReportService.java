package com.skyflytech.accountservice.service;

import com.skyflytech.accountservice.domain.AccountAmountHolder;
import com.skyflytech.accountservice.domain.AccountingPeriod;
import com.skyflytech.accountservice.report.AccountingFormula;
import com.skyflytech.accountservice.report.AccountingOperation;
import com.skyflytech.accountservice.report.DataType;
import com.skyflytech.accountservice.report.IncomeStatement;
import com.skyflytech.accountservice.report.ReportItem;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class ReportService {
    public ReportService() {
    }

    public void initializeDefaultIncomeStatementItems(IncomeStatement incomeStatement) {
        // 主要项目 (level 1)
        incomeStatement.setOperatingRevenue(new ReportItem("operatingRevenue", "营业收入", 1));
        incomeStatement.setOperatingCost(new ReportItem("operatingCost", "营业成本", 1));
        incomeStatement.setTaxesAndSurcharges(new ReportItem("taxesAndSurcharges", "税金及附加", 1));
        incomeStatement.setSellingExpenses(new ReportItem("sellingExpenses", "销售费用", 1));
        incomeStatement.setAdministrativeExpenses(new ReportItem("administrativeExpenses", "管理费用", 1));
        incomeStatement.setFinancialExpenses(new ReportItem("financialExpenses", "财务费用", 1));
        incomeStatement.setInvestmentIncome(new ReportItem("investmentIncome", "投资收益", 1));
        incomeStatement.setNonOperatingIncome(new ReportItem("nonOperatingIncome", "营业外收入", 1));
        incomeStatement.setNonOperatingExpenses(new ReportItem("nonOperatingExpenses", "营业外支出", 1));
        incomeStatement.setIncomeTaxExpense(new ReportItem("incomeTaxExpense", "所得税费用", 1));

        // 计算项目 (level 1)，初始化时 operations 列表为空
        incomeStatement.setGrossProfit(new ReportItem("grossProfit", "毛利", 1));
        incomeStatement.setOperatingProfit(new ReportItem("operatingProfit", "营业利润", 1));
        incomeStatement.setTotalProfit(new ReportItem("totalProfit", "利润总额", 1));
        incomeStatement.setNetProfit(new ReportItem("netProfit", "净利润", 1));

        // 细分项目 (level 2)
        incomeStatement.setConsumptionTax(new ReportItem("consumptionTax", "消费税", 2));
        incomeStatement.setBusinessTax(new ReportItem("businessTax", "营业税", 2));
        incomeStatement.setUrbanMaintenanceTax(new ReportItem("urbanMaintenanceTax", "城市维护建设税", 2));
        incomeStatement.setResourceTax(new ReportItem("resourceTax", "资源税", 2));
        incomeStatement.setLandAppreciationTax(new ReportItem("landAppreciationTax", "土地增值税", 2));
        incomeStatement.setPropertyRelatedTax(new ReportItem("propertyRelatedTax", "城镇土地使用税、房产税、车船税、印花税", 2));
        incomeStatement.setEducationSurcharge(new ReportItem("educationSurcharge", "教育费附加、矿产资源补偿税、排污费", 2));
        
        incomeStatement.setRepairCost(new ReportItem("repairCost", "商品维修费", 2));
        incomeStatement.setAdvertisingCost(new ReportItem("advertisingCost", "广告费和业务宣传费", 2));
        
        incomeStatement.setSetupCost(new ReportItem("setupCost", "开办费", 2));
        incomeStatement.setEntertainmentCost(new ReportItem("entertainmentCost", "业务招待费", 2));
        incomeStatement.setResearchCost(new ReportItem("researchCost", "研究费用", 2));
        
        incomeStatement.setInterestExpense(new ReportItem("interestExpense", "利息费用（收入以-号填列）", 2));
        
        incomeStatement.setGovernmentGrants(new ReportItem("governmentGrants", "政府补助", 2));
        
        incomeStatement.setBadDebtLoss(new ReportItem("badDebtLoss", "坏账损失", 2));
        incomeStatement.setLongTermBondInvestmentLoss(new ReportItem("longTermBondInvestmentLoss", "无法收回的长期债券投资损失", 2));
        incomeStatement.setLongTermEquityInvestmentLoss(new ReportItem("longTermEquityInvestmentLoss", "无法收回的长期股权投资损失", 2));
        incomeStatement.setForceMajeureLoss(new ReportItem("forceMajeureLoss", "自然灾害等不可抗力因素造成的损失", 2));
        incomeStatement.setTaxLateFee(new ReportItem("taxLateFee", "税收滞纳金", 2));
    }

    public void calculateIncomeStatement(IncomeStatement incomeStatement) {
        // 调用 getter 方法来触发计算并设置金额
        incomeStatement.getGrossProfit();
        incomeStatement.getOperatingProfit();
        incomeStatement.getTotalProfit();
        incomeStatement.getNetProfit();
    }
    
    public BigDecimal calculateFormula(AccountingFormula formula,AccountingPeriod accountingPeriod) {
        // 计算公式
        BigDecimal result = BigDecimal.ZERO;
        for (AccountingOperation operation : formula.getOperations()) {
            // 根据 operation 的类型进行计算
            switch (operation.getOperator().getSymbol()) {
                case "+":
                    result.add(getAccountAmount(accountingPeriod,operation.getAccountId(),operation.getDataType()));
                    break;
                case "-":
                    result.subtract(getAccountAmount(accountingPeriod,operation.getAccountId(),operation.getDataType()));
                    break;
                case "*":
                    result.multiply(getAccountAmount(accountingPeriod,operation.getAccountId(),operation.getDataType()));
                    break;
                case "/":
                    result.divide(getAccountAmount(accountingPeriod,operation.getAccountId(),operation.getDataType()));
                    break;
            }
        }
        return result;
    }

    private BigDecimal getAccountAmount(AccountingPeriod accountingPeriod,String accountId,DataType dataType){
        AccountAmountHolder accountAmountHolder=accountingPeriod.getAmountHolders().get(accountId);
        if(accountAmountHolder==null){
            return BigDecimal.ZERO;
        }
        switch(dataType){
            case DEBIT_TOTAL:
                return accountAmountHolder.getTotalDebit();
            case CREDIT_TOTAL:
                return accountAmountHolder.getTotalCredit();  
            case BALANCE:
                return accountAmountHolder.getBalance();
            default:
                return BigDecimal.ZERO;
        }
    }
}