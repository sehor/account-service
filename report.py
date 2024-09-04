class IncomeStatement:
    def __init__(self):
        # 一、营业收入
        self.operating_revenue = 0
        # 营业成本
        self.operating_cost = 0
        # 税金及附加
        self.taxes_and_surcharges = 0
        # 税金及附加的细分项目
        self.tax_details = {
            "consumption_tax": 0,  # 消费税
            "business_tax": 0,  # 营业税
            "urban_maintenance_tax": 0,  # 城市维护建设税
            "resource_tax": 0,  # 资源税
            "land_appreciation_tax": 0,  # 土地增值税
            "property_related_tax": 0,  # 城镇土地使用税、房产税、车船税、印花税
            "education_surcharge": 0,  # 教育费附加、矿产资源补偿税、排污费
        }
        # 销售费用
        self.selling_expenses = 0
        self.selling_expenses_details = {
            "repair_cost": 0,  # 商品维修费
            "advertising_cost": 0,  # 广告费和业务宣传费
        }
        # 管理费用
        self.administrative_expenses = 0
        self.administrative_expenses_details = {
            "setup_cost": 0,  # 开办费
            "entertainment_cost": 0,  # 业务招待费
            "research_cost": 0,  # 研究费用
        }
        # 财务费用
        self.financial_expenses = 0
        self.interest_expense = 0  # 利息费用（收入以负数表示）
        
        # 投资收益
        self.investment_income = 0
        
        # 营业外收入
        self.non_operating_income = 0
        self.government_grants = 0  # 政府补助
        
        # 营业外支出
        self.non_operating_expenses = 0
        self.non_operating_expenses_details = {
            "bad_debt_loss": 0,  # 坏账损失
            "long_term_bond_investment_loss": 0,  # 无法收回的长期债券投资损失
            "long_term_equity_investment_loss": 0,  # 无法收回的长期股权投资损失
            "force_majeure_loss": 0,  # 自然灾害等不可抗力因素造成的损失
            "tax_late_fee": 0,  # 税收滞纳金
        }
        
        # 所得税费用
        self.income_tax_expense = 0

    def calculate_gross_profit(self):
        """计算毛利"""
        return self.operating_revenue - self.operating_cost - self.taxes_and_surcharges

    def calculate_operating_profit(self):
        """计算营业利润"""
        return (self.calculate_gross_profit() - 
                self.selling_expenses - 
                self.administrative_expenses - 
                self.financial_expenses + 
                self.investment_income)

    def calculate_total_profit(self):
        """计算利润总额"""
        return (self.calculate_operating_profit() + 
                self.non_operating_income - 
                self.non_operating_expenses)

    def calculate_net_profit(self):
        """计算净利润"""
        return self.calculate_total_profit() - self.income_tax_expense

    def display(self):
        """显示利润表"""
        print("利润表")
        print(f"一、营业收入: {self.operating_revenue}")
        print(f"减：营业成本: {self.operating_cost}")
        print(f"    税金及附加: {self.taxes_and_surcharges}")
        print(f"    销售费用: {self.selling_expenses}")
        print(f"    管理费用: {self.administrative_expenses}")
        print(f"    财务费用: {self.financial_expenses}")
        print(f"加：投资收益: {self.investment_income}")
        print(f"二、营业利润: {self.calculate_operating_profit()}")
        print(f"加：营业外收入: {self.non_operating_income}")
        print(f"减：营业外支出: {self.non_operating_expenses}")
        print(f"三、利润总额: {self.calculate_total_profit()}")
        print(f"减：所得税费用: {self.income_tax_expense}")
        print(f"四、净利润: {self.calculate_net_profit()}")