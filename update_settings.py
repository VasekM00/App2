import re

with open('app/src/main/java/com/example/ui/tabs/SettingsTab.kt', 'r') as f:
    content = f.read()

# Add BooleanSettingField at the end
boolean_field_code = """
@Composable
private fun BooleanSettingField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = BrandTeal, checkedTrackColor = BrandTeal.copy(alpha = 0.5f))
        )
    }
}
"""

if "private fun BooleanSettingField" not in content:
    content += boolean_field_code

# The new settings blocks
new_settings = """
        // Vaclav Income Settings
        SettingsGroupCard(title = "Vaclav Income Settings") {
            NumberSettingField(label = "Salary (CZK)", value = s.vSalary, onValueChange = { onUpdateSettings(s.copy(vSalary = it)) })
            NumberSettingField(label = "Annual September Raise (CZK)", value = s.vRaiseAnnual, onValueChange = { onUpdateSettings(s.copy(vRaiseAnnual = it)) })
            NumberSettingField(label = "Annual Bonus (CZK)", value = s.vBonusAnnual, onValueChange = { onUpdateSettings(s.copy(vBonusAnnual = it)) })
            NumberSettingField(label = "Meal Vouchers Monthly (CZK)", value = s.vMealVouchersMonthly, onValueChange = { onUpdateSettings(s.copy(vMealVouchersMonthly = it)) })
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // Eleonora Income Settings
        SettingsGroupCard(title = "Eleonora Income Settings") {
            NumberSettingField(label = "Return Year", value = s.eReturnYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(eReturnYear = it.toInt())) })
            NumberSettingField(label = "Starting Salary (CZK)", value = s.eStartingSalary, onValueChange = { onUpdateSettings(s.copy(eStartingSalary = it)) })
            NumberSettingField(label = "Annual Bonus (CZK)", value = s.eBonusAnnual, onValueChange = { onUpdateSettings(s.copy(eBonusAnnual = it)) })
            NumberSettingField(label = "Salary Growth (%)", value = s.eSalaryGrowthPct, onValueChange = { onUpdateSettings(s.copy(eSalaryGrowthPct = it)) })
            NumberSettingField(label = "Reinvested (%)", value = s.eReinvestedPct, onValueChange = { onUpdateSettings(s.copy(eReinvestedPct = it)) })
            NumberSettingField(label = "Parental Allowance Monthly (CZK)", value = s.eParentalAllowanceMonthly, onValueChange = { onUpdateSettings(s.copy(eParentalAllowanceMonthly = it)) })
            NumberSettingField(label = "Lecturing Monthly (CZK)", value = s.eLecturingMonthly, onValueChange = { onUpdateSettings(s.copy(eLecturingMonthly = it)) })
            BooleanSettingField(label = "Include Lecturing Income", checked = s.eIncludeLecturing, onCheckedChange = { onUpdateSettings(s.copy(eIncludeLecturing = it)) })
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Gifts, Savings & Lump Sums
        SettingsGroupCard(title = "Gifts, Savings & Lump Sums") {
            NumberSettingField(label = "Family Gift Monthly (CZK)", value = s.familyGiftMonthly, onValueChange = { onUpdateSettings(s.copy(familyGiftMonthly = it)) })
            NumberSettingField(label = "Family Savings Monthly (CZK)", value = s.familySavingsMonthly, onValueChange = { onUpdateSettings(s.copy(familySavingsMonthly = it)) })
            NumberSettingField(label = "Lump Sum Year", value = s.lumpSumYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(lumpSumYear = it.toInt())) })
            NumberSettingField(label = "Lump Sum Amount (CZK)", value = s.lumpSumAmount, onValueChange = { onUpdateSettings(s.copy(lumpSumAmount = it)) })
            BooleanSettingField(label = "Include Lump Sum", checked = s.lumpSumInclude, onCheckedChange = { onUpdateSettings(s.copy(lumpSumInclude = it)) })
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Investment Balances & Contributions
        SettingsGroupCard(title = "Investment Balances & Setup") {
            NumberSettingField(label = "DPS Balance Current (CZK)", value = s.dpsBalanceCurrent, onValueChange = { onUpdateSettings(s.copy(dpsBalanceCurrent = it)) })
            NumberSettingField(label = "DIP Balance Current (CZK)", value = s.dipBalanceCurrent, onValueChange = { onUpdateSettings(s.copy(dipBalanceCurrent = it)) })
            NumberSettingField(label = "Portu Monthly DCA (CZK)", value = s.portuDcaMonthly, onValueChange = { onUpdateSettings(s.copy(portuDcaMonthly = it)) })
            NumberSettingField(label = "Portfolio Nominal Return (%)", value = s.portfolioNominalReturnPct, onValueChange = { onUpdateSettings(s.copy(portfolioNominalReturnPct = it)) })
            NumberSettingField(label = "DPS Own Contribution (CZK)", value = s.dpsOwnContributionMonthly, onValueChange = { onUpdateSettings(s.copy(dpsOwnContributionMonthly = it)) })
            NumberSettingField(label = "DPS Gross Return (%)", value = s.dpsGrossReturnPct, onValueChange = { onUpdateSettings(s.copy(dpsGrossReturnPct = it)) })
            NumberSettingField(label = "DPS Annual Fee (%)", value = s.dpsAnnualFeePct, onValueChange = { onUpdateSettings(s.copy(dpsAnnualFeePct = it)) })
            NumberSettingField(label = "DIP Monthly Contribution (CZK)", value = s.dipContributionMonthly, onValueChange = { onUpdateSettings(s.copy(dipContributionMonthly = it)) })
            NumberSettingField(label = "Employer Retirement Annual (CZK)", value = s.employerRetirementAnnual, onValueChange = { onUpdateSettings(s.copy(employerRetirementAnnual = it)) })
        }

        Spacer(modifier = Modifier.height(16.dp))

        // FIRE Target Settings
        SettingsGroupCard(title = "FIRE Target & Parameters") {
            NumberSettingField(label = "Safe Withdrawal Rate SWR (%)", value = s.safeWithdrawalRatePct, onValueChange = { onUpdateSettings(s.copy(safeWithdrawalRatePct = it)) })
            NumberSettingField(label = "Safety Buffer (%)", value = s.safetyBufferPct, onValueChange = { onUpdateSettings(s.copy(safetyBufferPct = it)) })
            NumberSettingField(label = "FIRE Target Override (CZK) [0=auto]", value = s.fireTargetOverride, onValueChange = { onUpdateSettings(s.copy(fireTargetOverride = it)) })
            NumberSettingField(label = "State Pension Monthly (CZK)", value = s.statePensionMonthly, onValueChange = { onUpdateSettings(s.copy(statePensionMonthly = it)) })
            NumberSettingField(label = "State Pension Age", value = s.statePensionAge.toDouble(), onValueChange = { onUpdateSettings(s.copy(statePensionAge = it.toInt())) })
            NumberSettingField(label = "Lifestyle Cost at FIRE (CZK/mo)", value = s.lifestyleCostAtFireMonthly, onValueChange = { onUpdateSettings(s.copy(lifestyleCostAtFireMonthly = it)) })
            NumberSettingField(label = "Monte Carlo N (runs)", value = s.monteCarloN.toDouble(), onValueChange = { onUpdateSettings(s.copy(monteCarloN = it.toInt())) })
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tax Parameters
        SettingsGroupCard(title = "Tax Parameters") {
            NumberSettingField(label = "Tax Rate (%)", value = s.taxRatePct, onValueChange = { onUpdateSettings(s.copy(taxRatePct = it)) })
            NumberSettingField(label = "Tax Deduction Ceiling Annual (CZK)", value = s.taxDeductionCeilingAnnual, onValueChange = { onUpdateSettings(s.copy(taxDeductionCeilingAnnual = it)) })
            NumberSettingField(label = "Spouse Tax Credit Annual (CZK)", value = s.spouseTaxCreditAnnual, onValueChange = { onUpdateSettings(s.copy(spouseTaxCreditAnnual = it)) })
            NumberSettingField(label = "Child Tax Bonus Annual (CZK)", value = s.childTaxBonusAnnual, onValueChange = { onUpdateSettings(s.copy(childTaxBonusAnnual = it)) })
            BooleanSettingField(label = "Include Spouse Credit", checked = s.includeSpouseCredit, onCheckedChange = { onUpdateSettings(s.copy(includeSpouseCredit = it)) })
            BooleanSettingField(label = "Has Child Under 3", checked = s.hasChildUnder3, onCheckedChange = { onUpdateSettings(s.copy(hasChildUnder3 = it)) })
            NumberSettingField(label = "Min Wage Monthly (CZK)", value = s.minWageMonthly, onValueChange = { onUpdateSettings(s.copy(minWageMonthly = it)) })
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // Child Settings & Expenses
        SettingsGroupCard(title = "Child Settings & Expenses") {
            BooleanSettingField(label = "Enable Child Expenses", checked = s.childExpensesEnabled, onCheckedChange = { onUpdateSettings(s.copy(childExpensesEnabled = it)) })
            NumberSettingField(label = "Child Birth Year", value = s.childBirthYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(childBirthYear = it.toInt())) })
            NumberSettingField(label = "Toddler Monthly (CZK)", value = s.childToddlerMonthly, onValueChange = { onUpdateSettings(s.copy(childToddlerMonthly = it)) })
            NumberSettingField(label = "Preschool Monthly (CZK)", value = s.childPreschoolMonthly, onValueChange = { onUpdateSettings(s.copy(childPreschoolMonthly = it)) })
            NumberSettingField(label = "School Monthly (CZK)", value = s.childSchoolMonthly, onValueChange = { onUpdateSettings(s.copy(childSchoolMonthly = it)) })
            NumberSettingField(label = "Teen Monthly (CZK)", value = s.childTeenMonthly, onValueChange = { onUpdateSettings(s.copy(childTeenMonthly = it)) })
            NumberSettingField(label = "Uni Monthly (CZK)", value = s.childUniMonthly, onValueChange = { onUpdateSettings(s.copy(childUniMonthly = it)) })
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Living Costs Settings
        SettingsGroupCard(title = "Monthly Living Expenses (CZK)") {
            NumberSettingField(label = "Rent", value = s.rentMonthly, onValueChange = { onUpdateSettings(s.copy(rentMonthly = it)) })
            NumberSettingField(label = "Rent Growth (%)", value = s.rentGrowthPct, onValueChange = { onUpdateSettings(s.copy(rentGrowthPct = it)) })
            NumberSettingField(label = "Groceries", value = s.groceriesMonthly, onValueChange = { onUpdateSettings(s.copy(groceriesMonthly = it)) })
            NumberSettingField(label = "Cafes & Restaurants", value = s.cafesMonthly, onValueChange = { onUpdateSettings(s.copy(cafesMonthly = it)) })
            NumberSettingField(label = "Therapy / Physio", value = s.therapyMonthly, onValueChange = { onUpdateSettings(s.copy(therapyMonthly = it)) })
            NumberSettingField(label = "Charity", value = s.charityMonthly, onValueChange = { onUpdateSettings(s.copy(charityMonthly = it)) })
            NumberSettingField(label = "Entertainment", value = s.entertainmentMonthly, onValueChange = { onUpdateSettings(s.copy(entertainmentMonthly = it)) })
            NumberSettingField(label = "Transport", value = s.transportMonthly, onValueChange = { onUpdateSettings(s.copy(transportMonthly = it)) })
            NumberSettingField(label = "Subscriptions", value = s.subscriptionsMonthly, onValueChange = { onUpdateSettings(s.copy(subscriptionsMonthly = it)) })
            NumberSettingField(label = "Other Discretionary", value = s.otherDiscretionaryMonthly, onValueChange = { onUpdateSettings(s.copy(otherDiscretionaryMonthly = it)) })
        }"""

pattern = re.compile(r'\s*// Vaclav Income Settings.*?// Living Costs Settings.*?\n        }', re.DOTALL)
new_content = pattern.sub(new_settings, content)

with open('app/src/main/java/com/example/ui/tabs/SettingsTab.kt', 'w') as f:
    f.write(new_content)

print("Done")
