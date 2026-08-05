package com.example.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    val tabIndex: Int
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
        NavTabItem("Plan", Icons.Default.Checklist, "nav_plan"),
        NavTabItem("Settings", Icons.Default.Settings, "nav_settings")
    )

    // Search filtering items
    val searchResults = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.lowercase()
            val list = mutableListOf<SearchResult>()
            if ("income".contains(q) || "salary".contains(q) || "vaclav".contains(q)) {
                list.add(SearchResult("Vaclav Salary & Income", "Cash Flow > Income", 1))
            }
            if ("rent".contains(q) || "groceries".contains(q) || "spending".contains(q) || "cost".contains(q)) {
                list.add(SearchResult("Living Expenses & Rent", "Cash Flow > Spending", 1))
            }
            if ("fire".contains(q) || "target".contains(q) || "swr".contains(q)) {
                list.add(SearchResult("FIRE Target & SWR Model", "Projections > FIRE", 2))
            }
            if ("monte carlo".contains(q) || "p50".contains(q) || "simulation".contains(q)) {
                list.add(SearchResult("Monte Carlo 1,000-Run Fan Chart", "Projections > Monte Carlo", 2))
            }
            if ("pension".contains(q) || "dps".contains(q) || "dip".contains(q) || "reform".contains(q)) {
                list.add(SearchResult("Lepší Penzijko DPS / DIP Reform", "Plan > Pension", 3))
            }
            if ("action".contains(q) || "tax return".contains(q) || "checklist".contains(q)) {
                list.add(SearchResult("Action Items Checklist", "Plan > Actions", 3))
            }
            if ("settings".contains(q) || "portfolio".contains(q) || "cpi".contains(q)) {
                list.add(SearchResult("Portfolio & CPI Settings", "Settings", 4))
            }
            list
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
                        onOpenExportReportDialog = { showExportReportDialog = true }
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
                                                selectedTab = result.tabIndex
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
                    Crossfade(
                        targetState = selectedTab,
                        animationSpec = tween(durationMillis = 150),
                        modifier = Modifier.weight(1f)
                    ) { tabIndex ->
                        when (tabIndex) {
                            0 -> OverviewTab(state = state)
                            1 -> CashFlowTab(
                                state = state,
                                ledgerEntries = ledgerEntries,
                                onAddLedgerEntry = { ym, incV, incE, expR, expG, expO, notes ->
                                    viewModel.addLedgerEntry(ym, incV, incE, expR, expG, expO, notes)
                                },
                                onUpdateLedgerEntry = { entry ->
                                    viewModel.updateLedgerEntry(entry)
                                },
                                onDeleteLedgerEntry = { id -> viewModel.deleteLedgerEntry(id) },
                                onImportCsv = { uri -> viewModel.importCsvData(uri) }
                            )
                            2 -> ProjectionsTab(
                                state = state,
                                onSensitivityChange = { r, c, s -> viewModel.setSensitivityOverrides(r, c, s) },
                                onApplySettings = { viewModel.updateSettings(it) }
                            )
                            3 -> PlanTab(
                                state = state,
                                actionStates = actionStates,
                                onToggleAction = { year, id, isDone ->
                                    viewModel.toggleAction(year, id, isDone)
                                },
                                onUpdateSettings = { viewModel.updateSettings(it) }
                            )
                            4 -> SettingsTab(
                                state = state,
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onResetDefaults = { viewModel.resetSettingsToDefault() },
                                onClearAllData = { viewModel.clearAllUserData() }
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

