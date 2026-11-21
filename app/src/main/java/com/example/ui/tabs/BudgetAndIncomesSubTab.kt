package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FullCalculationState
import com.example.ui.components.MetricInfo
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.OnBrandTeal
import com.example.util.BankTransactionType

@Composable
internal fun BudgetAndIncomesSubTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity>,
    allImportedTransactions: List<ImportedBankTransactionEntity> = emptyList(),
    dismissedSubscriptionMerchants: Set<String> = emptySet(),
    onDismissSubscription: ((String) -> Unit)? = null,
    onRestoreSubscription: ((String) -> Unit)? = null,
    onClearAllDismissedSubscriptions: (() -> Unit)? = null,
    onUpdateImportedTransactionCategory: ((Long, BankTransactionType, Boolean, Boolean) -> Unit)? = null,
    onShowInfo: (MetricInfo) -> Unit = {},
    onUpdateSettings: (SettingsEntity) -> Unit = {},
    externalSelectedSection: Int? = null,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    var internalSelectedSection by rememberSaveable { mutableIntStateOf(0) } // 0 = Overview, 1 = Income, 2 = Expenses
    val selectedSection = (externalSelectedSection ?: internalSelectedSection).coerceIn(0, 2)
    val sections = listOf("Overview", "Income", "Expenses")
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxSize()) {
        if (externalSelectedSection == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sections.forEachIndexed { index, name ->
                    val isSelected = selectedSection == index
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            internalSelectedSection = index
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (isSelected) OnBrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("budget_section_$index")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                softWrap = true,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        when (selectedSection) {
            0 -> BudgetOverviewCard(
                state = state,
                ledgerEntries = ledgerEntries,
                allImportedTransactions = allImportedTransactions,
                dismissedSubscriptionMerchants = dismissedSubscriptionMerchants,
                onDismissSubscription = onDismissSubscription,
                onRestoreSubscription = onRestoreSubscription,
                onClearAllDismissedSubscriptions = onClearAllDismissedSubscriptions,
                onUpdateImportedTransactionCategory = onUpdateImportedTransactionCategory,
                onShowInfo = onShowInfo,
                onUpdateSettings = onUpdateSettings,
                onReduceSettings = onReduceSettings
            )
            1 -> IncomeSourcesCard(
                state = state,
                ledgerEntries = ledgerEntries,
                onShowInfo = onShowInfo,
                onUpdateSettings = onUpdateSettings,
                onReduceSettings = onReduceSettings,
                allImportedTransactions = allImportedTransactions
            )
            2 -> ExpenseCategoriesCard(
                state = state,
                ledgerEntries = ledgerEntries,
                onShowInfo = onShowInfo,
                onUpdateSettings = onUpdateSettings,
                onReduceSettings = onReduceSettings,
                allImportedTransactions = allImportedTransactions
            )
        }
    }
}
