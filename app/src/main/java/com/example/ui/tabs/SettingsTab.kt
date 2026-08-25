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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.CustomExpenseItem
import com.example.domain.CustomLumpSumItem
import com.example.domain.FullCalculationState
import com.example.domain.parseCustomExpenses
import com.example.domain.parseCustomLumpSums
import com.example.domain.parseDeletedCategories
import com.example.domain.serializeCustomExpenses
import com.example.domain.serializeCustomLumpSums
import com.example.domain.serializeDeletedCategories
import com.example.ui.components.ColorPill
import com.example.ui.components.LiveSyncDialog
import com.example.ui.components.MetricInfo
import com.example.ui.components.MetricInfoDialog
import com.example.ui.components.rememberMetricInfoState
import com.example.ui.components.infoTapHold
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.BackupManager
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtPct
import kotlinx.coroutines.delay
import java.util.UUID

private object SettingsMetricInfos {
    val swr = MetricInfo(
        title = "Safe Withdrawal Rate (SWR)",
        category = "Actuarial Science",
        formulaOrRule = "Initial Annual Spend / Starting Portfolio Size",
        explanation = "The percentage of your investment portfolio withdrawn in year 1 of retirement, subsequently adjusted annually for CPI inflation. Based on the Trinity Study (Bengen, 1994). A 3.5% SWR historically provides a 99%+ 40-year portfolio survival rate.",
        statutoryReference = "Trinity Study & Modern Portfolio Theory",
        practicalImplication = "Dropping SWR from 4.0% to 3.5% provides extra safety margin against sequence-of-returns risk.",
        accentColor = Color(0xFF0F766E)
    )

    val safetyBuffer = MetricInfo(
        title = "FIRE Safety Capital Buffer",
        category = "Risk Management",
        formulaOrRule = "Target Base Portfolio * (1 + Safety Buffer %)",
        explanation = "An additional discretionary capital cushion added on top of your baseline FIRE nest egg. Shields against healthcare shocks, extended deep recessions, or unforeseen property maintenance.",
        practicalImplication = "A 10% buffer on a 10M CZK target adds 1,000,000 CZK in extra margin of safety.",
        accentColor = Color(0xFFD97706)
    )

    val spouseCredit = MetricInfo(
        title = "Spouse Tax Credit (§ 35ba)",
        category = "Czech Tax Code",
        formulaOrRule = "24,840 CZK/yr deduction if spouse's own income < 68,000 CZK",
        explanation = "Annual tax credit claimed by one spouse when the other has own annual gross income not exceeding 68,000 CZK and takes care of a child under 3 years old. Crucially, state parental allowance (rodičovský příspěvek) is legally excluded from this income ceiling.",
        statutoryReference = "§ 35ba odst. 1 písm. b) Act No. 586/1992 Coll.",
        practicalImplication = "Directly reduces personal income tax liability by up to 24,840 CZK per year (2,070 CZK/month).",
        accentColor = Color(0xFF16A34A)
    )

    val childBonus = MetricInfo(
        title = "Child Tax Credit & Bonus (§ 35c)",
        category = "Czech Tax Code",
        formulaOrRule = "1st child: 15,204 CZK/yr · 2nd child: 22,320 CZK/yr · 3rd+: 27,840 CZK/yr",
        explanation = "Progressive tax allowance per dependent child. If your tax liability reaches zero, the unused portion is paid out by the state directly to you as a cash tax bonus (daňový bonus).",
        statutoryReference = "§ 35c Act No. 586/1992 Coll.",
        practicalImplication = "Provides direct cash rebates when tax liability is low.",
        accentColor = Color(0xFF0F766E)
    )

    val monteCarlo = MetricInfo(
        title = "Monte Carlo Volatility Modeling",
        category = "Statistical Simulation",
        formulaOrRule = "Geometric Brownian Motion · 1,000 Iterations",
        explanation = "Simulates 1,000 randomized market return paths using historical asset volatility (16% std dev) and expected inflation. Renders 5th percentile (bear worst case), 50th percentile (median), and 95th percentile (bull optimal) wealth trajectories.",
        practicalImplication = "Ensures financial planning accounts for unpredictable market sequences rather than unrealistic linear returns.",
        accentColor = Color(0xFF0F766E)
    )

    val cpiInflation = MetricInfo(
        title = "CPI Inflation Compounding",
        category = "Macroeconomic Model",
        formulaOrRule = "Future Cost = Present Cost * (1 + CPI)^Years",
        explanation = "Annual consumer price inflation rate used to compound future living expenses, child costs, and lifestyle goals.",
        statutoryReference = "Czech Statistical Office (ČSÚ)",
        practicalImplication = "At 2.5% inflation, living costs double approximately every 28 years.",
        accentColor = Color(0xFFDC2626)
    )

    val statePensionAge = MetricInfo(
        title = "Statutory State Pension Age",
        category = "Czech Pension System",
        formulaOrRule = "Act No. 155/1995 Coll. · Statutory retirement age",
        explanation = "The legal retirement age when state pillar 1 pension benefits begin. In the FIRE model, your private investment portfolio bridges living expenses from early retirement until this age.",
        statutoryReference = "Act No. 155/1995 Coll. on Pension Insurance",
        practicalImplication = "Earlier personal retirement requires a larger private bridge fund to cover living costs before state pensions kick in.",
        accentColor = Color(0xFF0F766E)
    )
}

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
    var showAddLumpSumDialog by remember { mutableStateOf(false) }
    var newLumpSumName by remember { mutableStateOf("") }
    var newLumpSumYear by remember { mutableStateOf("") }
    var newLumpSumAmount by remember { mutableStateOf("") }
    val infoState = rememberMetricInfoState()

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

                    // 1. Earned & Side Incomes
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
                            NumberSettingField(label = "Net Salary", value = s.vSalary, onValueChange = { onUpdateSettings(s.copy(vSalary = it)) })
                            NumberSettingField(label = "Meal Vouchers Monthly", value = s.vMealVouchersMonthly, onValueChange = { onUpdateSettings(s.copy(vMealVouchersMonthly = it)) })
                            NumberSettingField(label = "Other Monthly Inflows / Side Income", value = s.vOtherInflowsMonthly, onValueChange = { onUpdateSettings(s.copy(vOtherInflowsMonthly = it)) })

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            val monthNames = listOf(
                                "January", "February", "March", "April", "May", "June",
                                "July", "August", "September", "October", "November", "December"
                            )
                            val returnMonthLabel = "${monthNames.getOrElse(s.eReturnMonth.coerceIn(1, 12) - 1) { "M${s.eReturnMonth}" }} ${s.eReturnYear}"
                            val isAlreadyEmployed = s.baseYear > s.eReturnYear || (s.baseYear == s.eReturnYear && s.eReturnMonth <= 1)

                            if (isAlreadyEmployed) {
                                Text(
                                    text = "Eleonora's Incomes (Employed)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandGold)
                                )
                                NumberSettingField(label = "Net Salary", value = s.eStartingSalary, onValueChange = { onUpdateSettings(s.copy(eStartingSalary = it)) })
                                NumberSettingField(label = "Other Monthly Inflows / Side Income", value = s.eOtherInflowsMonthly, onValueChange = { onUpdateSettings(s.copy(eOtherInflowsMonthly = it)) })
                                NumberSettingField(label = "Annual Salary Growth (%)", value = s.eSalaryGrowthPct, onValueChange = { onUpdateSettings(s.copy(eSalaryGrowthPct = it)) })
                                NumberSettingField(label = "Reinvested Share of Salary (%)", value = s.eReinvestedPct, onValueChange = { onUpdateSettings(s.copy(eReinvestedPct = it)) })

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                YearMonthSettingField(
                                    label = "Planned Return",
                                    yearValue = s.eReturnYear,
                                    monthValue = s.eReturnMonth,
                                    onValueChange = { yr, mo -> onUpdateSettings(s.copy(eReturnYear = yr, eReturnMonth = mo)) }
                                )
                            } else {
                                Text(
                                    text = "Eleonora's Incomes (Parental Leave until $returnMonthLabel)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandGold)
                                )
                                NumberSettingField(label = "Parental Allowance Monthly", value = s.eParentalAllowanceMonthly, onValueChange = { onUpdateSettings(s.copy(eParentalAllowanceMonthly = it)) })
                                NumberSettingField(label = "Lecturing Monthly", value = s.eLecturingMonthly, onValueChange = { onUpdateSettings(s.copy(eLecturingMonthly = it)) })
                                NumberSettingField(label = "Other Monthly Inflows / Side Income", value = s.eOtherInflowsMonthly, onValueChange = { onUpdateSettings(s.copy(eOtherInflowsMonthly = it)) })

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Text(
                                    text = "Eleonora's Future Return to Work ($returnMonthLabel+)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                YearMonthSettingField(
                                    label = "Planned Return",
                                    yearValue = s.eReturnYear,
                                    monthValue = s.eReturnMonth,
                                    onValueChange = { yr, mo -> onUpdateSettings(s.copy(eReturnYear = yr, eReturnMonth = mo)) }
                                )
                                NumberSettingField(label = "Future Starting Salary Net", value = s.eStartingSalary, onValueChange = { onUpdateSettings(s.copy(eStartingSalary = it)) })
                                NumberSettingField(label = "Future Salary Growth (%)", value = s.eSalaryGrowthPct, onValueChange = { onUpdateSettings(s.copy(eSalaryGrowthPct = it)) })
                                NumberSettingField(label = "Future Reinvested Share (%)", value = s.eReinvestedPct, onValueChange = { onUpdateSettings(s.copy(eReinvestedPct = it)) })
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            Text(
                                text = "Gifts & Lump Sum Inflows",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandTeal)
                            )
                            NumberSettingField(label = "Family Support Gift Monthly", value = s.familyGiftMonthly, onValueChange = { onUpdateSettings(s.copy(familyGiftMonthly = it)) })

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                text = "Primary Planned Lump Sum",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            BooleanSettingField(label = "Include Primary Lump Sum", checked = s.lumpSumInclude, onCheckedChange = { onUpdateSettings(s.copy(lumpSumInclude = it)) })
                            if (s.lumpSumInclude) {
                                NumberSettingField(label = "Planned Year", value = s.lumpSumYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(lumpSumYear = it.toInt())) })
                                NumberSettingField(label = "Planned Amount", value = s.lumpSumAmount, onValueChange = { onUpdateSettings(s.copy(lumpSumAmount = it)) })
                            }

                            val customLumpSums = remember(s.customLumpSumsJson) { parseCustomLumpSums(s.customLumpSumsJson) }
                            if (customLumpSums.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Text(
                                    text = "Additional Lump Sum Inflows",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                customLumpSums.forEach { item ->
                                    key(item.id) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${item.name} (${item.year})",
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                    Text(
                                                        text = fmtCZK(item.amount),
                                                        style = MaterialTheme.typography.bodySmall.copy(color = GoodGreen, fontWeight = FontWeight.SemiBold)
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Switch(
                                                        checked = item.enabled,
                                                        onCheckedChange = { enabledState ->
                                                            val updated = customLumpSums.map {
                                                                if (it.id == item.id) it.copy(enabled = enabledState) else it
                                                            }
                                                            onUpdateSettings(s.copy(customLumpSumsJson = serializeCustomLumpSums(updated)))
                                                        },
                                                        modifier = Modifier.scale(0.8f)
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            val updated = customLumpSums.filter { it.id != item.id }
                                                            onUpdateSettings(s.copy(customLumpSumsJson = serializeCustomLumpSums(updated)))
                                                        },
                                                        modifier = Modifier.size(48.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete lump sum",
                                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    newLumpSumName = ""
                                    newLumpSumYear = "${s.baseYear + 5}"
                                    newLumpSumAmount = ""
                                    showAddLumpSumDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Additional Lump Sum")
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
                                Text(
                                    text = "Indexed annually each July to ČSÚ CPI inflation (${s.cpiInflationPct}%)",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.5.sp
                                    ),
                                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                                )
                            }
                            if (!deletedSet.contains("groceries")) {
                                NumberSettingField(label = "Groceries & Daily Living", value = s.groceriesMonthly, onValueChange = { onUpdateSettings(s.copy(groceriesMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("groceries", s.copy(groceriesMonthly = 0.0))) })
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
                            if (!deletedSet.contains("other_discretionary") && s.otherDiscretionaryMonthly > 0.0) {
                                NumberSettingField(label = "Other Discretionary", value = s.otherDiscretionaryMonthly, onValueChange = { onUpdateSettings(s.copy(otherDiscretionaryMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("other_discretionary", s.copy(otherDiscretionaryMonthly = 0.0))) })
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
                                Text("Add Custom Category")
                            }
                        }
                    }

                    // 4. Family & Children
                    item {
                        SettingsGroupCard(
                            title = "Family & Children",
                            initiallyExpanded = false,
                            badgeText = if (s.childExpensesEnabled) "ACTIVE" else "OFF",
                            badgeColor = BrandTeal,
                            info = SettingsMetricInfos.childBonus,
                            onShowInfo = { infoState.show(it) }
                        ) {
                            BooleanSettingField(label = "Enable Family Child Expenses", checked = s.childExpensesEnabled, onCheckedChange = { onUpdateSettings(s.copy(childExpensesEnabled = it)) })

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            BooleanSettingField(label = "Include Child 1", checked = s.child1Enabled, onCheckedChange = { onUpdateSettings(s.copy(child1Enabled = it)) })
                            if (s.child1Enabled) {
                                NumberSettingField(label = "Child 1 Birth Year", value = s.child1BirthYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(child1BirthYear = it.toInt())) })
                                NumberSettingField(label = "Child 1 Tax Bonus Annual", value = s.child1TaxBonusAnnual, onValueChange = { onUpdateSettings(s.copy(child1TaxBonusAnnual = it)) })

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Stage Expense Estimates (per Child)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                NumberSettingField(label = "Toddler Monthly", value = s.childToddlerMonthly, onValueChange = { onUpdateSettings(s.copy(childToddlerMonthly = it)) })
                                NumberSettingField(label = "Preschool Monthly", value = s.childPreschoolMonthly, onValueChange = { onUpdateSettings(s.copy(childPreschoolMonthly = it)) })
                                NumberSettingField(label = "School Monthly", value = s.childSchoolMonthly, onValueChange = { onUpdateSettings(s.copy(childSchoolMonthly = it)) })
                                NumberSettingField(label = "Teen Monthly", value = s.childTeenMonthly, onValueChange = { onUpdateSettings(s.copy(childTeenMonthly = it)) })
                                NumberSettingField(label = "Uni Monthly", value = s.childUniMonthly, onValueChange = { onUpdateSettings(s.copy(childUniMonthly = it)) })
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            BooleanSettingField(label = "Include Planned Child 2 Milestone", checked = s.child2Enabled, onCheckedChange = { onUpdateSettings(s.copy(child2Enabled = it)) })
                            if (s.child2Enabled) {
                                NumberSettingField(label = "Child 2 Planned Birth Year", value = s.child2BirthYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(child2BirthYear = it.toInt())) })
                                NumberSettingField(label = "Child 2 Tax Bonus Annual", value = s.child2TaxBonusAnnual, onValueChange = { onUpdateSettings(s.copy(child2TaxBonusAnnual = it)) })
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
                                label = "Brokerage / ETF Portfolio",
                                value = s.liquidPortfolioCurrent,
                                onValueChange = { onUpdateSettings(s.copy(liquidPortfolioCurrent = it)) },
                                testTagStr = "input_liquid_port"
                            )
                            NumberSettingField(
                                label = "DIP Balance",
                                value = s.dipBalanceCurrent,
                                onValueChange = { onUpdateSettings(s.copy(dipBalanceCurrent = it)) }
                            )
                            NumberSettingField(
                                label = "DPS Pension Balance",
                                value = s.dpsBalanceCurrent,
                                onValueChange = { onUpdateSettings(s.copy(dpsBalanceCurrent = it)) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(text = "Eleonora's Balances", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandGold))
                            NumberSettingField(
                                label = "Brokerage / ETF Portfolio",
                                value = s.eLiquidPortfolioCurrent,
                                onValueChange = { onUpdateSettings(s.copy(eLiquidPortfolioCurrent = it)) }
                            )
                            NumberSettingField(
                                label = "DIP Balance",
                                value = s.eDipBalanceCurrent,
                                onValueChange = { onUpdateSettings(s.copy(eDipBalanceCurrent = it)) }
                            )
                            NumberSettingField(
                                label = "DPS Pension Balance",
                                value = s.eDpsBalanceCurrent,
                                onValueChange = { onUpdateSettings(s.copy(eDpsBalanceCurrent = it)) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(text = "Cash & Emergency Reserve", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                            NumberSettingField(
                                label = "Liquid Bank & Savings Accounts",
                                value = s.emergencyReserveCurrent,
                                onValueChange = { onUpdateSettings(s.copy(emergencyReserveCurrent = it)) }
                            )
                            NumberSettingField(
                                label = "Target Reserve Threshold",
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
                                label = "Monthly Brokerage ETF DCA",
                                value = s.portuDcaMonthly,
                                onValueChange = { onUpdateSettings(s.copy(portuDcaMonthly = it)) }
                            )
                            NumberSettingField(
                                label = "DIP Monthly Contribution",
                                value = s.dipContributionMonthly,
                                onValueChange = { onUpdateSettings(s.copy(dipContributionMonthly = it)) }
                            )
                            NumberSettingField(
                                label = "DPS Monthly Own Contribution",
                                value = s.dpsOwnContributionMonthly,
                                onValueChange = { onUpdateSettings(s.copy(dpsOwnContributionMonthly = it)) }
                            )
                            NumberSettingField(
                                label = "Employer Pension Match Monthly",
                                value = s.employerRetirementMonthly,
                                onValueChange = { onUpdateSettings(s.copy(employerRetirementMonthly = it)) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(text = "Eleonora's DCA", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandGold))
                            NumberSettingField(
                                label = "Monthly Brokerage ETF DCA",
                                value = s.ePortuDcaMonthly,
                                onValueChange = { onUpdateSettings(s.copy(ePortuDcaMonthly = it)) }
                            )
                            NumberSettingField(
                                label = "DIP Monthly Contribution",
                                value = s.eDipContributionMonthly,
                                onValueChange = { onUpdateSettings(s.copy(eDipContributionMonthly = it)) }
                            )
                            NumberSettingField(
                                label = "DPS Monthly Contribution",
                                value = s.eDpsOwnContributionMonthly,
                                onValueChange = { onUpdateSettings(s.copy(eDpsOwnContributionMonthly = it)) }
                            )
                            NumberSettingField(
                                label = "Employer Pension Match Monthly",
                                value = s.eEmployerRetirementMonthly,
                                onValueChange = { onUpdateSettings(s.copy(eEmployerRetirementMonthly = it)) }
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
                            badgeColor = BrandTeal,
                            info = SettingsMetricInfos.swr,
                            onShowInfo = { infoState.show(it) }
                        ) {
                            NumberSettingField(
                                label = "Base Planning Year",
                                value = s.baseYear.toDouble(),
                                minValue = 2000.0,
                                maxValue = 2200.0,
                                onValueChange = { yr ->
                                    val y = yr.toInt()
                                    val newAge = (y - com.example.data.VACLAV_BIRTH_YEAR).coerceIn(15, 80)
                                    onUpdateSettings(s.copy(baseYear = y, primaryAge = newAge))
                                }
                            )
                            NumberSettingField(
                                label = "Expected CPI Inflation (%)",
                                value = s.cpiInflationPct,
                                minValue = 0.0,
                                maxValue = 20.0,
                                onValueChange = { onUpdateSettings(s.copy(cpiInflationPct = it)) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            NumberSettingField(label = "Safe Withdrawal Rate SWR (%)", value = s.safeWithdrawalRatePct, minValue = 0.0, maxValue = 10.0, onValueChange = { onUpdateSettings(s.copy(safeWithdrawalRatePct = it)) })
                            NumberSettingField(label = "Safety Buffer (%)", value = s.safetyBufferPct, onValueChange = { onUpdateSettings(s.copy(safetyBufferPct = it)) })
                            NumberSettingField(label = "Expected Portfolio Nominal Return (%)", value = s.portfolioNominalReturnPct, onValueChange = { onUpdateSettings(s.copy(portfolioNominalReturnPct = it)) })
                            NumberSettingField(label = "DPS Gross Return (%)", value = s.dpsGrossReturnPct, onValueChange = { onUpdateSettings(s.copy(dpsGrossReturnPct = it)) })
                            NumberSettingField(label = "DPS Annual Management Fee (%, cap 0.5%)", value = s.dpsAnnualFeePct, minValue = 0.0, maxValue = 5.0, onValueChange = { onUpdateSettings(s.copy(dpsAnnualFeePct = it)) })
                            NumberSettingField(label = "Manual FIRE Target Override (0 = auto)", value = s.fireTargetOverride, onValueChange = { onUpdateSettings(s.copy(fireTargetOverride = it)) })
                            NumberSettingField(label = "Lifestyle Cost at FIRE (CZK/mo)", value = s.lifestyleCostAtFireMonthly, onValueChange = { onUpdateSettings(s.copy(lifestyleCostAtFireMonthly = it)) })
                            if (s.lifestyleCostAtFireMonthly <= 0.0) {
                                Text(
                                    text = "Linked to current living expenses: ${fmtCZK(state.totalLivingCostMonthly)}/mo",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.5.sp
                                    ),
                                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                )
                            }
                            NumberSettingField(label = "Václav's State Pension Monthly", value = s.vStatePensionMonthly, onValueChange = { onUpdateSettings(s.copy(vStatePensionMonthly = it)) })
                            NumberSettingField(label = "Václav's State Pension Age", value = s.vStatePensionAge.toDouble(), minValue = 55.0, maxValue = 75.0, onValueChange = { onUpdateSettings(s.copy(vStatePensionAge = it.toInt())) })
                            if (!s.isSingleHousehold) {
                                NumberSettingField(label = "Eleonora's State Pension Monthly", value = s.eStatePensionMonthly, onValueChange = { onUpdateSettings(s.copy(eStatePensionMonthly = it)) })
                                NumberSettingField(label = "Eleonora's State Pension Age", value = s.eStatePensionAge.toDouble(), minValue = 55.0, maxValue = 75.0, onValueChange = { onUpdateSettings(s.copy(eStatePensionAge = it.toInt())) })
                            }
                        }
                    }

                    // 4. Advanced: Czech Tax Shield & Statutory Engine
                    item {
                        SettingsGroupCard(
                            title = "Czech Tax Shield & Statutory Engine",
                            initiallyExpanded = false,
                            badgeText = "ADVANCED",
                            badgeColor = BrandGold,
                            info = SettingsMetricInfos.spouseCredit,
                            onShowInfo = { infoState.show(it) }
                        ) {
                            Text(
                                text = "Live Tax Shield Summary",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TaxSummaryRow(
                                label = "Eleonora Income",
                                status = fmtCZK(state.taxReturnHelper.spouseOwnIncome),
                                isGood = state.taxReturnHelper.spouseOwnIncome <= s.spouseIncomeLimitAnnual
                            )
                            TaxSummaryRow(
                                label = "Eleonora Tax Credit Eligible (§ 35ba)",
                                status = if (state.taxReturnHelper.spouseEligible) "Yes (+${fmtCZK(state.taxReturnHelper.spouseCredit)})" else "No",
                                isGood = state.taxReturnHelper.spouseEligible
                            )
                            TaxSummaryRow(
                                label = "Child 1 Tax Bonus (§ 35c)",
                                status = if (s.child1Enabled) "+${fmtCZK(s.child1TaxBonusAnnual)}/yr (${fmtCZK(s.child1TaxBonusAnnual / 12)}/mo)" else "Disabled",
                                isGood = s.child1Enabled
                            )
                            TaxSummaryRow(
                                label = "Child 2 Tax Bonus (§ 35c)",
                                status = if (s.child2Enabled) "+${fmtCZK(s.child2TaxBonusAnnual)}/yr (${fmtCZK(s.child2TaxBonusAnnual / 12)}/mo)" else "Disabled",
                                isGood = s.child2Enabled
                            )
                            TaxSummaryRow(
                                label = "Total Child Tax Bonus (§ 35c)",
                                status = "+${fmtCZK(state.taxReturnHelper.childBonus)}/yr",
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
                            NumberSettingField(label = "Higher Bracket Threshold Annual", value = s.taxSecondBracketThresholdAnnual, onValueChange = { onUpdateSettings(s.copy(taxSecondBracketThresholdAnnual = it)) })
                            NumberSettingField(label = "Basic Taxpayer Credit Annual", value = s.taxpayerCreditAnnual, onValueChange = { onUpdateSettings(s.copy(taxpayerCreditAnnual = it)) })
                            NumberSettingField(label = "Retirement Deduction Ceiling Annual", value = s.taxDeductionCeilingAnnual, onValueChange = { onUpdateSettings(s.copy(taxDeductionCeilingAnnual = it)) })
                            NumberSettingField(label = "Eleonora Tax Credit Annual", value = s.spouseTaxCreditAnnual, onValueChange = { onUpdateSettings(s.copy(spouseTaxCreditAnnual = it)) })
                            NumberSettingField(label = "Eleonora Income Limit Annual", value = s.spouseIncomeLimitAnnual, onValueChange = { onUpdateSettings(s.copy(spouseIncomeLimitAnnual = it)) })
                            BooleanSettingField(label = "Include Eleonora Tax Credit", checked = s.includeSpouseCredit, onCheckedChange = { onUpdateSettings(s.copy(includeSpouseCredit = it)) })
                            BooleanSettingField(label = "Has Child Under 3", checked = s.hasChildUnder3, onCheckedChange = { onUpdateSettings(s.copy(hasChildUnder3 = it)) })
                            NumberSettingField(label = "Min Wage Monthly", value = s.minWageMonthly, onValueChange = { onUpdateSettings(s.copy(minWageMonthly = it)) })
                            NumberSettingField(label = "Child 3+ Tax Bonus Annual", value = s.child3PlusTaxBonusAnnual, onValueChange = { onUpdateSettings(s.copy(child3PlusTaxBonusAnnual = it)) })

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            Text(text = "Statutory DPS Pension Subsidies", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            NumberSettingField(label = "DPS Tax Deduction Floor (CZK/mo)", value = s.dpsDeductionThresholdMonthly, onValueChange = { onUpdateSettings(s.copy(dpsDeductionThresholdMonthly = it)) })
                            NumberSettingField(label = "DPS Min Subsidy Deposit (CZK/mo)", value = s.dpsMinDepositForSubsidy, onValueChange = { onUpdateSettings(s.copy(dpsMinDepositForSubsidy = it)) })
                            NumberSettingField(label = "DPS Max Standard Subsidy (CZK/mo)", value = s.dpsStandardSubsidyMaxMonthly, onValueChange = { onUpdateSettings(s.copy(dpsStandardSubsidyMaxMonthly = it)) })
                            NumberSettingField(label = "DPS Standard Subsidy Rate (%)", value = s.dpsSubsidyRateStandardPct, onValueChange = { onUpdateSettings(s.copy(dpsSubsidyRateStandardPct = it)) })
                            NumberSettingField(label = "DPS Youth Age Limit (Years)", value = s.dpsYouthAgeLimit.toDouble(), onValueChange = { onUpdateSettings(s.copy(dpsYouthAgeLimit = it.toInt())) })
                            NumberSettingField(label = "DPS Max Youth Subsidy (CZK/mo)", value = s.dpsYouthSubsidyMaxMonthly, onValueChange = { onUpdateSettings(s.copy(dpsYouthSubsidyMaxMonthly = it)) })
                            NumberSettingField(label = "DPS Youth Subsidy Rate (%)", value = s.dpsSubsidyRateYouthPct, onValueChange = { onUpdateSettings(s.copy(dpsSubsidyRateYouthPct = it)) })

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            Text(text = "Monte Carlo Stochastic Risk Engine", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            NumberSettingField(label = "Portfolio Annual Volatility (%)", value = s.monteCarloVolatilityPct, onValueChange = { onUpdateSettings(s.copy(monteCarloVolatilityPct = it)) })
                            NumberSettingField(label = "Simulation Runs", value = s.monteCarloN.toDouble(), minValue = 100.0, maxValue = 400.0, onValueChange = { onUpdateSettings(s.copy(monteCarloN = it.toInt())) })
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
                                        clip.description.extras = android.os.PersistableBundle().apply {
                                            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                                        }
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
                                        appendLine("• Emergency Reserve: ${fmtCZK(s.emergencyReserveCurrent)} (${String.format(java.util.Locale.ROOT, "%.1f", state.emergencyCoverageMonths)} months)")
                                    }
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Financial Summary", summary)
                                    clip.description.extras = android.os.PersistableBundle().apply {
                                        putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                                    }
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
                        label = { Text("Monthly Amount") },
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

    if (showAddLumpSumDialog) {
        AlertDialog(
            onDismissRequest = { showAddLumpSumDialog = false },
            title = { Text("Add Planned Lump Sum", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newLumpSumName,
                        onValueChange = { newLumpSumName = it },
                        label = { Text("Description (e.g. Inheritance / Property / Gift)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newLumpSumYear,
                        onValueChange = { newLumpSumYear = it },
                        label = { Text("Planned Year (e.g. 2032)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newLumpSumAmount,
                        onValueChange = { newLumpSumAmount = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newLumpSumName.trim().ifBlank { "Lump Sum" }
                        val yr = newLumpSumYear.trim().toIntOrNull() ?: (s.baseYear + 5)
                        val sanitizedAmt = newLumpSumAmount.replace(',', '.').trim()
                        val amt = sanitizedAmt.toDoubleOrNull() ?: 0.0
                        if (amt > 0.0) {
                            val currentList = parseCustomLumpSums(s.customLumpSumsJson)
                            val updated = currentList + CustomLumpSumItem(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                year = yr,
                                amount = amt,
                                enabled = true
                            )
                            onUpdateSettings(s.copy(customLumpSumsJson = serializeCustomLumpSums(updated)))
                        }
                        showAddLumpSumDialog = false
                    }
                ) {
                    Text("Add Lump Sum")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLumpSumDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    MetricInfoDialog(
        info = infoState.currentInfo,
        onDismiss = { infoState.dismiss() }
    )
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
    info: MetricInfo? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null,
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
                    if (info != null && onShowInfo != null) {
                        IconButton(
                            onClick = { onShowInfo(info) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info on $title",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
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
    readOnly: Boolean = false,
    minValue: Double = 0.0,
    maxValue: Double? = null
) {
    fun formatVal(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "0"
        return if (v % 1.0 == 0.0) {
            v.toLong().toString()
        } else {
            val rounded = kotlin.math.round(v * 100.0) / 100.0
            if (rounded % 1.0 == 0.0) {
                rounded.toLong().toString()
            } else {
                String.format(java.util.Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
            }
        }
    }

    var textValue by remember { mutableStateOf(formatVal(value)) }
    var isFocused by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun commitCurrentText() {
        if (readOnly) return
        val sanitized = textValue.replace(',', '.').trim()
        val parsed = if (sanitized.isEmpty()) 0.0 else sanitized.toDoubleOrNull()
        if (parsed != null) {
            val clamped = parsed.coerceIn(minValue, maxValue ?: parsed)
            if (clamped != value) {
                onValueChange(clamped)
            }
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
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
private fun YearMonthSettingField(
    label: String,
    yearValue: Int,
    monthValue: Int,
    onValueChange: (year: Int, month: Int) -> Unit
) {
    var yearText by remember { mutableStateOf(yearValue.toString()) }
    var monthText by remember { mutableStateOf(monthValue.toString()) }
    var isYearFocused by remember { mutableStateOf(false) }
    var isMonthFocused by remember { mutableStateOf(false) }

    LaunchedEffect(yearValue) {
        if (!isYearFocused) {
            yearText = yearValue.toString()
        }
    }
    LaunchedEffect(monthValue) {
        if (!isMonthFocused) {
            monthText = monthValue.toString()
        }
    }

    fun commit() {
        val y = yearText.trim().toIntOrNull() ?: yearValue
        val m = (monthText.trim().toIntOrNull() ?: monthValue).coerceIn(1, 12)
        onValueChange(y, m)
    }

    LaunchedEffect(yearText, monthText) {
        if (isYearFocused || isMonthFocused) {
            delay(400)
            commit()
        }
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
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = monthText,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() }.take(2)
                    monthText = filtered
                },
                placeholder = { Text("M", fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { commit() }),
                singleLine = true,
                modifier = Modifier
                    .width(56.dp)
                    .onFocusChanged {
                        if (isMonthFocused && !it.isFocused) commit()
                        isMonthFocused = it.isFocused
                    },
                textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center)
            )
            Text(
                text = "/",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            )
            OutlinedTextField(
                value = yearText,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() }.take(4)
                    yearText = filtered
                },
                placeholder = { Text("YYYY", fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                singleLine = true,
                modifier = Modifier
                    .width(78.dp)
                    .onFocusChanged {
                        if (isYearFocused && !it.isFocused) commit()
                        isYearFocused = it.isFocused
                    },
                textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center)
            )
        }
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
