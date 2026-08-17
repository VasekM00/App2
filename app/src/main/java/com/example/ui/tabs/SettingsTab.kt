package com.example.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.CustomExpenseItem
import com.example.domain.FullCalculationState
import com.example.domain.parseCustomExpenses
import com.example.domain.serializeCustomExpenses
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Share
import com.example.util.BackupManager
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtPct
import com.example.ui.components.ColorPill
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    state: FullCalculationState,
    onUpdateSettings: (SettingsEntity) -> Unit,
    onResetDefaults: () -> Unit,
    onClearAllData: () -> Unit,
    initialSubTab: Int = 0,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val s = state.settings
    val tealColor = BrandTeal
    val context = LocalContext.current
    
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showImportJsonDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var newCategoryAmount by remember { mutableStateOf("") }

    var selectedTab by remember(initialSubTab) { mutableIntStateOf(initialSubTab) }
    val tabs = listOf("General", "Income", "Expenses", "Investments", "Taxes & Family", "Data")

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
                    SettingsGroupCard(title = "General Settings", initiallyExpanded = false, badgeText = "SYSTEM", badgeColor = BrandTeal) {
                        NumberSettingField(
                            label = "Base Year",
                            value = s.baseYear.toDouble(),
                            onValueChange = { yr ->
                                val y = yr.toInt()
                                onUpdateSettings(s.copy(baseYear = y, primaryAge = y - com.example.domain.PRIMARY_BIRTH_YEAR))
                            }
                        )
                        NumberSettingField(
                            label = "Birth Year (Hardcoded: 2000)",
                            value = com.example.domain.PRIMARY_BIRTH_YEAR.toDouble(),
                            onValueChange = { /* hardcoded as requested */ },
                            readOnly = true
                        )
                        NumberSettingField(
                            label = "CPI Inflation (%)",
                            value = s.cpiInflationPct,
                            onValueChange = { onUpdateSettings(s.copy(cpiInflationPct = it)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // FIRE Target Settings
                    SettingsGroupCard(title = "FIRE Target & Parameters", initiallyExpanded = false, badgeText = "TARGETS", badgeColor = BrandTeal) {
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
                    SettingsGroupCard(title = "Vaclav Income Settings", initiallyExpanded = true, badgeText = "ACTIVE", badgeColor = BrandTeal) {
                        NumberSettingField(label = "Salary (CZK)", value = s.vSalary, onValueChange = { onUpdateSettings(s.copy(vSalary = it)) })
                        NumberSettingField(label = "Annual September Raise (CZK)", value = s.vRaiseAnnual, onValueChange = { onUpdateSettings(s.copy(vRaiseAnnual = it)) })
                        NumberSettingField(label = "Annual Bonus (CZK)", value = s.vBonusAnnual, onValueChange = { onUpdateSettings(s.copy(vBonusAnnual = it)) })
                        NumberSettingField(label = "Meal Vouchers Monthly (CZK)", value = s.vMealVouchersMonthly, onValueChange = { onUpdateSettings(s.copy(vMealVouchersMonthly = it)) })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Eleonora Active Income (Today)
                    SettingsGroupCard(title = "Eleonora Active Income (Today)", initiallyExpanded = true, badgeText = "ACTIVE", badgeColor = BrandGold) {
                        NumberSettingField(label = "Parental Allowance Monthly (CZK)", value = s.eParentalAllowanceMonthly, onValueChange = { onUpdateSettings(s.copy(eParentalAllowanceMonthly = it)) })
                        NumberSettingField(label = "Lecturing Monthly (CZK)", value = s.eLecturingMonthly, onValueChange = { onUpdateSettings(s.copy(eLecturingMonthly = it)) })
                        BooleanSettingField(label = "Include Lecturing Income", checked = s.eIncludeLecturing, onCheckedChange = { onUpdateSettings(s.copy(eIncludeLecturing = it)) })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Eleonora Future Return to Work Milestone
                    SettingsGroupCard(
                        title = "Future Return to Work (${s.eReturnYear}+)",
                        initiallyExpanded = false,
                        badgeText = "MILESTONE",
                        badgeColor = BrandGold
                    ) {
                        NumberSettingField(label = "Planned Return Year", value = s.eReturnYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(eReturnYear = it.toInt())) })
                        NumberSettingField(label = "Future Starting Salary (CZK)", value = s.eStartingSalary, onValueChange = { onUpdateSettings(s.copy(eStartingSalary = it)) })
                        NumberSettingField(label = "Future Annual Bonus (CZK)", value = s.eBonusAnnual, onValueChange = { onUpdateSettings(s.copy(eBonusAnnual = it)) })
                        NumberSettingField(label = "Future Salary Growth (%)", value = s.eSalaryGrowthPct, onValueChange = { onUpdateSettings(s.copy(eSalaryGrowthPct = it)) })
                        NumberSettingField(label = "Future Reinvested Share (%)", value = s.eReinvestedPct, onValueChange = { onUpdateSettings(s.copy(eReinvestedPct = it)) })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Gifts & Secondary Inflow
                    SettingsGroupCard(title = "Family Support Gift", initiallyExpanded = true, badgeText = "INFLOW", badgeColor = GoodGreen) {
                        NumberSettingField(label = "Family Gift Monthly (CZK)", value = s.familyGiftMonthly, onValueChange = { onUpdateSettings(s.copy(familyGiftMonthly = it)) })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsGroupCard(
                        title = "Planned Lump Sum (${s.lumpSumYear})",
                        initiallyExpanded = false,
                        badgeText = "INFLOW",
                        badgeColor = GoodGreen
                    ) {
                        BooleanSettingField(label = "Include Lump Sum Event", checked = s.lumpSumInclude, onCheckedChange = { onUpdateSettings(s.copy(lumpSumInclude = it)) })
                        NumberSettingField(label = "Planned Lump Sum Year", value = s.lumpSumYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(lumpSumYear = it.toInt())) })
                        NumberSettingField(label = "Planned Lump Sum Amount (CZK)", value = s.lumpSumAmount, onValueChange = { onUpdateSettings(s.copy(lumpSumAmount = it)) })
                    }
                }
                2 -> {
                    val customCategories = remember(s.customExpensesJson) { parseCustomExpenses(s.customExpensesJson) }
                    val deletedSet = remember(s.deletedCategoriesJson) { com.example.domain.parseDeletedCategories(s.deletedCategoriesJson) }

                    fun deleteBuiltInKey(key: String, newSettings: SettingsEntity): SettingsEntity {
                        val newSet = deletedSet + key
                        return newSettings.copy(deletedCategoriesJson = com.example.domain.serializeDeletedCategories(newSet))
                    }

                    // Living Costs Settings
                    SettingsGroupCard(title = "Monthly Living Expenses (CZK)", initiallyExpanded = true, collapsible = true) {
                        if (!deletedSet.contains("rent")) {
                            NumberSettingField(label = "Rent", value = s.rentMonthly, onValueChange = { onUpdateSettings(s.copy(rentMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("rent", s.copy(rentMonthly = 0.0))) })
                        }
                        if (!deletedSet.contains("groceries")) {
                            NumberSettingField(label = "Groceries & Daily Living", value = s.groceriesMonthly, onValueChange = { onUpdateSettings(s.copy(groceriesMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("groceries", s.copy(groceriesMonthly = 0.0))) })
                        }
                        if (!deletedSet.contains("other_discretionary") && s.otherDiscretionaryMonthly > 0.0) {
                            NumberSettingField(label = "Other Discretionary", value = s.otherDiscretionaryMonthly, onValueChange = { onUpdateSettings(s.copy(otherDiscretionaryMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("other_discretionary", s.copy(otherDiscretionaryMonthly = 0.0))) })
                        }
                        if (!deletedSet.contains("cafes")) {
                            NumberSettingField(label = "Cafes & Restaurants", value = s.cafesMonthly, onValueChange = { onUpdateSettings(s.copy(cafesMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("cafes", s.copy(cafesMonthly = 0.0))) })
                        }
                        if (!deletedSet.contains("therapy")) {
                            NumberSettingField(label = "Therapy / Physio", value = s.therapyMonthly, onValueChange = { onUpdateSettings(s.copy(therapyMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("therapy", s.copy(therapyMonthly = 0.0))) })
                        }
                        if (!deletedSet.contains("charity")) {
                            NumberSettingField(label = "Charity", value = s.charityMonthly, onValueChange = { onUpdateSettings(s.copy(charityMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("charity", s.copy(charityMonthly = 0.0))) })
                        }
                        if (!deletedSet.contains("entertainment")) {
                            NumberSettingField(label = "Entertainment", value = s.entertainmentMonthly, onValueChange = { onUpdateSettings(s.copy(entertainmentMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("entertainment", s.copy(entertainmentMonthly = 0.0))) })
                        }
                        if (!deletedSet.contains("transport")) {
                            NumberSettingField(label = "Transport", value = s.transportMonthly, onValueChange = { onUpdateSettings(s.copy(transportMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("transport", s.copy(transportMonthly = 0.0))) })
                        }
                        if (!deletedSet.contains("subscriptions")) {
                            NumberSettingField(label = "Subscriptions", value = s.subscriptionsMonthly, onValueChange = { onUpdateSettings(s.copy(subscriptionsMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("subscriptions", s.copy(subscriptionsMonthly = 0.0))) })
                        }

                        if (customCategories.isNotEmpty()) {
                            customCategories.forEach { item ->
                                key(item.id) {
                                    NumberSettingField(
                                        label = item.name,
                                        value = item.amount,
                                        onValueChange = { updatedVal ->
                                            val updatedList = customCategories.map {
                                                if (it.id == item.id) it.copy(amount = updatedVal) else it
                                            }
                                            onUpdateSettings(s.copy(customExpensesJson = serializeCustomExpenses(updatedList)))
                                        },
                                        onDelete = {
                                            val updatedList = customCategories.filter { it.id != item.id }
                                            onUpdateSettings(s.copy(customExpensesJson = serializeCustomExpenses(updatedList)))
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

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
                            Text("+ Add Category")
                        }
                    }
                }
                3 -> {
                    var invSubTab by remember { mutableIntStateOf(0) }

                    SecondaryTabRow(
                        selectedTabIndex = invSubTab,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Tab(
                            selected = invSubTab == 0,
                            onClick = { invSubTab = 0 },
                            text = { Text("Current Balances", fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("settings_inv_subtab_balances")
                        )
                        Tab(
                            selected = invSubTab == 1,
                            onClick = { invSubTab = 1 },
                            text = { Text("Monthly DCA Flow", fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("settings_inv_subtab_dca")
                        )
                        Tab(
                            selected = invSubTab == 2,
                            onClick = { invSubTab = 2 },
                            text = { Text("Assumptions", fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("settings_inv_subtab_assumptions")
                        )
                    }

                    when (invSubTab) {
                        0 -> {
                            // Václav's Balances
                            SettingsGroupCard(title = "Václav's Current Balances 💼", initiallyExpanded = true) {
                                NumberSettingField(
                                    label = "Portu / ETF Liquid Portfolio Balance (CZK)",
                                    value = s.liquidPortfolioCurrent,
                                    onValueChange = { onUpdateSettings(s.copy(liquidPortfolioCurrent = it)) },
                                    testTagStr = "input_liquid_port"
                                )
                                NumberSettingField(
                                    label = "DIP Balance Current (CZK)",
                                    value = s.dipBalanceCurrent,
                                    onValueChange = { onUpdateSettings(s.copy(dipBalanceCurrent = it)) }
                                )
                                NumberSettingField(
                                    label = "DPS Pension Balance Current (CZK)",
                                    value = s.dpsBalanceCurrent,
                                    onValueChange = { onUpdateSettings(s.copy(dpsBalanceCurrent = it)) }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Eleonora's Balances
                            SettingsGroupCard(title = "Eleonora's Current Balances 💼", initiallyExpanded = true) {
                                NumberSettingField(
                                    label = "Eleonora's Liquid Portfolio Balance (CZK)",
                                    value = s.eLiquidPortfolioCurrent,
                                    onValueChange = { onUpdateSettings(s.copy(eLiquidPortfolioCurrent = it)) }
                                )
                                NumberSettingField(
                                    label = "Eleonora's DIP Balance Current (CZK)",
                                    value = s.eDipBalanceCurrent,
                                    onValueChange = { onUpdateSettings(s.copy(eDipBalanceCurrent = it)) }
                                )
                                NumberSettingField(
                                    label = "Eleonora's DPS Pension Balance Current (CZK)",
                                    value = s.eDpsBalanceCurrent,
                                    onValueChange = { onUpdateSettings(s.copy(eDpsBalanceCurrent = it)) }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Emergency Reserve & Cash
                            SettingsGroupCard(title = "Cash & Emergency Reserve 🏦", initiallyExpanded = true) {
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
                        }
                        1 -> {
                            // Václav's Monthly Investments
                            SettingsGroupCard(title = "Václav's Monthly Investments (DCA) 🔄", initiallyExpanded = true) {
                                NumberSettingField(
                                    label = "Portu / ETF Monthly DCA (CZK)",
                                    value = s.portuDcaMonthly,
                                    onValueChange = { onUpdateSettings(s.copy(portuDcaMonthly = it)) }
                                )
                                NumberSettingField(
                                    label = "DIP Monthly Contribution (CZK)",
                                    value = s.dipContributionMonthly,
                                    onValueChange = { onUpdateSettings(s.copy(dipContributionMonthly = it)) }
                                )
                                NumberSettingField(
                                    label = "DPS Monthly Own Contribution (CZK)",
                                    value = s.dpsOwnContributionMonthly,
                                    onValueChange = { onUpdateSettings(s.copy(dpsOwnContributionMonthly = it)) }
                                )
                                NumberSettingField(
                                    label = "Employer Retirement Annual Benefit (CZK)",
                                    value = s.employerRetirementAnnual,
                                    onValueChange = { onUpdateSettings(s.copy(employerRetirementAnnual = it)) }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Eleonora's Monthly Investments
                            SettingsGroupCard(title = "Eleonora's Monthly Investments (DCA) 🔄", initiallyExpanded = true) {
                                NumberSettingField(
                                    label = "Eleonora's Portu / ETF Monthly DCA (CZK)",
                                    value = s.ePortuDcaMonthly,
                                    onValueChange = { onUpdateSettings(s.copy(ePortuDcaMonthly = it)) }
                                )
                                NumberSettingField(
                                    label = "Eleonora's DIP Monthly Contribution (CZK)",
                                    value = s.eDipContributionMonthly,
                                    onValueChange = { onUpdateSettings(s.copy(eDipContributionMonthly = it)) }
                                )
                                NumberSettingField(
                                    label = "Eleonora's DPS Monthly Contribution (CZK)",
                                    value = s.eDpsOwnContributionMonthly,
                                    onValueChange = { onUpdateSettings(s.copy(eDpsOwnContributionMonthly = it)) }
                                )
                                NumberSettingField(
                                    label = "Eleonora's Employer Retirement Annual Benefit (CZK)",
                                    value = s.eEmployerRetirementAnnual,
                                    onValueChange = { onUpdateSettings(s.copy(eEmployerRetirementAnnual = it)) }
                                )
                            }
                        }
                        2 -> {
                            // Return & Fee Assumptions
                            SettingsGroupCard(title = "Global Return & Fee Assumptions 📊", initiallyExpanded = true) {
                                NumberSettingField(
                                    label = "Portfolio Nominal Return (%)",
                                    value = s.portfolioNominalReturnPct,
                                    onValueChange = { onUpdateSettings(s.copy(portfolioNominalReturnPct = it)) }
                                )
                                NumberSettingField(
                                    label = "DPS Gross Return (%)",
                                    value = s.dpsGrossReturnPct,
                                    onValueChange = { onUpdateSettings(s.copy(dpsGrossReturnPct = it)) }
                                )
                                NumberSettingField(
                                    label = "DPS Annual Fee (%) [Cap 0.5%]",
                                    value = s.dpsAnnualFeePct,
                                    onValueChange = { onUpdateSettings(s.copy(dpsAnnualFeePct = it)) }
                                )
                            }
                        }
                    }
                }
                4 -> {
                    SettingsGroupCard(title = "Czech Tax Return Helper Summary 🇨🇿", initiallyExpanded = false) {
                        TaxSummaryRow(
                            label = "Spouse Income (Calculated)",
                            status = com.example.util.Formatters.fmtCZK(state.taxReturnHelper.spouseOwnIncome),
                            isGood = state.taxReturnHelper.spouseOwnIncome <= s.spouseIncomeLimitAnnual
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        TaxSummaryRow(
                            label = "Spouse Tax Credit (<${(s.spouseIncomeLimitAnnual / 1000).toInt()}k income & child <3)",
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tax Parameters
                    SettingsGroupCard(title = "Tax Rates & Thresholds (Future-Proof)", initiallyExpanded = false) {
                        NumberSettingField(label = "Base Income Tax Rate (%)", value = s.taxRatePct, onValueChange = { onUpdateSettings(s.copy(taxRatePct = it)) })
                        NumberSettingField(label = "Higher Bracket Tax Rate (%)", value = s.taxRateSecondPct, onValueChange = { onUpdateSettings(s.copy(taxRateSecondPct = it)) })
                        NumberSettingField(label = "Higher Bracket Threshold Annual (CZK)", value = s.taxSecondBracketThresholdAnnual, onValueChange = { onUpdateSettings(s.copy(taxSecondBracketThresholdAnnual = it)) })
                        NumberSettingField(label = "Basic Taxpayer Credit Annual (CZK)", value = s.taxpayerCreditAnnual, onValueChange = { onUpdateSettings(s.copy(taxpayerCreditAnnual = it)) })
                        NumberSettingField(label = "Retirement Tax Deduction Ceiling Annual (CZK)", value = s.taxDeductionCeilingAnnual, onValueChange = { onUpdateSettings(s.copy(taxDeductionCeilingAnnual = it)) })
                        NumberSettingField(label = "Spouse Tax Credit Annual (CZK)", value = s.spouseTaxCreditAnnual, onValueChange = { onUpdateSettings(s.copy(spouseTaxCreditAnnual = it)) })
                        NumberSettingField(label = "Spouse Income Limit Annual (CZK)", value = s.spouseIncomeLimitAnnual, onValueChange = { onUpdateSettings(s.copy(spouseIncomeLimitAnnual = it)) })
                        BooleanSettingField(label = "Include Spouse Credit", checked = s.includeSpouseCredit, onCheckedChange = { onUpdateSettings(s.copy(includeSpouseCredit = it)) })
                        BooleanSettingField(label = "Has Child Under 3", checked = s.hasChildUnder3, onCheckedChange = { onUpdateSettings(s.copy(hasChildUnder3 = it)) })
                        NumberSettingField(label = "Min Wage Monthly (CZK)", value = s.minWageMonthly, onValueChange = { onUpdateSettings(s.copy(minWageMonthly = it)) })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pension Reform & Subsidies
                    SettingsGroupCard(title = "Pension Subsidies & Reform Rules", initiallyExpanded = false) {
                        NumberSettingField(label = "DPS Deduction Threshold Monthly (CZK)", value = s.dpsDeductionThresholdMonthly, onValueChange = { onUpdateSettings(s.copy(dpsDeductionThresholdMonthly = it)) })
                        NumberSettingField(label = "DPS Min Deposit For Subsidy Monthly (CZK)", value = s.dpsMinDepositForSubsidy, onValueChange = { onUpdateSettings(s.copy(dpsMinDepositForSubsidy = it)) })
                        NumberSettingField(label = "DPS Standard State Subsidy Max Monthly (CZK)", value = s.dpsStandardSubsidyMaxMonthly, onValueChange = { onUpdateSettings(s.copy(dpsStandardSubsidyMaxMonthly = it)) })
                        NumberSettingField(label = "DPS Standard Subsidy Rate (%)", value = s.dpsSubsidyRateStandardPct, onValueChange = { onUpdateSettings(s.copy(dpsSubsidyRateStandardPct = it)) })
                        NumberSettingField(label = "DPS Youth Age Limit (Years)", value = s.dpsYouthAgeLimit.toDouble(), onValueChange = { onUpdateSettings(s.copy(dpsYouthAgeLimit = it.toInt())) })
                        NumberSettingField(label = "DPS Youth State Subsidy Max Monthly (CZK)", value = s.dpsYouthSubsidyMaxMonthly, onValueChange = { onUpdateSettings(s.copy(dpsYouthSubsidyMaxMonthly = it)) })
                        NumberSettingField(label = "DPS Youth Subsidy Rate (%)", value = s.dpsSubsidyRateYouthPct, onValueChange = { onUpdateSettings(s.copy(dpsSubsidyRateYouthPct = it)) })
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Child Settings & Expenses (Active Today)
                    SettingsGroupCard(title = "Child & Family Expenses (Active Today)", initiallyExpanded = false) {
                        BooleanSettingField(label = "Enable Family Child Expenses", checked = s.childExpensesEnabled, onCheckedChange = { onUpdateSettings(s.copy(childExpensesEnabled = it)) })
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        BooleanSettingField(label = "Include Child 1", checked = s.child1Enabled, onCheckedChange = { onUpdateSettings(s.copy(child1Enabled = it)) })
                        NumberSettingField(label = "Child 1 Birth Year", value = s.child1BirthYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(child1BirthYear = it.toInt())) })
                        NumberSettingField(label = "Child 1 Tax Bonus Annual (CZK)", value = s.child1TaxBonusAnnual, onValueChange = { onUpdateSettings(s.copy(child1TaxBonusAnnual = it)) })
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Stage Expense Estimates (per Child)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        NumberSettingField(label = "Toddler Monthly (CZK)", value = s.childToddlerMonthly, onValueChange = { onUpdateSettings(s.copy(childToddlerMonthly = it)) })
                        NumberSettingField(label = "Preschool Monthly (CZK)", value = s.childPreschoolMonthly, onValueChange = { onUpdateSettings(s.copy(childPreschoolMonthly = it)) })
                        NumberSettingField(label = "School Monthly (CZK)", value = s.childSchoolMonthly, onValueChange = { onUpdateSettings(s.copy(childSchoolMonthly = it)) })
                        NumberSettingField(label = "Teen Monthly (CZK)", value = s.childTeenMonthly, onValueChange = { onUpdateSettings(s.copy(childTeenMonthly = it)) })
                        NumberSettingField(label = "Uni Monthly (CZK)", value = s.childUniMonthly, onValueChange = { onUpdateSettings(s.copy(childUniMonthly = it)) })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Future Child 2 Event
                    SettingsGroupCard(
                        title = "🔮 Future Milestones: Child 2 (Planned Birth ${s.child2BirthYear})",
                        initiallyExpanded = false
                    ) {
                        BooleanSettingField(label = "Include Child 2 Planned Event", checked = s.child2Enabled, onCheckedChange = { onUpdateSettings(s.copy(child2Enabled = it)) })
                        NumberSettingField(label = "Child 2 Planned Birth Year", value = s.child2BirthYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(child2BirthYear = it.toInt())) })
                        NumberSettingField(label = "Child 2 Tax Bonus Annual (CZK)", value = s.child2TaxBonusAnnual, onValueChange = { onUpdateSettings(s.copy(child2TaxBonusAnnual = it)) })
                    }
                }
                5 -> {
                    // About & Version Card
                    SettingsGroupCard(title = "About Application", initiallyExpanded = false) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "App Version",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Surface(
                                color = tealColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "v${com.example.BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = tealColor
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Czech Financial & FIRE Planning Suite",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Backup & Snapshot Card
                    SettingsGroupCard(title = "Data Backup & Snapshots 💾", initiallyExpanded = true, badgeText = "BACKUP", badgeColor = BrandTeal) {
                        Text(
                            text = "Export your entire financial model as JSON or copy a summary to keep your records safe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val json = BackupManager.serializeSettingsToJson(s)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("FIRE Settings JSON", json)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Settings JSON copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    importJsonText = ""
                                    importErrorMessage = null
                                    showImportJsonDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                val summary = buildString {
                                    appendLine("📊 Financial Summary (${s.baseYear})")
                                    appendLine("• Net Worth: ${fmtCZK(state.netWorthTotal)}")
                                    appendLine("• Monthly Expenses: ${fmtCZK(state.totalLivingCostMonthly)}")
                                    appendLine("• Monthly Investments (DCA): ${fmtCZK(state.investMonthlyTotal)}")
                                    appendLine("• FIRE Target: ${fmtCZK(state.fireBaseTargetToday)} (${fmtPct(s.safeWithdrawalRatePct)} SWR)")
                                    appendLine("• Dual FIRE ETA: ${state.fireDualPoint?.let { "${it.year} (Age ${it.age})" } ?: "Beyond 35y"}")
                                    appendLine("• Emergency Reserve: ${fmtCZK(s.emergencyReserveCurrent)} (${String.format("%.1f", state.emergencyCoverageMonths)} months)")
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Financial Summary", summary)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Summary copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Financial Snapshot Summary", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Reset Card
                    SettingsGroupCard(title = "Reset & Danger Zone", initiallyExpanded = false) {
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

    if (showImportJsonDialog) {
        AlertDialog(
            onDismissRequest = { showImportJsonDialog = false },
            title = { Text("Import Settings JSON", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste your exported JSON settings snippet below to restore your configuration:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = {
                            importJsonText = it
                            importErrorMessage = null
                        },
                        label = { Text("JSON Data") },
                        minLines = 6,
                        maxLines = 10,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (importErrorMessage != null) {
                        Text(
                            text = importErrorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val restored = BackupManager.deserializeSettingsFromJson(importJsonText, s)
                        if (restored != null) {
                            onUpdateSettings(restored)
                            showImportJsonDialog = false
                            Toast.makeText(context, "Settings successfully restored! 🎉", Toast.LENGTH_SHORT).show()
                        } else {
                            importErrorMessage = "Invalid JSON format. Please check the pasted data."
                        }
                    }
                ) {
                    Text("Apply & Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportJsonDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
            title = { Text("Add Expense Category") },
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
                        val name = newCategoryName.ifBlank { "Expense Category" }
                        val sanitizedAmt = newCategoryAmount.replace(',', '.').trim()
                        val amt = sanitizedAmt.toDoubleOrNull() ?: 0.0
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
private fun SettingsGroupCard(
    title: String,
    initiallyExpanded: Boolean = false,
    collapsible: Boolean = true,
    badgeText: String? = null,
    badgeColor: androidx.compose.ui.graphics.Color = BrandTeal,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (collapsible) Modifier.clickable { expanded = !expanded } else Modifier),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (badgeText != null) {
                        ColorPill(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 9.sp,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                }
                if (collapsible) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse section" else "Expand section",
                            tint = BrandTeal
                        )
                    }
                }
            }
            if (!collapsible || expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun NumberSettingField(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    testTagStr: String = "",
    onDelete: (() -> Unit)? = null,
    readOnly: Boolean = false
) {
    fun formatVal(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    var textValue by remember { mutableStateOf(formatVal(value)) }
    var isFocused by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun commitCurrentText() {
        if (readOnly) return
        val sanitized = textValue.replace(',', '.').trim()
        val parsed = if (sanitized.isEmpty()) 0.0 else sanitized.toDoubleOrNull()
        if (parsed != null && parsed != value) {
            onValueChange(parsed)
        }
    }

    // Sync external state changes when not actively being edited
    LaunchedEffect(value) {
        if (!isFocused) {
            val currentParsed = textValue.replace(',', '.').toDoubleOrNull()
            if (currentParsed == null || currentParsed != value) {
                textValue = formatVal(value)
            }
        }
    }

    // Debounce commit while user is typing
    LaunchedEffect(textValue) {
        if (isFocused) {
            delay(400)
            commitCurrentText()
        }
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Category") },
            text = { Text("Are you sure you want to delete \"$label\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

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
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onDelete != null) {
                        Modifier.pointerInput(label) {
                            detectTapGestures(
                                onLongPress = {
                                    showDeleteConfirm = true
                                }
                            )
                        }
                    } else Modifier
                )
        )
        OutlinedTextField(
            value = textValue,
            onValueChange = { input ->
                if (!readOnly) {
                    textValue = input
                }
            },
            readOnly = readOnly,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            keyboardActions = KeyboardActions(onDone = { commitCurrentText() }),
            singleLine = true,
            modifier = Modifier
                .width(130.dp)
                .onFocusChanged {
                    if (isFocused && !it.isFocused) {
                        commitCurrentText()
                    }
                    isFocused = it.isFocused
                }
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
