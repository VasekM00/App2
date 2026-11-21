package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ImportedBankTransactionEntity
import com.example.data.SettingsEntity
import com.example.domain.CustomExpenseItem
import com.example.domain.parseCustomExpenses
import com.example.domain.parseDeletedCategories
import com.example.domain.serializeCustomExpenses
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BrandSky
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.OnBrandTeal
import com.example.ui.theme.brandSolidContentColor
import com.example.util.BankTransactionType
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.SubscriptionAuditor
import java.time.LocalDate

data class UpcomingBill(
    val id: String,
    val dayOfMonth: Int,
    val title: String,
    val provider: String,
    val amount: Double,
    val category: BankTransactionType,
    val icon: ImageVector,
    val paymentType: String = "Recurring debit",
    val isDcaInvestment: Boolean = false,
    val isFromBankStatement: Boolean = false,
    val editSource: BillEditSource? = null
)

@Composable
fun UpcomingBillsCalendarCard(
    transactions: List<ImportedBankTransactionEntity> = emptyList(),
    settings: SettingsEntity? = null,
    dismissedSubscriptionMerchants: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    onDismissSubscription: ((String) -> Unit)? = null,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var selectedDayFilter by remember { mutableStateOf<Int?>(null) }
    var editingBill by remember { mutableStateOf<UpcomingBill?>(null) }
    var showAddBillDialog by remember { mutableStateOf(false) }

    // Derive authentic recurring obligations strictly from:
    // 1. Actually audited bank subscriptions/debits (if transactions exist)
    // 2. Explicit user settings (Rent, DCA Portu, DIP, DPS) and custom expenses
    val bills = remember(transactions, settings, dismissedSubscriptionMerchants) {
        val list = mutableListOf<UpcomingBill>()

        // 1. Detect recurring subscriptions from real bank statements
        val auditedSubs = if (transactions.isNotEmpty()) {
            SubscriptionAuditor.auditSubscriptions(
                transactions = transactions,
                dismissedMerchantKeys = dismissedSubscriptionMerchants
            ).items
        } else {
            emptyList()
        }

        auditedSubs.forEachIndexed { idx, sub ->
            val day = try {
                if (sub.lastBilledDate.isNotBlank()) {
                    LocalDate.parse(sub.lastBilledDate).dayOfMonth
                } else {
                    15
                }
            } catch (_: Exception) {
                15
            }
            list.add(
                UpcomingBill(
                    id = "bank_sub_$idx",
                    dayOfMonth = day,
                    title = sub.displayName,
                    provider = sub.merchantKey,
                    amount = sub.latestMonthlyAmount,
                    category = sub.category,
                    icon = if (sub.category == BankTransactionType.SUBSCRIPTIONS_MEDIA) Icons.Default.Tv else Icons.Default.CreditCard,
                    paymentType = "Statement Subscription",
                    isDcaInvestment = false,
                    isFromBankStatement = true,
                    editSource = BillEditSource.StatementSubscription(
                        merchantKey = sub.merchantKey,
                        displayName = sub.displayName,
                        amount = sub.latestMonthlyAmount,
                        dayOfMonth = day
                    )
                )
            )
        }

        // 2. Add real budget settings
        if (settings != null) {
            val deletedSet = parseDeletedCategories(settings.deletedCategoriesJson)
            if ("rent" !in deletedSet && settings.rentMonthly > 0.0) {
                list.add(
                    UpcomingBill(
                        id = "sett_rent",
                        dayOfMonth = 1,
                        title = "Housing Rent & Residence",
                        provider = "Property Landlord",
                        amount = settings.rentMonthly,
                        category = BankTransactionType.HOUSING_RENT,
                        icon = Icons.Default.Home,
                        paymentType = "Standing Order",
                        isDcaInvestment = false,
                        editSource = BillEditSource.BudgetField("sett_rent", settings.rentMonthly)
                    )
                )
            }

            if (settings.portuDcaMonthly > 0.0) {
                list.add(
                    UpcomingBill(
                        id = "sett_portu_v",
                        dayOfMonth = 10,
                        title = "Portu ETF Investment (${settings.primaryName})",
                        provider = "WOOD & Company",
                        amount = settings.portuDcaMonthly,
                        category = BankTransactionType.INVESTMENT_PORTU,
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        paymentType = "Automated DCA",
                        isDcaInvestment = true,
                        editSource = BillEditSource.BudgetField("sett_portu_v", settings.portuDcaMonthly)
                    )
                )
            }

            if (!settings.isSingleHousehold && settings.ePortuDcaMonthly > 0.0) {
                list.add(
                    UpcomingBill(
                        id = "sett_portu_e",
                        dayOfMonth = 10,
                        title = "Portu ETF Investment (${settings.spouseName})",
                        provider = "WOOD & Company",
                        amount = settings.ePortuDcaMonthly,
                        category = BankTransactionType.INVESTMENT_PORTU,
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        paymentType = "Automated DCA",
                        isDcaInvestment = true,
                        editSource = BillEditSource.BudgetField("sett_portu_e", settings.ePortuDcaMonthly)
                    )
                )
            }

            if (settings.dipContributionMonthly > 0.0) {
                list.add(
                    UpcomingBill(
                        id = "sett_dip_v",
                        dayOfMonth = 15,
                        title = "DIP Retirement Product (${settings.primaryName})",
                        provider = "Long-Term Investment Product",
                        amount = settings.dipContributionMonthly,
                        category = BankTransactionType.INVESTMENT_DIP,
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        paymentType = "Standing Order",
                        isDcaInvestment = true,
                        editSource = BillEditSource.BudgetField("sett_dip_v", settings.dipContributionMonthly)
                    )
                )
            }

            if (!settings.isSingleHousehold && settings.eDipContributionMonthly > 0.0) {
                list.add(
                    UpcomingBill(
                        id = "sett_dip_e",
                        dayOfMonth = 15,
                        title = "DIP Retirement Product (${settings.spouseName})",
                        provider = "Long-Term Investment Product",
                        amount = settings.eDipContributionMonthly,
                        category = BankTransactionType.INVESTMENT_DIP,
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        paymentType = "Standing Order",
                        isDcaInvestment = true,
                        editSource = BillEditSource.BudgetField("sett_dip_e", settings.eDipContributionMonthly)
                    )
                )
            }

            if (settings.dpsOwnContributionMonthly > 0.0) {
                list.add(
                    UpcomingBill(
                        id = "sett_dps_v",
                        dayOfMonth = 20,
                        title = "DPS Pension Scheme (${settings.primaryName})",
                        provider = "Pension Company",
                        amount = settings.dpsOwnContributionMonthly,
                        category = BankTransactionType.INVESTMENT_DPS,
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        paymentType = "Standing Order",
                        isDcaInvestment = true,
                        editSource = BillEditSource.BudgetField("sett_dps_v", settings.dpsOwnContributionMonthly)
                    )
                )
            }

            if (!settings.isSingleHousehold && settings.eDpsOwnContributionMonthly > 0.0) {
                list.add(
                    UpcomingBill(
                        id = "sett_dps_e",
                        dayOfMonth = 20,
                        title = "DPS Pension Scheme (${settings.spouseName})",
                        provider = "Pension Company",
                        amount = settings.eDpsOwnContributionMonthly,
                        category = BankTransactionType.INVESTMENT_DPS,
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        paymentType = "Standing Order",
                        isDcaInvestment = true,
                        editSource = BillEditSource.BudgetField("sett_dps_e", settings.eDpsOwnContributionMonthly)
                    )
                )
            }

            // Custom user expenses (if any defined)
            val customExpenses = parseCustomExpenses(settings.customExpensesJson)
            customExpenses.filter { it.amount > 0.0 }.forEachIndexed { i, exp ->
                val day = (exp.dayOfMonth ?: 25).coerceIn(1, 28)
                list.add(
                    UpcomingBill(
                        id = "custom_exp_${exp.id}_$i",
                        dayOfMonth = day,
                        title = exp.name,
                        provider = "Custom Expense",
                        amount = exp.amount,
                        category = BankTransactionType.GENERAL_EXPENSE,
                        icon = Icons.Default.CreditCard,
                        paymentType = "Recurring Payment",
                        isDcaInvestment = false,
                        editSource = BillEditSource.CustomBill(exp.id, exp.name, exp.amount, day)
                    )
                )
            }
        }

        list.sortedBy { it.dayOfMonth }
    }

    val totalBills = bills.sumOf { it.amount }
    val livingBills = bills.filter { !it.isDcaInvestment }.sumOf { it.amount }
    val dcaBills = bills.filter { it.isDcaInvestment }.sumOf { it.amount }

    val filteredBills = if (selectedDayFilter != null) {
        bills.filter { it.dayOfMonth == selectedDayFilter }
    } else {
        bills
    }

    val billsCalendarInfo = MetricInfo(
        title = "Upcoming Bills & Cash Calendar",
        category = "Cash Flow Management",
        formulaOrRule = "30-Day Obligations = Living Rent + Custom Bills + Automated DCA + Bank Subscriptions",
        explanation = "Built directly from your Budget Settings (Rent on 1st, Portu ETF on 10th, DIP on 15th, DPS on 20th, Custom Bills on their scheduled day) plus recurring subscriptions detected in imported bank statements. Tap any row to edit its amount or add a recurring bill.",
        practicalImplication = "Scheduling automated DCA right after salary receipt guarantees pay-yourself-first wealth compounding.",
        accentColor = BrandTeal
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("upcoming_bills_calendar_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            BrandTeal.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header with accurate source attribution & collapsible toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable(role = Role.Button) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            expanded = !expanded
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrandTeal.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = BrandTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Upcoming Bills",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (expanded) {
                                    if (onUpdateSettings != null || onReduceSettings != null) "30-day bills · tap to edit" else "30-day fixed bills & DCA"
                                } else {
                                    "${bills.size} bills · ${fmtCompact(livingBills)}"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if ((onUpdateSettings != null || onReduceSettings != null) && settings != null) {
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(role = Role.Button) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showAddBillDialog = true
                                    }
                                    .testTag("btn_add_upcoming_bill"),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BrandTeal.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.28f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = BrandTeal,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Add",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = BrandTeal
                                            ),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onShowInfo?.invoke(billsCalendarInfo)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Calendar information",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (expanded) {
                // 3 Summary Metric Boxes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BillStatBox(
                        title = "Total Projected",
                        value = fmtCompact(totalBills),
                        subtitle = "${bills.size} scheduled",
                        accentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    BillStatBox(
                        title = "Fixed Bills",
                        value = fmtCompact(livingBills),
                        subtitle = "Rent & utilities",
                        accentColor = AmberWarning,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    BillStatBox(
                        title = "Auto-Investing",
                        value = fmtCompact(dcaBills),
                        subtitle = "Monthly wealth transfer",
                        accentColor = GoodGreen,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }

                if (bills.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No scheduled bills or recurring debits found. Set up your budget or import a bank statement.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.5.sp
                                ),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                softWrap = true
                            )
                        }
                    }
                } else {
                    // Day of month filter timeline
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "TIMELINE BY DUE DAY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.4.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isAllSelected = selectedDayFilter == null
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedDayFilter = null
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isAllSelected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (isAllSelected) BrandTeal else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "All (${bills.size})",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = BodyFontFamily,
                                            color = if (isAllSelected) OnBrandTeal else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 11.5.sp
                                        ),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Unique days
                            val uniqueDays = bills.map { it.dayOfMonth }.distinct().sorted()
                            uniqueDays.forEach { day ->
                                val isDaySelected = selectedDayFilter == day
                                val dayBills = bills.filter { it.dayOfMonth == day }
                                val hasDca = dayBills.any { it.isDcaInvestment }
                                val selectedBg = if (hasDca) GoodGreen else BrandSky
                                val selectedFg = brandSolidContentColor(selectedBg)

                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedDayFilter = if (isDaySelected) null else day
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDaySelected) selectedBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, if (isDaySelected) selectedBg else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$day.",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = BodyFontFamily,
                                                fontSize = 12.sp,
                                                color = if (isDaySelected) selectedFg else MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                        Text(
                                            text = if (hasDca) "DCA" else "Bill",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                color = if (isDaySelected) selectedFg.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    // List of Bills separated into Living Obligations vs Scheduled DCA
                    val fixedObligations = filteredBills.filter { !it.isDcaInvestment }
                    val scheduledDcaItems = filteredBills.filter { it.isDcaInvestment }

                    if (fixedObligations.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            fixedObligations.forEach { bill ->
                                val canEditRow = when (bill.editSource) {
                                    is BillEditSource.StatementSubscription -> onDismissSubscription != null
                                    is BillEditSource.BudgetField, is BillEditSource.CustomBill ->
                                        (onUpdateSettings != null || onReduceSettings != null) && settings != null
                                    null -> false
                                }
                                UpcomingBillRow(
                                    bill = bill,
                                    canEdit = canEditRow,
                                    onEditClick = if (canEditRow) { { editingBill = bill } } else null
                                )
                            }
                        }
                    }

                    if (scheduledDcaItems.isNotEmpty()) {
                        if (fixedObligations.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Text(
                            text = "SCHEDULED AUTO-INVESTING (DCA)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.4.sp,
                                color = GoodGreen
                            )
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            scheduledDcaItems.forEach { bill ->
                                val canEditRow = (onUpdateSettings != null || onReduceSettings != null) && settings != null
                                UpcomingBillRow(
                                    bill = bill,
                                    canEdit = canEditRow,
                                    onEditClick = if (canEditRow) { { editingBill = bill } } else null
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }

    editingBill?.let { activeBill ->
        EditUpcomingBillDialog(
            bill = activeBill,
            settings = settings,
            onDismiss = { editingBill = null },
            onUpdateSettings = onUpdateSettings,
            onDismissSubscription = onDismissSubscription,
            onReduceSettings = onReduceSettings
        )
    }

    if (showAddBillDialog && settings != null && (onUpdateSettings != null || onReduceSettings != null)) {
        CustomBillEditorDialog(
            title = "Add Recurring Bill",
            initialName = "",
            initialAmount = 0.0,
            initialDayOfMonth = 25,
            canDelete = false,
            onDismiss = { showAddBillDialog = false },
            onSave = { name, amount, day ->
                com.example.ui.state.dispatchSettingsPatch(
                    settings,
                    onUpdateSettings ?: {},
                    onReduceSettings
                ) { cur ->
                    val existing = parseCustomExpenses(cur.customExpensesJson)
                    val newItem = CustomExpenseItem(
                        id = java.util.UUID.randomUUID().toString(),
                        name = name,
                        amount = amount,
                        dayOfMonth = day
                    )
                    cur.copy(customExpensesJson = serializeCustomExpenses(existing + newItem))
                }
                showAddBillDialog = false
            }
        )
    }
}

@Composable
private fun UpcomingBillRow(
    bill: UpcomingBill,
    canEdit: Boolean = false,
    onEditClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accent = if (bill.isDcaInvestment) GoodGreen else BrandSky

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (canEdit && onEditClick != null) {
                    Modifier.clickable(role = Role.Button) { onEditClick() }
                } else {
                    Modifier
                }
            )
            .testTag("upcoming_bill_item")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Day badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accent.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${bill.dayOfMonth}.",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodyFontFamily,
                                fontSize = 12.5.sp,
                                color = accent
                            )
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = bill.title,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = true
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${bill.provider} · ${bill.paymentType}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = fmtCZK(bill.amount),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = BodyFontFamily,
                        fontSize = 12.5.sp,
                        color = if (bill.isDcaInvestment) GoodGreen else MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    softWrap = false
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (bill.isDcaInvestment) "Investment" else "Fixed Bill",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            color = accent
                        )
                    )
                    if (canEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit ${bill.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BillStatBox(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 6.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 2,
                softWrap = true,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = BodyFontFamily,
                    fontSize = 13.sp,
                    color = accentColor
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                ),
                maxLines = 1,
                softWrap = true,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
