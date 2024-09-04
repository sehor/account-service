package report;

import java.util.HashMap;
import java.util.Map;

public class IncomeStatement {
    // 一、营业收入
    private double operatingRevenue;
    // 营业成本
    private double operatingCost;
    // 税金及附加
    private double taxesAndSurcharges;
    // 税金及附加的细分项目
    private Map<String, Double> taxDetails;
    // 销售费用
    private double sellingExpenses;
    private Map<String, Double> sellingExpensesDetails;
    // 管理费用
    private double administrativeExpenses;
    private Map<String, Double> administrativeExpensesDetails;
    // 财务费用
    private double financialExpenses;
    private double interestExpense;  // 利息费用（收入以负数表示）
    // 投资收益
    private double investmentIncome;
    // 营业外收入
    private double nonOperatingIncome;
    private double governmentGrants;  // 政府补助
    // 营业外支出
    private double nonOperatingExpenses;
    private Map<String, Double> nonOperatingExpensesDetails;
    // 所得税费用
    private double incomeTaxExpense;

    public IncomeStatement() {
        // 初始化所有字段
        this.operatingRevenue = 0;
        this.operatingCost = 0;
        this.taxesAndSurcharges = 0;
        
        this.taxDetails = new HashMap<>();
        this.taxDetails.put("consumptionTax", 0.0);  // 消费税
        this.taxDetails.put("businessTax", 0.0);  // 营业税
        this.taxDetails.put("urbanMaintenanceTax", 0.0);  // 城市维护建设税
        this.taxDetails.put("resourceTax", 0.0);  // 资源税
        this.taxDetails.put("landAppreciationTax", 0.0);  // 土地增值税
        this.taxDetails.put("propertyRelatedTax", 0.0);  // 城镇土地使用税、房产税、车船税、印花税
        this.taxDetails.put("educationSurcharge", 0.0);  // 教育费附加、矿产资源补偿税、排污费

        this.sellingExpenses = 0;
        this.sellingExpensesDetails = new HashMap<>();
        this.sellingExpensesDetails.put("repairCost", 0.0);  // 商品维修费
        this.sellingExpensesDetails.put("advertisingCost", 0.0);  // 广告费和业务宣传费

        this.administrativeExpenses = 0;
        this.administrativeExpensesDetails = new HashMap<>();
        this.administrativeExpensesDetails.put("setupCost", 0.0);  // 开办费
        this.administrativeExpensesDetails.put("entertainmentCost", 0.0);  // 业务招待费
        this.administrativeExpensesDetails.put("researchCost", 0.0);  // 研究费用

        this.financialExpenses = 0;
        this.interestExpense = 0;

        this.investmentIncome = 0;

        this.nonOperatingIncome = 0;
        this.governmentGrants = 0;

        this.nonOperatingExpenses = 0;
        this.nonOperatingExpensesDetails = new HashMap<>();
        this.nonOperatingExpensesDetails.put("badDebtLoss", 0.0);  // 坏账损失
        this.nonOperatingExpensesDetails.put("longTermBondInvestmentLoss", 0.0);  // 无法收回的长期债券投资损失
        this.nonOperatingExpensesDetails.put("longTermEquityInvestmentLoss", 0.0);  // 无法收回的长期股权投资损失
        this.nonOperatingExpensesDetails.put("forceMajeureLoss", 0.0);  // 自然灾害等不可抗力因素造成的损失
        this.nonOperatingExpensesDetails.put("taxLateFee", 0.0);  // 税收滞纳金

        this.incomeTaxExpense = 0;
    }

    // 计算毛利
    public double calculateGrossProfit() {
        return this.operatingRevenue - this.operatingCost - this.taxesAndSurcharges;
    }

    // 计算营业利润
    public double calculateOperatingProfit() {
        return calculateGrossProfit() - this.sellingExpenses - 
               this.administrativeExpenses - this.financialExpenses + 
               this.investmentIncome;
    }

    // 计算利润总额
    public double calculateTotalProfit() {
        return calculateOperatingProfit() + this.nonOperatingIncome - 
               this.nonOperatingExpenses;
    }

    // 计算净利润
    public double calculateNetProfit() {
        return calculateTotalProfit() - this.incomeTaxExpense;
    }

    // 显示利润表
    public void display() {
        System.out.println("利润表");
        System.out.println("一、营业收入: " + this.operatingRevenue);
        System.out.println("减：营业成本: " + this.operatingCost);
        System.out.println("    税金及附加: " + this.taxesAndSurcharges);
        System.out.println("    销售费用: " + this.sellingExpenses);
        System.out.println("    管理费用: " + this.administrativeExpenses);
        System.out.println("    财务费用: " + this.financialExpenses);
        System.out.println("加：投资收益: " + this.investmentIncome);
        System.out.println("二、营业利润: " + calculateOperatingProfit());
        System.out.println("加：营业外收入: " + this.nonOperatingIncome);
        System.out.println("减：营业外支出: " + this.nonOperatingExpenses);
        System.out.println("三、利润总额: " + calculateTotalProfit());
        System.out.println("减：所得税费用: " + this.incomeTaxExpense);
        System.out.println("四、净利润: " + calculateNetProfit());
    }

    // 这里应该添加所有字段的getter和setter方法
    // 为了简洁，这里省略了这些方法
}