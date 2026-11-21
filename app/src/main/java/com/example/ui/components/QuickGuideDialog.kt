package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.BadRed
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.OnBrandTeal

private enum class GuideCategory(val label: String) {
    ALL("All"),
    APP_TOUR("App Tour"),
    ENGINE("Financial Engine"),
    TAX_RULES("Czech Tax Perks"),
    FIRE_MILESTONES("FIRE Milestones"),
    PRIVACY("Privacy & Banking")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickGuideDialog(
    onDismiss: () -> Unit,
    onGoToSetup: (() -> Unit)? = null
) {
    var selectedCategory by rememberSaveable { mutableStateOf(GuideCategory.ALL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = 560.dp)
            .testTag("quick_guide_dialog"),
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(BrandTeal.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Welcome! How the app works",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Personal finance, Czech tax perks & early retirement manual",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Category Filter Chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GuideCategory.entries.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label, fontSize = 11.5.sp) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("guide_filter_${cat.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandTeal.copy(alpha = 0.16f),
                                selectedLabelColor = BrandTeal
                            )
                        )
                    }
                }

                // 1. App Tour Section
                if (selectedCategory == GuideCategory.ALL || selectedCategory == GuideCategory.APP_TOUR) {
                    GuideSectionHeader(title = "App Architecture & Navigation", accent = BrandTeal)

                    GuideStepRow(
                        icon = Icons.Default.Home,
                        accent = BrandTeal,
                        title = "1. Home (Daily Snapshot)",
                        subtitle = "Net worth, emergency runway, take-home inflows & core targets",
                        description = "Your primary dashboard. Track your total net worth in real time, see your liquid emergency buffer measured in months of living expenses, and monitor savings velocity."
                    )

                    GuideStepRow(
                        icon = Icons.Default.AccountBalanceWallet,
                        accent = GoodGreen,
                        title = "2. Budget (Cash Flow & Ledger)",
                        subtitle = "Interactive Sankey flow, upcoming bills calendar, subscriptions & bank statements",
                        description = "Switch the Sankey diagram between Overview and By Category and tap any stream to inspect its Czech tax or budget rule. Monitor upcoming fixed bills, recurring subscription creep, merchant spend drift, and parse bank PDFs/CSVs offline."
                    )

                    GuideStepRow(
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        accent = BrandBlue,
                        title = "3. Wealth (Future & Tax Optimization)",
                        subtitle = "35-year compounding forecast, What-If sandbox, lot tracker & Czech pension shields",
                        description = "Simulate long-term independence. Models the Pre-60 Liquidity Bridge vs Barista FIRE before state pension starts, runs 400-iteration Monte Carlo crash stress tests (up to 2 000), tracks 3-year time-test lots, and optimizes DIP/DPS relief."
                    )

                    GuideStepRow(
                        icon = Icons.Default.Settings,
                        accent = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "4. More (Profile & Assumptions)",
                        subtitle = "Salaries, spending categories, child lifecycle & portable backups",
                        description = "Customize all parameters. Configure household salaries, Czech gross-to-net tax rules, custom expense categories, child development costs, and export portable JSON/CSV backups."
                    )
                }

                // 2. Financial Engine Section
                if (selectedCategory == GuideCategory.ALL || selectedCategory == GuideCategory.ENGINE) {
                    GuideSectionHeader(title = "Core Financial Engine & Formulas", accent = BrandGold)

                    GuideRuleCard(
                        title = "Manual Edits vs Bank Imports & Ledger",
                        formula = "Live Settings Override Manual Defaults · Imported Statements Stay Read-Only",
                        explanation = "Any edit you make in Budget or More immediately updates household inflows, savings, and FIRE targets—your numbers are never capped or frozen. Months created by importing bank statements remain read-only historical actuals so past imports never overwrite manual edits.",
                        accent = BrandTeal
                    )

                    GuideRuleCard(
                        title = "Total Net Worth",
                        formula = "Liquid Brokerage + DIP + DPS + Emergency Cash − Debts",
                        explanation = "Measures true household solvency. Liquid portfolio comprises taxable ETF brokerages (e.g. Portu), locked pension assets (DIP & DPS), and high-yield cash reserves.",
                        accent = BrandGold
                    )

                    GuideRuleCard(
                        title = "Emergency Runway & Buffer",
                        formula = "Runway = Liquid Cash ÷ Essential Monthly Burn",
                        explanation = "Recommended 3–6 months. The app calculates an automatic 24-month catch-up allocation: (Target − Current) ÷ 24 months to smoothly fund reserves without starving long-term compounding.",
                        accent = GoodGreen
                    )

                    GuideRuleCard(
                        title = "Safe Withdrawal Rate (SWR) & Pre-60 Bridge",
                        formula = "Base Target = (Annual Living Burn − State Pension) ÷ SWR + Bridge Deficit",
                        explanation = "Default 4.0% (customizable 2.5%–5.0%). Two-Bucket Bridge separates flexible taxable ETFs (spending flexibly before age 60) from locked DIP/DPS pension accounts, while Barista FIRE models part-time bridge income until the Czech state pension arrives.",
                        accent = BrandBlue
                    )
                }

                // 3. Czech Tax Perks Section
                if (selectedCategory == GuideCategory.ALL || selectedCategory == GuideCategory.TAX_RULES) {
                    GuideSectionHeader(title = "Czech Statutory Tax Perks (§ ZDP)", accent = GoodGreen)

                    GuideRuleCard(
                        title = "Parental & Direct-Line Family Gifts (§ 4a & § 10(3)(c) ZDP)",
                        formula = "100% Tax-Exempt · Excluded from 68\u00A0000\u00A0Kč Partner Limit",
                        explanation = "Gifts from parents and direct-line relatives are completely tax-free under Czech law. The app disregards all family gifts for income tax calculations, excludes them from the 68\u00A0000\u00A0Kč annual partner own-income test, and never nets them as internal transfers during statement reconciliation.",
                        accent = GoodGreen
                    )

                    GuideRuleCard(
                        title = "3-Year Securities Time Test & Lot Tracker (§ 4(1)(u) & (w) ZDP)",
                        formula = "Holding Period > 3 Years → 0% Capital Gains Tax (FIFO)",
                        explanation = "Track individual ETF/stock purchase lots to watch countdowns to the 3-year time test. Publicly traded securities held strictly over 3 years are 100% tax-free on sale, alongside the 100\u00A0000\u00A0Kč annual gross proceeds exemption, letting you harvest FIFO lots with minimal tax drag.",
                        accent = BrandTeal
                    )

                    GuideRuleCard(
                        title = "DIP Tax Shield (§ 15a ZDP)",
                        formula = "Up to 48\u00A0000\u00A0Kč/year Deduction = 7\u00A0200\u00A0Kč/year Net Refund",
                        explanation = "Contributions to the Long-Term Investment Product reduce your taxable base by up to 48\u00A0000\u00A0Kč per earner (saving 7\u00A0200\u00A0Kč at 15%, or 11\u00A0040\u00A0Kč at 23%). Subject to the statutory 120-month holding and age 60 withdrawal rule.",
                        accent = BrandTeal
                    )

                    GuideRuleCard(
                        title = "DPS Pension Matching (Act No. 427/2011 Coll.)",
                        formula = "Deposit 1\u00A0700\u00A0Kč/month → 340\u00A0Kč/month State Bonus (20%)",
                        explanation = "State-subsidized supplementary pension. Direct matching bonus is 20% on monthly deposits from 500\u00A0Kč to 1\u00A0700\u00A0Kč (up to 340\u00A0Kč/month; ends once drawing old-age pension). Deposits above 1\u00A0700\u00A0Kč/month count toward the shared 48\u00A0000\u00A0Kč annual tax deduction.",
                        accent = BrandBlue
                    )

                    GuideRuleCard(
                        title = "Tax-Exempt Allowances & Family Credits",
                        formula = "Meal Vouchers § 6(9)(b) + Basic Credit 30\u00A0840\u00A0Kč + Spouse 24\u00A0840\u00A0Kč",
                        explanation = "Monetary meal vouchers (up to 129,50\u00A0Kč/day in 2026) are 100% tax-free and exempt from 7,1% social and 4,5% health insurance. Spouse tax credit applies when caring for a child under 3 with spouse own gross income under 68\u00A0000\u00A0Kč/year.",
                        accent = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 4. FIRE Milestones Section
                if (selectedCategory == GuideCategory.ALL || selectedCategory == GuideCategory.FIRE_MILESTONES) {
                    GuideSectionHeader(title = "Retirement & Independence Milestones", accent = BrandBlue)

                    GuideMilestoneRow(
                        icon = Icons.Default.Savings,
                        title = "Coast FIRE",
                        badge = "MILESTONE 1",
                        accent = GoodGreen,
                        description = "Your current invested portfolio has grown large enough that compound interest alone will reach your full retirement target by age 65 without saving another crown."
                    )

                    GuideMilestoneRow(
                        icon = Icons.Default.Shield,
                        title = "Lean FIRE",
                        badge = "MILESTONE 2",
                        accent = BrandTeal,
                        description = "Invested nest egg covers 100% of non-negotiable living essentials (housing, groceries, utilities, basic healthcare) via your safe withdrawal rate."
                    )

                    GuideMilestoneRow(
                        icon = Icons.Default.CheckCircle,
                        title = "Standard FIRE",
                        badge = "MILESTONE 3",
                        accent = BrandBlue,
                        description = "Full financial independence sustaining 100% of your current complete household lifestyle (including dining out, travel, hobbies, and gifts) in perpetuity."
                    )

                    GuideMilestoneRow(
                        icon = Icons.Default.Coffee,
                        title = "Barista FIRE",
                        badge = "ALTERNATIVE",
                        accent = BrandGold,
                        description = "Transitioning to enjoyable part-time or seasonal work covering partial living expenses and statutory minimum Czech health insurance, while investments cover the balance."
                    )

                    GuideMilestoneRow(
                        icon = Icons.Default.Flight,
                        title = "Fat FIRE",
                        badge = "ABUNDANCE",
                        accent = BadRed,
                        description = "Standard FIRE plus an additional 20%–50% discretionary capital buffer for luxury travel, generational wealth transfer, or healthcare contingencies."
                    )
                }

                // 5. Privacy & Banking Section
                if (selectedCategory == GuideCategory.ALL || selectedCategory == GuideCategory.PRIVACY) {
                    GuideSectionHeader(title = "Privacy Guarantee & Statement Import", accent = BrandTeal)

                    GuideRuleCard(
                        title = "Local-First & Privacy-Respecting",
                        formula = "Zero Cloud Sync · Zero Analytics · Zero Telemetry",
                        explanation = "All calculations, budgets, and imported transactions stay strictly on your Android device in a private sandboxed local SQLite Room database (cloud backup disabled). No account is required. Optional network requests occur only when you tap Live ČNB FX Sync.",
                        accent = GoodGreen
                    )

                    GuideRuleCard(
                        title = "Automated Bank Statement Parser",
                        formula = "ČSOB · Moneta · Česká spořitelna · Air Bank · Komerční banka · Raiffeisenbank · Fio banka · mBank · UniCredit · Revolut · Creditas",
                        explanation = "Drop official monthly PDF or CSV account statements into Budget > Ledger. The engine automatically parses all supported banks offline, detects subscriptions and outliers, and categorizes transactions using built-in Czech merchant dictionaries.",
                        accent = BrandTeal
                    )

                    GuideRuleCard(
                        title = "Cross-Account Netting Audit",
                        formula = "Self-Transfers Netted · Parent/Family Gifts Preserved",
                        explanation = "Moving money between your own checking, savings, and brokerage accounts is automatically paired and netted out so internal transfers never inflate spending or income, while incoming transfers from parents/relatives remain preserved as tax-exempt family gifts.",
                        accent = BrandBlue
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = BrandGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Tip: Tap or hold any card across the app to inspect its exact mathematical formula and statutory Czech legal reference.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (onGoToSetup != null) {
                Button(
                    onClick = {
                        onDismiss()
                        onGoToSetup()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        contentColor = OnBrandTeal
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("guide_btn_setup")
                ) {
                    Text("Set Up My Numbers", fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("guide_btn_got_it")
                ) {
                    Text("Got It", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = if (onGoToSetup != null) {
            {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("guide_btn_close")
                ) {
                    Text("Close")
                }
            }
        } else null
    )
}

@Composable
private fun GuideSectionHeader(title: String, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
                color = accent
            )
        )
    }
}

@Composable
private fun GuideStepRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = accent
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 15.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun GuideRuleCard(
    title: String,
    formula: String,
    explanation: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.5.sp
                )
            )
            Spacer(modifier = Modifier.height(3.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formula,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = BodyFontFamily,
                        fontSize = 10.5.sp,
                        color = accent
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 15.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuideMilestoneRow(
    icon: ImageVector,
    title: String,
    badge: String,
    accent: Color,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = accent.copy(alpha = 0.16f),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.5.sp,
                                color = accent
                            ),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 15.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
