package com.example.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.SettingsEntity
import com.example.domain.CustomExpenseItem
import com.example.domain.FullCalculationState
import com.example.domain.parseCustomExpenses
import com.example.domain.serializeCustomExpenses
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import java.util.UUID

@Composable
fun SettingsTab(
    state: FullCalculationState,
    onUpdateSettings: (SettingsEntity) -> Unit,
    onResetDefaults: () -> Unit,
    onClearAllData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val s = state.settings
    
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var newCategoryAmount by remember { mutableStateOf("") }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("General", "Income", "Investments", "Taxes & Family", "Expenses", "Data")

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_tab")
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.background
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // Base Settings
                    SettingsGroupCard(title = "General Settings") {
                        NumberSettingField(
                            label = "Base Year",
                            value = s.baseYear.toDouble(),
                            onValueChange = { onUpdateSettings(s.copy(baseYear = it.toInt())) }
                        )
                        NumberSettingField(
                            label = "Primary Age",
                            value = s.primaryAge.toDouble(),
                            onValueChange = { onUpdateSettings(s.copy(primaryAge = it.toInt())) }
                        )
                        NumberSettingField(
                            label = "CPI Inflation (%)",
                            value = s.cpiInflationPct,
                            onValueChange = { onUpdateSettings(s.copy(cpiInflationPct = it)) }
                        )
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
                }
                1 -> {
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
                }
                2 -> {
                    // Václav's Portfolio & Accounts
                    SettingsGroupCard(title = "Václav's Investments & Accounts") {
                        NumberSettingField(
                            label = "Portu / ETF Liquid Portfolio (CZK)",
                            value = s.liquidPortfolioCurrent,
                            onValueChange = { onUpdateSettings(s.copy(liquidPortfolioCurrent = it)) },
                            testTagStr = "input_liquid_port"
                        )
                        NumberSettingField(label = "Portu / ETF Monthly DCA (CZK)", value = s.portuDcaMonthly, onValueChange = { onUpdateSettings(s.copy(portuDcaMonthly = it)) })
                        NumberSettingField(label = "DPS Balance Current (CZK)", value = s.dpsBalanceCurrent, onValueChange = { onUpdateSettings(s.copy(dpsBalanceCurrent = it)) })
                        NumberSettingField(label = "DPS Monthly Own Contribution (CZK)", value = s.dpsOwnContributionMonthly, onValueChange = { onUpdateSettings(s.copy(dpsOwnContributionMonthly = it)) })
                        NumberSettingField(label = "DIP Balance Current (CZK)", value = s.dipBalanceCurrent, onValueChange = { onUpdateSettings(s.copy(dipBalanceCurrent = it)) })
                        NumberSettingField(label = "DIP Monthly Contribution (CZK)", value = s.dipContributionMonthly, onValueChange = { onUpdateSettings(s.copy(dipContributionMonthly = it)) })
                        NumberSettingField(label = "Employer Retirement Annual (CZK)", value = s.employerRetirementAnnual, onValueChange = { onUpdateSettings(s.copy(employerRetirementAnnual = it)) })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Eleonora's Portfolio & Accounts
                    SettingsGroupCard(title = "Eleonora's Investments & Accounts") {
                        NumberSettingField(
                            label = "Eleonora's Portu / ETF Liquid Portfolio (CZK)",
                            value = s.eLiquidPortfolioCurrent,
                            onValueChange = { onUpdateSettings(s.copy(eLiquidPortfolioCurrent = it)) }
                        )
                        NumberSettingField(
                            label = "Eleonora's Portu / ETF Monthly DCA (CZK)",
                            value = s.ePortuDcaMonthly,
                            onValueChange = { onUpdateSettings(s.copy(ePortuDcaMonthly = it)) }
                        )
                        NumberSettingField(
                            label = "Eleonora's DPS Balance Current (CZK)",
                            value = s.eDpsBalanceCurrent,
                            onValueChange = { onUpdateSettings(s.copy(eDpsBalanceCurrent = it)) }
                        )
                        NumberSettingField(
                            label = "Eleonora's DPS Monthly Contribution (CZK)",
                            value = s.eDpsOwnContributionMonthly,
                            onValueChange = { onUpdateSettings(s.copy(eDpsOwnContributionMonthly = it)) }
                        )
                        NumberSettingField(
                            label = "Eleonora's DIP Balance Current (CZK)",
                            value = s.eDipBalanceCurrent,
                            onValueChange = { onUpdateSettings(s.copy(eDipBalanceCurrent = it)) }
                        )
                        NumberSettingField(
                            label = "Eleonora's DIP Monthly Contribution (CZK)",
                            value = s.eDipContributionMonthly,
                            onValueChange = { onUpdateSettings(s.copy(eDipContributionMonthly = it)) }
                        )
                        NumberSettingField(
                            label = "Eleonora's Employer Retirement Annual (CZK)",
                            value = s.eEmployerRetirementAnnual,
                            onValueChange = { onUpdateSettings(s.copy(eEmployerRetirementAnnual = it)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Emergency Reserve & Cash
                    SettingsGroupCard(title = "Cash & Emergency Reserve") {
                        NumberSettingField(
                            label = "Savings & Bank Accounts (CZK)",
                            value = s.emergencyReserveCurrent,
                            onValueChange = { onUpdateSettings(s.copy(emergencyReserveCurrent = it)) }
                        )
                        NumberSettingField(
                            label = "Savings / Reserve Target (CZK)",
                            value = s.emergencyReserveTarget,
                            onValueChange = { onUpdateSettings(s.copy(emergencyReserveTarget = it)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Investment Parameters
                    SettingsGroupCard(title = "Global Return & Fee Assumptions") {
                        NumberSettingField(label = "Portfolio Nominal Return (%)", value = s.portfolioNominalReturnPct, onValueChange = { onUpdateSettings(s.copy(portfolioNominalReturnPct = it)) })
                        NumberSettingField(label = "DPS Gross Return (%)", value = s.dpsGrossReturnPct, onValueChange = { onUpdateSettings(s.copy(dpsGrossReturnPct = it)) })
                        NumberSettingField(label = "DPS Annual Fee (%) [Cap 0.5%]", value = s.dpsAnnualFeePct, onValueChange = { onUpdateSettings(s.copy(dpsAnnualFeePct = it)) })
                    }
                }
                3 -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Czech Tax Return Helper Summary 🇨🇿",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TaxSummaryRow(
                                label = "Spouse Income (Calculated)",
                                status = com.example.util.Formatters.fmtCZK(state.taxReturnHelper.spouseOwnIncome),
                                isGood = state.taxReturnHelper.spouseOwnIncome <= 68000.0
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            TaxSummaryRow(
                                label = "Spouse Tax Credit (<68k income & child <3)",
                                status = if (state.taxReturnHelper.spouseEligible) "Eligible (+${com.example.util.Formatters.fmtCZK(state.taxReturnHelper.spouseCredit)})" else "Not Eligible",
                                isGood = state.taxReturnHelper.spouseEligible
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            TaxSummaryRow(
                                label = "Child Tax Bonus (Sleva na dítě)",
                                status = "Eligible (+${com.example.util.Formatters.fmtCZK(state.taxReturnHelper.childBonus)})",
                                isGood = true
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            TaxSummaryRow(
                                label = "Estimated Annual DIP Tax Saving",
                                status = fmtCZK(state.taxReturnHelper.dipSaving),
                                isGood = true
                            )
                        }
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
                }
                4 -> {
                    val customCategories = remember(s.customExpensesJson) { parseCustomExpenses(s.customExpensesJson) }

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

                        if (customCategories.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            Text(
                                text = "Custom Expense Categories",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            customCategories.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    NumberSettingField(
                                        label = "",
                                        value = item.amount,
                                        onValueChange = { updatedVal ->
                                            val updatedList = customCategories.map {
                                                if (it.id == item.id) it.copy(amount = updatedVal) else it
                                            }
                                            onUpdateSettings(s.copy(customExpensesJson = serializeCustomExpenses(updatedList)))
                                        }
                                    )
                                    IconButton(
                                        onClick = {
                                            val updatedList = customCategories.filter { it.id != item.id }
                                            onUpdateSettings(s.copy(customExpensesJson = serializeCustomExpenses(updatedList)))
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete category",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                newCategoryName = ""
                                newCategoryAmount = ""
                                showAddCategoryDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_custom_category_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text("+ Add Custom Expense Category")
                        }
                    }
                }
                5 -> {
                    // Reset Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Reset & Restore Data",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showResetDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reset_defaults_button")
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                                Text("Reset All Settings to Defaults")
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showClearDataDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("clear_all_data_button")
                            ) {
                                Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null)
                                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                                Text("Clear All Data (Ledger, Settings, Plan)")
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Settings?") },
            text = { Text("This will reset all your settings (like income, expenses, FIRE target) back to their default values. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetDefaults()
                        showResetDialog = false
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear All User Data?", color = MaterialTheme.colorScheme.error) },
            text = { Text("This will PERMANENTLY delete all your ledger entries, action plan checks, and reset all your settings. Are you absolutely sure?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllData()
                        showClearDataDialog = false
                    }
                ) {
                    Text("Delete Everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Add Custom Expense Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Category Name (e.g., Pets & Vet)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_category_name_input")
                    )
                    OutlinedTextField(
                        value = newCategoryAmount,
                        onValueChange = { newCategoryAmount = it },
                        label = { Text("Monthly Amount (CZK)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_category_amount_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newCategoryName.ifBlank { "Custom Category" }
                        val amt = newCategoryAmount.toDoubleOrNull() ?: 0.0
                        val currentList = parseCustomExpenses(s.customExpensesJson)
                        val newItem = CustomExpenseItem(id = UUID.randomUUID().toString(), name = name, amount = amt)
                        val updatedList = currentList + newItem
                        onUpdateSettings(s.copy(customExpensesJson = serializeCustomExpenses(updatedList)))
                        showAddCategoryDialog = false
                    },
                    modifier = Modifier.testTag("confirm_add_category_button")
                ) {
                    Text("Add Category")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TaxSummaryRow(label: String, status: String, isGood: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = if (isGood) GoodGreen else BadRed
            )
        )
    }
}

@Composable
private fun SettingsGroupCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun NumberSettingField(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    testTagStr: String = ""
) {
    var textValue by remember(value) { mutableStateOf(if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = textValue,
            onValueChange = { input ->
                textValue = input
                input.toDoubleOrNull()?.let { onValueChange(it) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth(0.45f)
                .then(if (testTagStr.isNotEmpty()) Modifier.testTag(testTagStr) else Modifier)
        )
    }
}

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
