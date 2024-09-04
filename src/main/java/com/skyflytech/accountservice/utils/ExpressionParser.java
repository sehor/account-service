package com.skyflytech.accountservice.utils;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.HashMap;
import java.util.Map;

public class ExpressionParser {
    private Map<String, Double> variables;

    public ExpressionParser() {
        this.variables = new HashMap<>();
    }

    public void setVariable(String name, double value) {
        variables.put(name, value);
    }

    public double evaluate(String expression) {
        try {
            // 预处理：替换中文括号为英文括号
            expression = expression.replace('（', '(').replace('）', ')');
            
            ExpressionBuilder expressionBuilder = new ExpressionBuilder(expression);
            for (String variableName : variables.keySet()) {
                expressionBuilder.variable(variableName);
            }
            
            Expression exp = expressionBuilder.build();
            for (Map.Entry<String, Double> entry : variables.entrySet()) {
                exp.setVariable(entry.getKey(), entry.getValue());
            }
            
            return exp.evaluate();
        } catch (Exception e) {
            System.out.println("计算表达式时出错: " + e.getMessage());
            return Double.NaN;
        }
    }

    public static void main(String[] args) {
        ExpressionParser calculator = new ExpressionParser();
        
        calculator.setVariable("现金", 1000);
        calculator.setVariable("银行存款", 5000);
        calculator.setVariable("应收账款", 2000);
        
        String expression = "现金 + （银行存款 - 应收账款） * 2";
        double result = calculator.evaluate(expression);
        
        if (!Double.isNaN(result)) {
            System.out.println("结果: " + result);
        }
    }
}
