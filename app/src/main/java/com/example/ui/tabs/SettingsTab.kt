package com.example.ui.tabs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
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
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.CustomExpenseItem
import com.example.domain.FullCalculationState
import com.example.domain.PRIMARY_BIRTH_YEAR
import com.example.domain.parseCustomExpenses
import com.example.domain.parseDeletedCategories
import com.example.domain.serializeCustomExpenses
import com.example.domain.serializeDeletedCategories
import com.example.ui.components.ColorPill
import com.example.ui.components.LiveSyncDialog
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.BackupManager
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtPct
import kotlinx.coroutines.delay
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    state: FullCalculationState,
    onUpdateSettings: (SettingsEntity) -> Unit,
    onResetDefaults: () -> Unit,
    onClearAllData: () -> Unit,
    liveRegulatoryData: com.example.domain.CzechRegulatoryData? = null,
    isSyncing: Boolean = false,
    onSyncLiveCzechData: () -> Unit = {},
    initialSubTab: Int = 0,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val s = state.settings
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var showSyncDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showImportJsonDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var newCategoryAmount by remember { mutableStateOf("") }

    // Consolidated into 3 high-level cohesive hubs
    val clampedInitialTab = initialSubTab.coerceIn(0, 2)
    var selectedTab by rememberSaveable(initialSubTab) { mutableIntStateOf(clampedInitialTab) }
    val tabs = listOf("Cashflow & Family", "FIRE & Assets", "Data & System")

    LaunchedEffect(selectedTab) {
        listState.scrollToItem(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_tab")
    ) {
        SecondaryTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedTab = index
                    },
                    text = {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.testTag("settings_subtab_$index")
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // ==========================================
                    // TAB 0: CASHFLOW & FAMILY
                    // ==========================================

                    // 1. Profile & Household Structure
                    item {
                        SettingsGroupCard(
                            title = "Profile & Household Structure",
                            initiallyExpanded = false,
                            badgeText = "VÁCLAV & ELEONORA",
                            badgeColor = BrandGold
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Václav & Eleonora",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = BrandTeal)
                                        )
                                        Text(
                                            text = "Married Household · Primary & Wife",
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                    }
                                    ColorPill(
                                        text = "MARRIED",
                                        color = BrandTeal,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        horizontalPadding = 8.dp,
                                        verticalPadding = 4.dp
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            NumberSettingField(
                                label = "Base Planning Year",
                                value = s.baseYear.toDouble(),
                                onValueChange = { yr ->
                                    val y = yr.toInt()
                                    onUpdateSettings(s.copy(baseYear = y, primaryAge = y - PRIMARY_BIRTH_YEAR))
                                }
                            )
                            NumberSettingField(
                                label = "Birth Year (2000)",
                                value = PRIMARY_BIRTH_YEAR.toDouble(),
                                onValueChange = { },
                                readOnly = true
                            )
                            NumberSettingField(
                                label = "Expected CPI Inflation (%)",
                                value = s.cpiInflationPct,
                                onValueChange = { onUpdateSettings(s.copy(cpiInflationPct = it)) }
                            )
                        }
                    }

                    // 2. Earned & Side Incomes
                    item {
                        SettingsGroupCard(
                            title = "Earned & Side Incomes",
                            initiallyExpanded = false,
                            badgeText = fmtCZK(state.currentIncome.totalMonthly) + "/mo",
                            badgeColor = GoodGreen
                        ) {
                            Text(
                                text = "Václav's Incomes",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandTeal)
                            )
                            NumberSettingField(label = "Gross Salary (CZK)", value = s.vSalary, onValueChange = { onUpdateSettings(s.copy(vSalary = it)) })
                            NumberSettingField(label = "Annual September Raise (CZK)", value = s.vRaiseAnnual, onValueChange = { onUpdateSettings(s.copy(vRaiseAnnual = it)) })
                            NumberSettingField(label = "Annual Bonus (CZK)", value = s.vBonusAnnual, onValueChange = { onUpdateSettings(s.copy(vBonusAnnual = it)) })
                            NumberSettingField(label = "Meal Vouchers Monthly (CZK)", value = s.vMealVouchersMonthly, onValueChange = { onUpdateSettings(s.copy(vMealVouchersMonthly = it)) })

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            Text(
                                text = "Eleonora's Incomes (Wife)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandGold)
                            )
                            NumberSettingField(label = "Parental Allowance Monthly (CZK)", value = s.eParentalAllowanceMonthly, onValueChange = { onUpdateSettings(s.copy(eParentalAllowanceMonthly = it)) })
                            NumberSettingField(label = "Lecturing Monthly (CZK)", value = s.eLecturingMonthly, onValueChange = { onUpdateSettings(s.copy(eLecturingMonthly = it)) })

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                text = "Eleonora's Future Return to Work (${s.eReturnYear}+)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            NumberSettingField(label = "Planned Return Year", value = s.eReturnYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(eReturnYear = it.toInt())) })
                            NumberSettingField(label = "Future Starting Salary (CZK)", value = s.eStartingSalary, onValueChange = { onUpdateSettings(s.copy(eStartingSalary = it)) })
                            NumberSettingField(label = "Future Annual Bonus (CZK)", value = s.eBonusAnnual, onValueChange = { onUpdateSettings(s.copy(eBonusAnnual = it)) })
                            NumberSettingField(label = "Future Salary Growth (%)", value = s.eSalaryGrowthPct, onValueChange = { onUpdateSettings(s.copy(eSalaryGrowthPct = it)) })
                            NumberSettingField(label = "Future Reinvested Share (%)", value = s.eReinvestedPct, onValueChange = { onUpdateSettings(s.copy(eReinvestedPct = it)) })

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            Text(
                                text = "Gifts & One-Off Events",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            NumberSettingField(label = "Family Support Gift Monthly (CZK)", value = s.familyGiftMonthly, onValueChange = { onUpdateSettings(s.copy(familyGiftMonthly = it)) })
                            BooleanSettingField(label = "Include Lump Sum Event", checked = s.lumpSumInclude, onCheckedChange = { onUpdateSettings(s.copy(lumpSumInclude = it)) })
                            if (s.lumpSumInclude) {
                                NumberSettingField(label = "Planned Lump Sum Year", value = s.lumpSumYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(lumpSumYear = it.toInt())) })
                                NumberSettingField(label = "Planned Lump Sum Amount (CZK)", value = s.lumpSumAmount, onValueChange = { onUpdateSettings(s.copy(lumpSumAmount = it)) })
                            }
                        }
                    }

                    // 3. Monthly Living Expenses
                    item {
                        val customCategories = remember(s.customExpensesJson) { parseCustomExpenses(s.customExpensesJson) }
                        val deletedSet = remember(s.deletedCategoriesJson) { parseDeletedCategories(s.deletedCategoriesJson) }

                        fun deleteBuiltInKey(key: String, newSettings: SettingsEntity): SettingsEntity {
                            val newSet = deletedSet + key
                            return newSettings.copy(deletedCategoriesJson = serializeDeletedCategories(newSet))
                        }

                        SettingsGroupCard(
                            title = "Monthly Living Expenses",
                            initiallyExpanded = false,
                            badgeText = fmtCZK(state.totalLivingCostMonthly) + "/mo",
                            badgeColor = BadRed
                        ) {
                            if (!deletedSet.contains("rent")) {
                                NumberSettingField(label = "Rent / Housing", value = s.rentMonthly, onValueChange = { onUpdateSettings(s.copy(rentMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("rent", s.copy(rentMonthly = 0.0))) })
                                NumberSettingField(label = "Rent Annual Inflation Growth (%)", value = s.rentGrowthPct, onValueChange = { onUpdateSettings(s.copy(rentGrowthPct = it)) })
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
                                Text("+ Add Custom Category")
                            }
                        }
                    }

                    // 4. Family & Children
                    item {
                        SettingsGroupCard(
                            title = "Family & Children",
                            initiallyExpanded = false,
                            badgeText = if (s.childExpensesEnabled) "ACTIVE" else "OFF",
                            badgeColor = BrandTeal
                        ) {
                            BooleanSettingField(label = "Enable Family Child Expenses", checked = s.childExpensesEnabled, onCheckedChange = { onUpdateSettings(s.copy(childExpensesEnabled = it)) })

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            BooleanSettingField(label = "Include Child 1", checked = s.child1Enabled, onCheckedChange = { onUpdateSettings(s.copy(child1Enabled = it)) })
                            if (s.child1Enabled) {
                                NumberSettingField(label = "Child 1 Birth Year", value = s.child1BirthYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(child1BirthYear = it.toInt())) })
                                NumberSettingField(label = "Child 1 Tax Bonus Annual (CZK)", value = s.child1TaxBonusAnnual, onValueChange = { onUpdateSettings(s.copy(child1TaxBonusAnnual = it)) })
                                NumberSettingField(label = "Child 3+ Tax Bonus Annual (CZK)", value = s.child3PlusTaxBonusAnnual, onValueChange = { onUpdateSettings(s.copy(child3PlusTaxBonusAnnual = it)) })

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Stage Expense Estimates (per Child)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                NumberSettingField(label = "Toddler Monthly (CZK)", value = s.childToddlerMonthly, onValueChange = { onUpdateSettings(s.copy(childToddlerMonthly = it)) })
                                NumberSettingField(label = "Preschool Monthly (CZK)", value = s.childPreschoolMonthly, onValueChange = { onUpdateSettings(s.copy(childPreschoolMonthly = it)) })
                                NumberSettingField(label = "School Monthly (CZK)", value = s.childSchoolMonthly, onValueChange = { onUpdateSettings(s.copy(childSchoolMonthly = it)) })
                                NumberSettingField(label = "Teen Monthly (CZK)", value = s.childTeenMonthly, onValueChange = { onUpdateSettings(s.copy(childTeenMonthly = it)) })
                                NumberSettingField(label = "Uni Monthly (CZK)", value = s.childUniMonthly, onValueChange = { onUpdateSettings(s.copy(childUniMonthly = it)) })
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            BooleanSettingField(label = "Include Planned Child 2 Milestone", checked = s.child2Enabled, onCheckedChange = { onUpdateSettings(s.copy(child2Enabled = it)) })
                            if (s.child2Enabled) {
                                NumberSettingField(label = "Child 2 Planned Birth Year", value = s.child2BirthYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(child2BirthYear = it.toInt())) })
                                NumberSettingField(label = "Child 2 Tax Bonus Annual (CZK)", value = s.child2TaxBonusAnnual, onValueChange = { onUpdateSettings(s.copy(child2TaxBonusAnnual = it)) })
                            }
                        }
                    }
                }

                1 -> {
                    // ==========================================
                    // TAB 1: FIRE & INVESTMENTS
                    // ==========================================

                    // 1. Current Balances & Emergency Reserve
                    item {
                        SettingsGroupCard(
                            title = "Current Asset Balances & Cash Reserve",
                            initiallyExpanded = false,
                            badgeText = fmtCZK(state.netWorthTotal),
                            badgeColor = BrandTeal
                        ) {
                            Text(text = "Václav's Balances", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandTeal))
                            NumberSettingField(
                                label = "Liquid Brokerage / ETF Portfolio (CZK)",
                                value = s.liquidPortfolioCurrent,
                                onValueChange = { onUpdateSettings(s.copy(liquidPortfolioCurrent = it)) },
                                testTagStr = "input_liquid_port"
                            )
                            NumberSettingField(
                                label = "DIP Balance (CZK)",
                                value = s.dipBalanceCurrent,
                                onValueChange = { onUpdateSettings(s.copy(dipBalanceCurrent = it)) }
                            )
                            NumberSettingField(
                                label = "DPS Pension Balance (CZK)",
                                value = s.dpsBalanceCurrent,
                                onValueChange = { onUpdateSettings(s.copy(dpsBalanceCurrent = it)) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(text = "Eleonora's Balances (Wife)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandGold))
                            NumberSettingField(
                                label = "Liquid Brokerage / ETF (CZK)",
                                value = s.eLiquidPortfolioCurrent,
                                onValueChange = { onUpdateSettings(s.copy(eLiquidPortfolioCurrent = it)) }
                            )
                            NumberSettingField(
                                label = "DIP Balance (CZK)",
                                value = s.eDipBalanceCurrent,
                                onValueChange = { onUpdateSettings(s.copy(eDipBalanceCurrent = it)) }
                            )
                            NumberSettingField(
                                label = "DPS Pension Balance (CZK)",
                                value = s.eDpsBalanceCurrent,
                                onValueChange = { onUpdateSettings(s.copy(eDpsBalanceCurrent = it)) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(text = "Cash & Emergency Reserve", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                            NumberSettingField(
                                label = "Liquid Bank & Savings Accounts (CZK)",
                                value = s.emergencyReserveCurrent,
                                onValueChange = { onUpdateSettings(s.copy(emergencyReserveCurrent = it)) }
                            )
                            NumberSettingField(
                                label = "Target Reserve Threshold (CZK)",
                                value = s.emergencyReserveTarget,
                                onValueChange = { onUpdateSettings(s.copy(emergencyReserveTarget = it)) }
                            )
                        }
                    }

                    // 2. Monthly Investment Flows (DCA)
                    item {
                        SettingsGroupCard(
                            title = "Monthly Investment Flows (DCA)",
                            initiallyExpanded = false,
                            badgeText = fmtCZK(state.investMonthlyTotal) + "/mo",
                            badgeColor = GoodGreen
                        ) {
                            Text(text = "Václav's DCA", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandTeal))
                            NumberSettingField(
                                label = "Monthly Brokerage / ETF (CZK)",
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

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(text = "Eleonora's DCA (Wife)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandGold))
                            NumberSettingField(
                                label = "Brokerage / ETF (CZK)",
                                value = s.ePortuDcaMonthly,
                                onValueChange = { onUpdateSettings(s.copy(ePortuDcaMonthly = it)) }
                            )
                            NumberSettingField(
                                label = "DIP Contribution (CZK)",
                                value = s.eDipContributionMonthly,
                                onValueChange = { onUpdateSettings(s.copy(eDipContributionMonthly = it)) }
                            )
                            NumberSettingField(
                                label = "DPS Contribution (CZK)",
                                value = s.eDpsOwnContributionMonthly,
                                onValueChange = { onUpdateSettings(s.copy(eDpsOwnContributionMonthly = it)) }
                            )
                            NumberSettingField(
                                label = "Employer Benefit (CZK)",
                                value = s.eEmployerRetirementAnnual,
                                onValueChange = { onUpdateSettings(s.copy(eEmployerRetirementAnnual = it)) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            NumberSettingField(
                                label = "Annual DCA Contribution Growth (%)",
                                value = s.dcaAnnualGrowthPct,
                                onValueChange = { onUpdateSettings(s.copy(dcaAnnualGrowthPct = it)) }
                            )
                        }
                    }

                    // 3. FIRE Targets & Market Assumptions
                    item {
                        SettingsGroupCard(
                            title = "FIRE Targets & Market Assumptions",
                            initiallyExpanded = false,
                            badgeText = "${fmtPct(s.safeWithdrawalRatePct)} SWR",
                            badgeColor = BrandTeal
                        ) {
                            NumberSettingField(label = "Safe Withdrawal Rate SWR (%)", value = s.safeWithdrawalRatePct, onValueChange = { onUpdateSettings(s.copy(safeWithdrawalRatePct = it)) })
                            NumberSettingField(label = "Safety Buffer (%)", value = s.safetyBufferPct, onValueChange = { onUpdateSettings(s.copy(safetyBufferPct = it)) })
                            NumberSettingField(label = "Expected Portfolio Nominal Return (%)", value = s.portfolioNominalReturnPct, onValueChange = { onUpdateSettings(s.copy(portfolioNominalReturnPct = it)) })
                            NumberSettingField(label = "DPS Gross Return (%)", value = s.dpsGrossReturnPct, onValueChange = { onUpdateSettings(s.copy(dpsGrossReturnPct = it)) })
                            NumberSettingField(label = "DPS Annual Fee (%) [Cap 0.5%]", value = s.dpsAnnualFeePct, onValueChange = { onUpdateSettings(s.copy(dpsAnnualFeePct = it)) })
                            NumberSettingField(label = "FIRE Target Override (CZK) [0=auto]", value = s.fireTargetOverride, onValueChange = { onUpdateSettings(s.copy(fireTargetOverride = it)) })
                            NumberSettingField(label = "Lifestyle Cost at FIRE (CZK/mo)", value = s.lifestyleCostAtFireMonthly, onValueChange = { onUpdateSettings(s.copy(lifestyleCostAtFireMonthly = it)) })
                            NumberSettingField(label = "State Pension Monthly (CZK)", value = s.statePensionMonthly, onValueChange = { onUpdateSettings(s.copy(statePensionMonthly = it)) })
                            NumberSettingField(label = "State Pension Age", value = s.statePensionAge.toDouble(), onValueChange = { onUpdateSettings(s.copy(statePensionAge = it.toInt())) })
                        }
                    }

                    // 4. Advanced: Czech Tax Shield & Statutory Engine
                    item {
                        SettingsGroupCard(
                            title = "Czech Tax Shield & Statutory Engine",
                            initiallyExpanded = false,
                            badgeText = "ADVANCED",
                            badgeColor = BrandGold
                        ) {
                            Text(
                                text = "Live Tax Shield Summary",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TaxSummaryRow(
                                label = "Wife Income (Eleonora)",
                                status = fmtCZK(state.taxReturnHelper.spouseOwnIncome),
                                isGood = state.taxReturnHelper.spouseOwnIncome <= s.spouseIncomeLimitAnnual
                            )
                            TaxSummaryRow(
                                label = "Wife Tax Credit Eligible (§ 35ba)",
                                status = if (state.taxReturnHelper.spouseEligible) "Yes (+${fmtCZK(state.taxReturnHelper.spouseCredit)})" else "No",
                                isGood = state.taxReturnHelper.spouseEligible
                            )
                            TaxSummaryRow(
                                label = "Child Tax Bonus",
                                status = "+${fmtCZK(state.taxReturnHelper.childBonus)}",
                                isGood = true
                            )
                            TaxSummaryRow(
                                label = "DIP Annual Tax Saving",
                                status = fmtCZK(state.taxReturnHelper.dipSaving),
                                isGood = true
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            Text(text = "Tax Rates & Brackets", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            NumberSettingField(label = "Base Income Tax Rate (%)", value = s.taxRatePct, onValueChange = { onUpdateSettings(s.copy(taxRatePct = it)) })
                            NumberSettingField(label = "Higher Bracket Tax Rate (%)", value = s.taxRateSecondPct, onValueChange = { onUpdateSettings(s.copy(taxRateSecondPct = it)) })
                            NumberSettingField(label = "Higher Bracket Threshold Annual (CZK)", value = s.taxSecondBracketThresholdAnnual, onValueChange = { onUpdateSettings(s.copy(taxSecondBracketThresholdAnnual = it)) })
                            NumberSettingField(label = "Basic Taxpayer Credit Annual (CZK)", value = s.taxpayerCreditAnnual, onValueChange = { onUpdateSettings(s.copy(taxpayerCreditAnnual = it)) })
                            NumberSettingField(label = "Retirement Tax Deduction Ceiling Annual (CZK)", value = s.taxDeductionCeilingAnnual, onValueChange = { onUpdateSettings(s.copy(taxDeductionCeilingAnnual = it)) })
                            NumberSettingField(label = "Wife Tax Credit Annual (CZK)", value = s.spouseTaxCreditAnnual, onValueChange = { onUpdateSettings(s.copy(spouseTaxCreditAnnual = it)) })
                            NumberSettingField(label = "Wife Income Limit Annual (CZK)", value = s.spouseIncomeLimitAnnual, onValueChange = { onUpdateSettings(s.copy(spouseIncomeLimitAnnual = it)) })
                            BooleanSettingField(label = "Include Wife Tax Credit", checked = s.includeSpouseCredit, onCheckedChange = { onUpdateSettings(s.copy(includeSpouseCredit = it)) })
                            BooleanSettingField(label = "Has Child Under 3", checked = s.hasChildUnder3, onCheckedChange = { onUpdateSettings(s.copy(hasChildUnder3 = it)) })
                            NumberSettingField(label = "Min Wage Monthly (CZK)", value = s.minWageMonthly, onValueChange = { onUpdateSettings(s.copy(minWageMonthly = it)) })

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            Text(text = "Statutory DPS Pension Subsidies", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            NumberSettingField(label = "DPS Deduction Threshold Monthly (CZK)", value = s.dpsDeductionThresholdMonthly, onValueChange = { onUpdateSettings(s.copy(dpsDeductionThresholdMonthly = it)) })
                            NumberSettingField(label = "DPS Min Deposit For Subsidy Monthly (CZK)", value = s.dpsMinDepositForSubsidy, onValueChange = { onUpdateSettings(s.copy(dpsMinDepositForSubsidy = it)) })
                            NumberSettingField(label = "DPS Standard Subsidy Max Monthly (CZK)", value = s.dpsStandardSubsidyMaxMonthly, onValueChange = { onUpdateSettings(s.copy(dpsStandardSubsidyMaxMonthly = it)) })
                            NumberSettingField(label = "DPS Standard Subsidy Rate (%)", value = s.dpsSubsidyRateStandardPct, onValueChange = { onUpdateSettings(s.copy(dpsSubsidyRateStandardPct = it)) })
                            NumberSettingField(label = "DPS Youth Age Limit (Years)", value = s.dpsYouthAgeLimit.toDouble(), onValueChange = { onUpdateSettings(s.copy(dpsYouthAgeLimit = it.toInt())) })
                            NumberSettingField(label = "DPS Youth Subsidy Max Monthly (CZK)", value = s.dpsYouthSubsidyMaxMonthly, onValueChange = { onUpdateSettings(s.copy(dpsYouthSubsidyMaxMonthly = it)) })
                            NumberSettingField(label = "DPS Youth Subsidy Rate (%)", value = s.dpsSubsidyRateYouthPct, onValueChange = { onUpdateSettings(s.copy(dpsSubsidyRateYouthPct = it)) })

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            Text(text = "Monte Carlo Stochastic Risk Engine", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            NumberSettingField(label = "Portfolio Annual Volatility (%)", value = s.monteCarloVolatilityPct, onValueChange = { onUpdateSettings(s.copy(monteCarloVolatilityPct = it)) })
                            NumberSettingField(label = "Simulation Runs (N)", value = s.monteCarloN.toDouble(), onValueChange = { onUpdateSettings(s.copy(monteCarloN = it.toInt())) })
                        }
                    }
                }

                2 -> {
                    // ==========================================
                    // TAB 2: DATA & SYSTEM
                    // ==========================================

                    // 1. Live Czech Benchmarks Sync
                    item {
                        SettingsGroupCard(
                            title = "Live Czech Economic & Regulatory Sync",
                            initiallyExpanded = false,
                            badgeText = if (liveRegulatoryData != null) "SYNCED" else "OFFLINE",
                            badgeColor = if (liveRegulatoryData != null) GoodGreen else BrandGold
                        ) {
                            Text(
                                text = "Benchmark live macroeconomic parameters directly against official feeds from Český statistický úřad (ČSÚ), Česká národní banka (ČNB), and Czech tax/pension legislation (ZDP).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = liveDataSubtitle(liveRegulatoryData),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSyncLiveCzechData()
                                    showSyncDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sync_czech_benchmarks_button")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check & Sync Live Benchmarks", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 2. Export & Import Settings
                    item {
                        SettingsGroupCard(
                            title = "Backup & Share Settings",
                            initiallyExpanded = false,
                            badgeText = "JSON",
                            badgeColor = BrandTeal
                        ) {
                            Text(
                                text = "Safely export your parameters as a portable JSON file, restore previous backups, or copy a plain-text summary.",
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
                                        Toast.makeText(context, "Settings JSON copied to clipboard", Toast.LENGTH_SHORT).show()
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
                                        appendLine("Financial Summary (${s.baseYear})")
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
                                    Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Financial Snapshot Summary", fontSize = 12.sp)
                            }
                        }
                    }

                    // 3. Danger Zone / Reset Defaults
                    item {
                        SettingsGroupCard(
                            title = "Reset & Danger Zone",
                            initiallyExpanded = false,
                            badgeText = "RESET",
                            badgeColor = BadRed
                        ) {
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
                                Text("Clear All Data & Cache")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }

    if (showSyncDialog) {
        LiveSyncDialog(
            currentSettings = s,
            liveData = liveRegulatoryData,
            isLoading = isSyncing,
            onDismiss = { showSyncDialog = false },
            onApplySettings = { updated ->
                onUpdateSettings(updated)
                showSyncDialog = false
                Toast.makeText(context, "Official Czech benchmarks applied", Toast.LENGTH_SHORT).show()
            }
        )
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
                            Toast.makeText(context, "Settings successfully restored", Toast.LENGTH_SHORT).show()
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall.copy(
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
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (collapsible) Modifier.clickable { expanded = !expanded } else Modifier),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (badgeText != null) {
                        ColorPill(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            horizontalPadding = 8.dp,
                            verticalPadding = 3.5.dp
                        )
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

    LaunchedEffect(value) {
        if (!isFocused) {
            val currentParsed = textValue.replace(',', '.').toDoubleOrNull()
            if (currentParsed == null || currentParsed != value) {
                textValue = formatVal(value)
            }
        }
    }

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
            trailingIcon = if (isFocused && textValue.isNotEmpty() && !readOnly) {
                {
                    IconButton(
                        onClick = {
                            textValue = ""
                            onValueChange(0.0)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else null,
            singleLine = true,
            modifier = Modifier
                .width(140.dp)
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
    val haptic = LocalHapticFeedback.current
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
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(it)
            },
            colors = SwitchDefaults.colors(checkedThumbColor = BrandTeal, checkedTrackColor = BrandTeal.copy(alpha = 0.5f))
        )
    }
}

private fun liveDataSubtitle(data: com.example.domain.CzechRegulatoryData?): String {
    return if (data != null) {
        "ČSÚ CPI: ${data.csuCpiInflationPct}% · EUR: ${String.format("%.2f", data.eurCzkRate)} · USD: ${String.format("%.2f", data.usdCzkRate)}"
    } else {
        "Check live ČSÚ CPI, ČNB rates, and ZDP tax credits"
    }
}
