package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.ui.components.ExportReportDialog
import com.example.ui.components.HeroHeader
import com.example.ui.components.ReformDialog
import com.example.ui.tabs.CashFlowTab
import com.example.ui.tabs.OverviewTab
import com.example.ui.tabs.PlanTab
import com.example.ui.tabs.ProjectionsTab
import com.example.ui.tabs.SettingsTab
import kotlinx.coroutines.flow.collectLatest

data class NavTabItem(
    val title: String,
    val icon: ImageVector,
    val tag: String
)

data class SearchResult(
    val title: String,
    val category: String,
    val tabIndex: Int,
    val subTabIndex: Int = 0
)

data class SearchIndexEntry(
    val title: String,
    val category: String,
    val tabIndex: Int,
    val subTabIndex: Int,
    val keywords: List<String>
)

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.calculationState.collectAsState()
    val ledgerEntries by viewModel.ledgerEntries.collectAsState()
    val actionStates by viewModel.actionStates.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var targetCashFlowSubTab by remember { mutableIntStateOf(0) }
    var targetProjectionsSubTab by remember { mutableIntStateOf(0) }
    var targetPlanSubTab by remember { mutableIntStateOf(0) }
    var targetSettingsSubTab by remember { mutableIntStateOf(0) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var showReformDialog by remember { mutableStateOf(false) }
    var showExportReportDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collectLatest { event ->
            when (event) {
                is UiMessage.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    val tabs = listOf(
        NavTabItem("Overview", Icons.Default.Dashboard, "nav_overview"),
        NavTabItem("Cash Flow", Icons.Default.Payments, "nav_cashflow"),
        NavTabItem("Projections", Icons.Default.Assessment, "nav_projections"),
        NavTabItem("Strategy", Icons.Default.Checklist, "nav_plan")
    )

    val searchCatalog = remember {
        listOf(
            SearchIndexEntry("Emergency Cash Reserve & Runway", "Overview", 0, 0, listOf("emergency", "reserve", "runway", "liquid", "cash", "safety", "buffer")),
            SearchIndexEntry("FIRE Milestones & Freedom Score", "Overview", 0, 0, listOf("freedom", "score", "milestone", "coast", "lean", "standard", "barista", "overview")),
            SearchIndexEntry("Monthly Budget & Surplus Summary", "Cash Flow > Budget & Incomes", 1, 0, listOf("budget", "surplus", "summary", "net flow", "savings rate", "family net", "cash flow")),
            SearchIndexEntry("Vaclav & Eleonora Salaries, Raises & Bonuses", "Cash Flow > Budget & Incomes", 1, 0, listOf("income", "salary", "bonus", "raise", "vaclav", "eleonora", "parental", "allowance", "benefit", "lecturing", "gift", "meal vouchers")),
            SearchIndexEntry("Living Expenses, Rent & Groceries", "Cash Flow > Budget & Incomes", 1, 0, listOf("spending", "expenses", "rent", "groceries", "cafes", "therapy", "charity", "insurance", "fitness", "children", "lifestyle", "cost")),
            SearchIndexEntry("Historical Ledger Records & CSV Import", "Cash Flow > Monthly Records & Ledger", 1, 1, listOf("ledger", "csv", "import", "export", "history", "records", "actuals", "tracking", "log", "entries")),
            SearchIndexEntry("FIRE Target, SWR & 35-Year Trajectory", "Projections > 35y Trajectory", 2, 0, listOf("fire", "target", "swr", "withdrawal", "bridge", "state pension", "lifestyle", "projections", "trajectory")),
            SearchIndexEntry("What-If Live Sandbox & Investment Balances", "Projections > What-If Sandbox", 2, 1, listOf("investments", "portu", "etf", "dca", "portfolio", "liquid", "sandbox", "what-if", "sliders")),
            SearchIndexEntry("Monte Carlo Multi-Run Simulation & Macro Stress Scenarios", "Projections > Monte Carlo & Stress", 2, 2, listOf("monte carlo", "fan chart", "p50", "p95", "p5", "simulation", "probability", "stress", "stagflation", "crash")),
            SearchIndexEntry("Lepší Penzijko Reform, DPS & DIP Tax Shield", "Strategy & Reform > Czech Tax & Pension", 3, 0, listOf("pension", "dps", "dip", "reform", "lepsí penzijko", "youth", "40%", "tax shield", "deduction", "employer match", "early withdrawal")),
            SearchIndexEntry("FIRE Roadmap, Annual Action Checklist & Life Goals", "Strategy & Reform > Roadmap & Goals", 3, 1, listOf("roadmap", "action", "checklist", "tasks", "phase", "timeline", "life goals", "goal", "down payment", "real estate", "house")),
            SearchIndexEntry("Base Year, Inflation & Horizon Settings", "Settings > General", 4, 0, listOf("base year", "cpi", "inflation", "birth year", "settings", "general", "macro")),
            SearchIndexEntry("Salary, Raise & Bonus Parameters", "Settings > Income", 4, 1, listOf("salary settings", "vsalary", "esalary", "growth", "bonus", "gift", "vouchers", "income settings")),
            SearchIndexEntry("Expense Categories & Custom Budgets", "Settings > Expenses", 4, 2, listOf("expense settings", "custom category", "delete category", "add category", "rent setting", "groceries setting")),
            SearchIndexEntry("DIP & DPS Contribution Parameters & Returns", "Settings > Investments", 4, 3, listOf("dip settings", "dps settings", "nominal return", "fee", "dps return", "investment settings")),
            SearchIndexEntry("Czech Tax Optimization & Family Relief Settings", "Settings > Taxes & Family", 4, 4, listOf("tax rate", "tax settings", "relief", "deduction ceiling", "spouse tax relief", "child tax credit", "czech tax")),
            SearchIndexEntry("Preset Profiles, CSV Export & Data Management", "Settings > Data", 4, 5, listOf("presets", "backup", "restore", "reset", "clear data", "export csv", "import csv", "data"))
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
                SearchResult(it.title, it.category, it.tabIndex, it.subTabIndex)
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("bottom_navigation_bar")
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                        label = { Text(text = tab.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Search Bar & Header
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    HeroHeader(
                        state = state,
                        isDarkTheme = isDarkTheme,
                        onToggleDarkTheme = onToggleDarkTheme,
                        onOpenReformDialog = { showReformDialog = true },
                        onOpenExportReportDialog = { showExportReportDialog = true },
                        onOpenSettings = { showSettingsDialog = true }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Streamlined Compact Search Input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search metrics, tabs, settings", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_input_field")
                    )
                }

                // Search Results Dropdown Overlay or Main Tab Content
                if (searchQuery.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
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
                                items(searchResults) { result ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (result.tabIndex == 4) {
                                                    targetSettingsSubTab = result.subTabIndex
                                                    showSettingsDialog = true
                                                } else {
                                                    selectedTab = result.tabIndex
                                                    when (result.tabIndex) {
                                                        1 -> targetCashFlowSubTab = result.subTabIndex
                                                        2 -> targetProjectionsSubTab = result.subTabIndex
                                                        3 -> targetPlanSubTab = result.subTabIndex
                                                    }
                                                }
                                                searchQuery = ""
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = result.title, fontWeight = FontWeight.Bold)
                                            Text(text = result.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Active Tab Content
                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            0 -> OverviewTab(
                                state = state,
                                actionStates = actionStates,
                                onToggleAction = { year, id, isDone ->
                                    viewModel.toggleAction(year, id, isDone)
                                },
                                onNavigateToPlan = { selectedTab = 3 }
                            )
                            1 -> CashFlowTab(
                                state = state,
                                ledgerEntries = ledgerEntries,
                                onAddLedgerEntry = { ym, incV, incE, incU, expR, expL, notes ->
                                    viewModel.addLedgerEntry(ym, incV, incE, incU, expR, expL, notes)
                                },
                                onUpdateLedgerEntry = { entry ->
                                    viewModel.updateLedgerEntry(entry)
                                },
                                onDeleteLedgerEntry = { id -> viewModel.deleteLedgerEntry(id) },
                                onImportCsv = { uri -> viewModel.importCsvData(uri) },
                                initialSubTab = targetCashFlowSubTab
                            )
                            2 -> ProjectionsTab(
                                state = state,
                                onSensitivityChange = { r, c, s -> viewModel.setSensitivityOverrides(r, c, s) },
                                onApplySettings = { viewModel.updateSettings(it) },
                                initialSubTab = targetProjectionsSubTab
                            )
                            3 -> PlanTab(
                                state = state,
                                actionStates = actionStates,
                                onToggleAction = { year, id, isDone ->
                                    viewModel.toggleAction(year, id, isDone)
                                },
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                initialSubTab = targetPlanSubTab
                            )
                        }
                    }
                }
            }

            // Settings Modal Dialog / Fullsheet
            if (showSettingsDialog) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showSettingsDialog = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Settings & Parameters",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                androidx.compose.material3.TextButton(
                                    onClick = { showSettingsDialog = false },
                                    modifier = Modifier.testTag("close_settings_button")
                                ) {
                                    Text("Done", fontWeight = FontWeight.Bold)
                                }
                            }
                            HorizontalDivider()

                            SettingsTab(
                                state = state,
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onResetDefaults = { viewModel.resetSettingsToDefault() },
                                onClearAllData = { viewModel.clearAllUserData() },
                                initialSubTab = targetSettingsSubTab,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // What's New / Reform Dialog Modal
            if (showReformDialog) {
                ReformDialog(onDismiss = { showReformDialog = false })
            }

            // Financial Summary Export Report Dialog Modal
            if (showExportReportDialog) {
                ExportReportDialog(
                    state = state,
                    onDismiss = { showExportReportDialog = false }
                )
            }
        }
    }
}

