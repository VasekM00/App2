package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.CrossAccountNettingAuditDialog
import com.example.ui.components.ExportReportDialog
import com.example.ui.components.QuickGuideDialog
import com.example.ui.components.StatementImportReviewDialog
import com.example.ui.tabs.CashFlowTab
import com.example.ui.tabs.OverviewTab
import com.example.ui.tabs.ProjectionsTab
import com.example.ui.tabs.SettingsTab

@Immutable
data class NavTabItem(
    val title: String,
    val icon: ImageVector,
    val tag: String
)

@Immutable
data class SearchResult(
    val title: String,
    val category: String,
    val tabIndex: Int,
    val subTabIndex: Int = 0,
    val sectionKey: String? = null
)

@Immutable
data class SearchIndexEntry(
    val title: String,
    val category: String,
    val tabIndex: Int,
    val subTabIndex: Int,
    val keywords: List<String>,
    val sectionKey: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    themeMode: String = "SYSTEM",
    onChangeThemeMode: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.calculationState.collectAsStateWithLifecycle()
    val ledgerEntries by viewModel.ledgerEntries.collectAsStateWithLifecycle()
    val actionStates by viewModel.actionStates.collectAsStateWithLifecycle()
    val liveRegulatoryData by viewModel.liveRegulatoryData.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val pendingStatementImport by viewModel.pendingStatementImport.collectAsStateWithLifecycle()
    val activeAuditReport by viewModel.activeAuditReport.collectAsStateWithLifecycle()
    val importedBankSourcesByMonth by viewModel.importedBankSourcesByMonth.collectAsStateWithLifecycle()
    val lastImportTimestamp by viewModel.lastImportTimestamp.collectAsStateWithLifecycle()
    val lastCzechSyncTimestamp by viewModel.lastCzechSyncTimestamp.collectAsStateWithLifecycle()
    val allImportedTransactions by viewModel.allImportedTransactions.collectAsStateWithLifecycle()
    val dismissedSubscriptionMerchants by viewModel.dismissedSubscriptionMerchants.collectAsStateWithLifecycle()
    val biometricLockEnabled by viewModel.biometricLockEnabled.collectAsStateWithLifecycle()
    val flagSecureEnabled by viewModel.flagSecureEnabled.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var targetCashFlowSubTab by rememberSaveable { mutableIntStateOf(0) }
    var targetProjectionsSubTab by rememberSaveable { mutableIntStateOf(0) }
    var targetSettingsSubTab by rememberSaveable { mutableIntStateOf(0) }
    var targetSettingsSectionKey by rememberSaveable { mutableStateOf<String?>(null) }
    var targetSettingsSectionTrigger by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(selectedTab) {
        if (selectedTab != 3 && targetSettingsSectionKey != null) {
            targetSettingsSectionKey = null
        }
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var showExportReportDialog by rememberSaveable { mutableStateOf(false) }
    var showQuickGuideDialog by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val overviewScrollState = rememberScrollState()
    var scrollToEmergencyReserveTrigger by remember { mutableIntStateOf(0) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiMessage.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    val tabs = remember {
        listOf(
            NavTabItem("Home", Icons.Default.Dashboard, "nav_overview"),
            NavTabItem("Budget", Icons.Default.Payments, "nav_cashflow"),
            NavTabItem("Wealth", Icons.Default.Assessment, "nav_projections"),
            NavTabItem("More", Icons.Default.Settings, "nav_settings")
        )
    }
    val safeSelectedTab = selectedTab.coerceIn(0, tabs.lastIndex)

    BackHandler(enabled = activeAuditReport != null) {
        viewModel.clearAuditReport()
    }
    BackHandler(enabled = pendingStatementImport != null && activeAuditReport == null) {
        viewModel.dismissStatementImport()
    }
    BackHandler(enabled = showQuickGuideDialog && activeAuditReport == null && pendingStatementImport == null) {
        showQuickGuideDialog = false
    }
    BackHandler(enabled = showExportReportDialog && !showQuickGuideDialog && activeAuditReport == null && pendingStatementImport == null) {
        showExportReportDialog = false
    }
    BackHandler(
        enabled = (isSearchExpanded || searchQuery.isNotEmpty()) &&
            !showQuickGuideDialog &&
            !showExportReportDialog &&
            pendingStatementImport == null &&
            activeAuditReport == null
    ) {
        isSearchExpanded = false
        searchQuery = ""
        focusManager.clearFocus()
    }
    BackHandler(
        enabled = safeSelectedTab != 0 &&
            !isSearchExpanded &&
            searchQuery.isEmpty() &&
            !showQuickGuideDialog &&
            !showExportReportDialog &&
            pendingStatementImport == null &&
            activeAuditReport == null
    ) {
        selectedTab = 0
    }

    val searchCatalog = remember {
        listOf(
            SearchIndexEntry("Emergency Cash Reserve & Runway", "Home", 0, 0, listOf("emergency", "reserve", "runway", "liquid", "cash", "safety", "buffer")),
            SearchIndexEntry("Freedom Milestones & Targets", "Wealth > Goals & Roadmap", 2, 4, listOf("milestone", "coast", "lean", "standard", "barista", "fat", "overview", "target", "fire")),
            SearchIndexEntry("Recurring Bills & Subscriptions", "Budget > Overview", 1, 0, listOf("subscription", "recurring", "fixed debit", "auditor", "netflix", "spotify", "dismissed", "bills")),
            SearchIndexEntry("Learned Merchant Rules & Auto-Categorization", "More > App & Data", 3, 2, listOf("merchant", "rules", "learned", "category", "override", "keyword", "auto"), sectionKey = "merchant_rules"),
            SearchIndexEntry("Monthly Budget Summary", "Budget > Overview", 1, 0, listOf("budget", "surplus", "summary", "net flow", "savings rate", "family net", "cash flow")),
            SearchIndexEntry("Where Your Money Goes (Flow Chart)", "Budget > Overview", 1, 0, listOf("sankey", "waterfall", "cash flow", "cascade", "inflow", "allocation", "stream", "journey", "leakage")),
            SearchIndexEntry("ČNB FX Rates & 2026 Statutory Constants", "More > App & Data", 3, 2, listOf("sync", "cnb", "inflation", "tax law", "constants", "rates", "fx", "eur", "usd", "gbp", "pln", "chf", "huf", "zdp"), sectionKey = "sync"),
            SearchIndexEntry("Privacy, Biometric Lock & Screen Security", "More > App & Data", 3, 2, listOf("privacy", "biometric", "fingerprint", "pin", "lock", "flag_secure", "screenshot", "security"), sectionKey = "privacy"),
            SearchIndexEntry("Bank Accounts & Reconciliation", "More > App & Data", 3, 2, listOf("bank", "accounts", "iban", "reconciliation", "transfer", "parental", "gift", "family", "own account"), sectionKey = "bank_accounts"),
            SearchIndexEntry("Household Salaries, Raises & Bonuses", "Budget > Income", 1, 1, listOf("income", "salary", "bonus", "raise", "partner", "parental", "allowance", "benefit", "lecturing", "gift", "meal vouchers")),
            SearchIndexEntry("Living Expenses, Rent & Groceries", "Budget > Expenses", 1, 2, listOf("spending", "expenses", "rent", "groceries", "cafes", "therapy", "charity", "insurance", "fitness", "children", "lifestyle", "cost")),
            SearchIndexEntry("Monthly Records & Bank Ledger", "Budget > Ledger", 1, 3, listOf("ledger", "csv", "import", "export", "history", "records", "actuals", "tracking", "log", "entries")),
            SearchIndexEntry("Freedom Goal, SWR & 35-Year Forecast", "Wealth > Forecast", 2, 0, listOf("fire", "target", "swr", "withdrawal", "bridge", "state pension", "lifestyle", "projections", "trajectory", "forecast")),
            SearchIndexEntry("What-If Simulator & Scenario Presets", "Wealth > What-If", 2, 1, listOf("investments", "portu", "etf", "dca", "portfolio", "liquid", "sandbox", "what-if", "sliders", "presets")),
            SearchIndexEntry("Monte Carlo Stress Test & Scenarios", "Wealth > Stress", 2, 2, listOf("monte carlo", "fan chart", "p50", "p95", "p5", "simulation", "probability", "stress", "stagflation", "crash")),
            SearchIndexEntry("Czech Tax Relief, DPS, DIP & 3-Year Time Test", "Wealth > Pension", 2, 3, listOf("pension", "dps", "dip", "tax", "statutory", "youth", "tax shield", "deduction", "employer match", "early withdrawal", "time test", "capital gains", "3 year")),
            SearchIndexEntry("Goals, Roadmap & Annual Action Checklist", "Wealth > Goals & Roadmap", 2, 4, listOf("roadmap", "action", "checklist", "tasks", "phase", "timeline", "life goals", "goal", "down payment", "real estate", "house")),
            SearchIndexEntry("Base Planning Year, Inflation & Assumptions", "More > Portfolio & Tax", 3, 1, listOf("base year", "cpi", "inflation", "settings", "planning year", "macro", "fire assumptions"), sectionKey = "assumptions"),
            SearchIndexEntry("Salary, Raise & Bonus Parameters", "More > Household", 3, 0, listOf("salary settings", "vsalary", "esalary", "growth", "bonus", "gift", "vouchers", "income settings"), sectionKey = "income"),
            SearchIndexEntry("Expense Categories & Custom Budgets", "More > Household", 3, 0, listOf("expense settings", "custom category", "delete category", "add category", "rent setting", "groceries setting"), sectionKey = "expenses"),
            SearchIndexEntry("Family & Children Expenses & Tax Bonus", "More > Household", 3, 0, listOf("family settings", "children", "child bonus", "kindergarten"), sectionKey = "family"),
            SearchIndexEntry("Current Investment & Pension Balances", "More > Portfolio & Tax", 3, 1, listOf("starting balance", "portu balance", "dip balance", "dps balance", "emergency cash"), sectionKey = "balances"),
            SearchIndexEntry("DIP & DPS Contribution Parameters & Returns", "More > Portfolio & Tax", 3, 1, listOf("dip settings", "dps settings", "nominal return", "fee", "dps return", "investment settings", "swr", "balances"), sectionKey = "dca"),
            SearchIndexEntry("Czech Tax Optimization & Family Relief Settings", "More > Portfolio & Tax", 3, 1, listOf("tax rate", "tax settings", "relief", "deduction ceiling", "tax relief", "child tax credit", "czech tax"), sectionKey = "tax_shield"),
            SearchIndexEntry("Appearance & Dark Mode Theme", "More > App & Data", 3, 2, listOf("theme", "dark", "light", "appearance", "mode"), sectionKey = "appearance"),
            SearchIndexEntry("Preset Profiles, CSV Export & Backups", "More > App & Data", 3, 2, listOf("presets", "backup", "restore", "reset", "clear data", "export csv", "import csv", "data"), sectionKey = "backup")
        )
    }

    // Search filtering items
    val searchResults = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.lowercase().trim()
            searchCatalog.filter { entry ->
                entry.title.lowercase().contains(q) ||
                entry.category.lowercase().contains(q) ||
                entry.keywords.any { it.contains(q) }
            }.map {
                SearchResult(it.title, it.category, it.tabIndex, it.subTabIndex, it.sectionKey)
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (isSearchExpanded || searchQuery.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search metrics, tabs, settings", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            IconButton(onClick = {
                                isSearchExpanded = false
                                searchQuery = ""
                                focusManager.clearFocus()
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Close search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_input_field")
                    )
                }
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tabs[safeSelectedTab].title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isSyncing) {
                                Spacer(modifier = Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showQuickGuideDialog = true
                            },
                            modifier = Modifier.testTag("action_quick_guide")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = "How the app works",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isSearchExpanded = true
                            },
                            modifier = Modifier.testTag("action_toggle_search")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleDarkTheme()
                            },
                            modifier = Modifier.testTag("action_toggle_theme")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (isDarkTheme) "Switch to Light Mode" else "Switch to Dark Mode",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showExportReportDialog = true
                            },
                            modifier = Modifier.testTag("action_export_report")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Export Financial Report",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.testTag("bottom_navigation_bar")
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = safeSelectedTab == index,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedTab = index
                        },
                        icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            // Search Results Dropdown Overlay or Main Tab Content
            if (searchQuery.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .imePadding()
                        .animateContentSize(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    if (searchResults.isEmpty()) {
                        Text(
                            text = "No matching metrics or features found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.padding(8.dp)) {
                            items(
                                items = searchResults,
                                key = { "${it.tabIndex}_${it.subTabIndex}_${it.title}" }
                            ) { result ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            focusManager.clearFocus()
                                            selectedTab = result.tabIndex
                                            when (result.tabIndex) {
                                                1 -> targetCashFlowSubTab = result.subTabIndex
                                                2 -> targetProjectionsSubTab = result.subTabIndex
                                                3 -> {
                                                    targetSettingsSubTab = result.subTabIndex
                                                    if (result.sectionKey != null) {
                                                        targetSettingsSectionKey = result.sectionKey
                                                        targetSettingsSectionTrigger += 1
                                                    }
                                                }
                                            }
                                            isSearchExpanded = false
                                            searchQuery = ""
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            softWrap = true
                                        )
                                        Text(
                                            text = result.category,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Active Tab Content
                when (safeSelectedTab) {
                            0 -> OverviewTab(
                                state = state,
                                ledgerEntries = ledgerEntries,
                                onNavigateToIncome = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    targetCashFlowSubTab = 0
                                    selectedTab = 1
                                },
                                onNavigateToProjections = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    targetProjectionsSubTab = 0
                                    selectedTab = 2
                                },
                                onNavigateToSandbox = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    targetProjectionsSubTab = 1
                                    selectedTab = 2
                                },
                                onUpdateSettings = { newSettings ->
                                    viewModel.updateSettings(newSettings)
                                },
                                onReduceSettings = { transform ->
                                    viewModel.updateSettings(transform = transform)
                                },
                                onOpenSettings = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    targetSettingsSubTab = 0
                                    selectedTab = 3
                                },
                                onNavigateToNetWorth = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedTab = 2
                                    targetProjectionsSubTab = 0
                                },
                                onNavigateToEmergencyReserve = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedTab = 0
                                    scrollToEmergencyReserveTrigger += 1
                                },
                                onNavigateToSavingsRate = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedTab = 1
                                    targetCashFlowSubTab = 0
                                },
                                onOpenQuickGuide = { showQuickGuideDialog = true },
                                scrollToEmergencyReserveTrigger = scrollToEmergencyReserveTrigger,
                                scrollState = overviewScrollState
                            )
                            1 -> CashFlowTab(
                                state = state,
                                ledgerEntries = ledgerEntries,
                                onAddLedgerEntry = { ym, incV, incE, incU, expR, expL, notes, balPortu, balPension, balReserve ->
                                    viewModel.addLedgerEntry(ym, incV, incE, incU, expR, expL, notes, balPortu, balPension, balReserve)
                                },
                                onUpdateLedgerEntry = { entry ->
                                    viewModel.updateLedgerEntry(entry)
                                },
                                onAddLedgerEntryWithOptionalBalances = { ym, incV, incE, incU, expR, expL, notes, balPortu, balPension, balReserve ->
                                    viewModel.addLedgerEntryWithOptionalBalances(ym, incV, incE, incU, expR, expL, notes, balPortu, balPension, balReserve)
                                },
                                onUpdateLedgerEntryWithSync = { entry, balPortu, balPension, balReserve ->
                                    viewModel.updateLedgerEntryWithOptionalBalances(entry, balPortu, balPension, balReserve)
                                },
                                onDeleteLedgerEntry = { id -> viewModel.deleteLedgerEntry(id) },
                                onImportCsv = { uri -> viewModel.importCsvData(uri) },
                                onImportStatements = { uris -> viewModel.importStatements(uris) },
                                onShowAuditReport = { ym -> viewModel.loadAuditReportForMonth(ym) },
                                importedBankSourcesByMonth = importedBankSourcesByMonth,
                                lastImportTimestamp = lastImportTimestamp,
                                allImportedTransactions = allImportedTransactions,
                                onUpdateImportedTransactionCategory = { txId, cat, rem, applyAll ->
                                    viewModel.updateImportedTransactionCategory(txId, cat, rem, applyAll)
                                },
                                onToggleImportedTransactionExcluded = { txId ->
                                    viewModel.toggleImportedTransactionExcluded(txId)
                                },
                                dismissedSubscriptionMerchants = dismissedSubscriptionMerchants,
                                onDismissSubscription = { viewModel.dismissSubscription(it) },
                                onRestoreSubscription = { viewModel.restoreSubscription(it) },
                                onClearAllDismissedSubscriptions = { viewModel.clearAllDismissedSubscriptions() },
                                onDeleteImportedStatement = { ym, bank -> viewModel.deleteImportedStatement(ym, bank) },
                                initialSubTab = targetCashFlowSubTab,
                                onSubTabSelected = { targetCashFlowSubTab = it },
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onReduceSettings = { transform -> viewModel.updateSettings(transform = transform) }
                            )
                            2 -> ProjectionsTab(
                                state = state,
                                ledgerEntries = ledgerEntries,
                                actionStates = actionStates,
                                onApplySettings = { viewModel.updateSettings(it) },
                    onReduceSettings = { transform -> viewModel.updateSettings(transform = transform) },
                                onToggleAction = { year, id, isDone ->
                                    viewModel.toggleAction(year, id, isDone)
                                },
                                initialSubTab = targetProjectionsSubTab,
                                onSubTabSelected = { targetProjectionsSubTab = it }
                            )
                            3 -> SettingsTab(
                                state = state,
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onResetDefaults = { viewModel.resetSettingsToDefault() },
                                onClearAllData = { viewModel.clearAllUserData() },
                                liveRegulatoryData = liveRegulatoryData,
                                isSyncing = isSyncing,
                                onSyncLiveCzechData = { viewModel.syncLiveCzechData() },
                                lastSyncTimestamp = lastCzechSyncTimestamp,
                                initialSubTab = targetSettingsSubTab,
                                onSubTabSelected = { targetSettingsSubTab = it },
                                ledgerEntries = ledgerEntries,
                                actionStates = actionStates,
                                importedTransactions = allImportedTransactions,
                                onRestoreFullBackup = { viewModel.restoreFullBackup(it) },
                                themeMode = themeMode,
                                onChangeThemeMode = onChangeThemeMode,
                                focusedSectionKey = targetSettingsSectionKey,
                                focusedSectionTrigger = targetSettingsSectionTrigger,
                                onConsumeFocusedSection = { targetSettingsSectionKey = null },
                                onReduceSettings = { transform -> viewModel.updateSettings(transform = transform) },
                                biometricLockEnabled = biometricLockEnabled,
                                flagSecureEnabled = flagSecureEnabled,
                                onToggleBiometricLock = { viewModel.setBiometricLockEnabled(it) },
                                onToggleFlagSecure = { viewModel.setFlagSecureEnabled(it) },
                                onPrepareExportRequest = { full, enc, phrase ->
                                    viewModel.setPendingExportRequest(full, enc, phrase)
                                },
                                onConsumeExportRequest = { viewModel.consumePendingExportRequest() }
                            )
                        }
                    }

            if (showQuickGuideDialog) {
                QuickGuideDialog(
                    onDismiss = { showQuickGuideDialog = false },
                    onGoToSetup = {
                        showQuickGuideDialog = false
                        targetSettingsSubTab = 0
                        targetSettingsSectionKey = "income"
                        targetSettingsSectionTrigger += 1
                        selectedTab = 3
                    }
                )
            }

            // Financial Summary Export Report Dialog Modal
            if (showExportReportDialog) {
                ExportReportDialog(
                    state = state,
                    onDismiss = { showExportReportDialog = false }
                )
            }

            pendingStatementImport?.let { summary ->
                StatementImportReviewDialog(
                    summary = summary,
                    onConfirm = { viewModel.confirmStatementImport(it) },
                    onDismiss = { viewModel.dismissStatementImport() },
                    onShowNettingAudit = { viewModel.loadAuditReportForMonth(summary.yearMonth) },
                    onUpdateTransactionCategory = { idx, cat, remember ->
                        viewModel.updatePendingTransactionCategory(idx, cat, remember)
                    },
                    onUpdateTransactionCategoryBulk = { idx, cat, remember, applyAll ->
                        viewModel.updatePendingTransactionCategory(idx, cat, remember, applyAll)
                    },
                    onToggleTransactionExcluded = { idx ->
                        viewModel.togglePendingTransactionExcluded(idx)
                    },
                    onBulkExcludeMerchant = { query ->
                        viewModel.bulkExcludeMerchant(query)
                    }
                )
            }

            activeAuditReport?.let { report ->
                CrossAccountNettingAuditDialog(
                    auditReport = report,
                    onDismiss = { viewModel.clearAuditReport() }
                )
            }
        }
    }
}

