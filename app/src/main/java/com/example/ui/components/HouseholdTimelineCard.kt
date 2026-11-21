package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.domain.HouseholdMember
import com.example.domain.HouseholdProfile
import com.example.domain.HouseholdRole
import com.example.domain.LifeTimelineEvent
import com.example.domain.TimelineEventType
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK

@Composable
fun HouseholdTimelineCard(
    settings: SettingsEntity,
    state: FullCalculationState? = null,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    val profile = HouseholdProfile.fromSettings(settings)
    val totalMembers = 1 + (if (!profile.isSingleHousehold) 1 else 0) + profile.children.size

    val income = state?.currentIncome ?: FinancialEngine.householdIncome(settings.baseYear, settings)
    val totalHouseholdNet = income.totalMonthly.finiteOrZero()

    val primaryNetTotal = (income.vaclavNet + income.vouchers + income.vaclavGifts + income.vaclavOther).finiteOrZero()

    val isSpouseActive = !settings.isSingleHousehold
    val spouseNetTotal = if (isSpouseActive) {
        (income.eleonoraSalary + income.benefit + income.lecturing + income.eleonoraGifts + income.eleonoraOther).finiteOrZero()
    } else 0.0

    var showEditPrimaryDialog by remember { mutableStateOf(false) }
    var showEditSpouseDialog by remember { mutableStateOf(false) }

    val canEdit = onUpdateSettings != null || onReduceSettings != null
    if (showEditPrimaryDialog && canEdit) {
        EditPrimaryEarnerDialog(
            settings = settings,
            onDismiss = { showEditPrimaryDialog = false },
            onSave = { updated -> onUpdateSettings?.invoke(updated) },
            onSaveTransform = onReduceSettings
        )
    }

    if (showEditSpouseDialog && canEdit) {
        EditSpouseEarnerDialog(
            settings = settings,
            onDismiss = { showEditSpouseDialog = false },
            onSave = { updated -> onUpdateSettings?.invoke(updated) },
            onSaveTransform = onReduceSettings
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("household_timeline_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardHeaderPill(
                title = "Household Members & Timeline",
                subtitle = "Family roles, lifecycle events, and statutory milestone roadmaps",
                badgeText = "$totalMembers MEMBERS",
                badgeColor = BrandTeal,
                icon = Icons.Default.Event,
                accentColor = BrandTeal
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("household_members_header_row"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FAMILY MEMBERS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    letterSpacing = 0.8.sp
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BrandTeal.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.25f)),
                    modifier = Modifier.testTag("household_total_income_banner")
                ) {
                    Text(
                        text = "Household Net Total: ${fmtCZK(totalHouseholdNet)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BodyFontFamily,
                        color = BrandTeal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val primarySubtitle = buildString {
                append("Salary: ")
                append(fmtCZK(income.vaclavNet))
                if (income.vouchers > 0.0) {
                    append(" · Vouchers: ")
                    append(fmtCZK(income.vouchers))
                }
                if (income.vaclavGifts > 0.0) {
                    append(" · Gift: ")
                    append(fmtCZK(income.vaclavGifts))
                }
                if (income.vaclavOther > 0.0) {
                    append(" · Other: ")
                    append(fmtCZK(income.vaclavOther))
                }
            }
            MemberSummaryRow(
                member = profile.primaryEarner,
                displayName = settings.primaryName.ifBlank { "Primary Earner" },
                baseYear = settings.baseYear,
                monthlyNetTotal = primaryNetTotal,
                breakdownSubtitle = primarySubtitle,
                onEditClick = if (canEdit) { { showEditPrimaryDialog = true } } else null
            )

            if (profile.spouseEarner != null) {
                Spacer(modifier = Modifier.height(6.dp))
                val spouseSubtitle = buildString {
                    if (settings.baseYear < settings.eReturnYear) {
                        append("Parental benefit: ")
                        append(fmtCZK(income.benefit))
                        if (income.lecturing > 0.0) {
                            append(" · Lecturing: ")
                            append(fmtCZK(income.lecturing))
                        }
                        if (income.eleonoraOther > 0.0) {
                            append(" · Other: ")
                            append(fmtCZK(income.eleonoraOther))
                        }
                    } else {
                        append("Salary: ")
                        append(fmtCZK(income.eleonoraSalary))
                        if (income.eleonoraOther > 0.0) {
                            append(" · Other: ")
                            append(fmtCZK(income.eleonoraOther))
                        }
                    }
                    if (income.eleonoraGifts > 0.0) {
                        append(" · Gift: ")
                        append(fmtCZK(income.eleonoraGifts))
                    }
                }
                MemberSummaryRow(
                    member = profile.spouseEarner,
                    displayName = settings.spouseName.ifBlank { "Partner" },
                    baseYear = settings.baseYear,
                    monthlyNetTotal = spouseNetTotal,
                    breakdownSubtitle = spouseSubtitle,
                    onEditClick = if (canEdit) { { showEditSpouseDialog = true } } else null
                )
            }

            if (income.lumpSumMonthly > 0.0) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(BrandGold.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = BrandGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bonuses & Other Inflows",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Unforeseen ledger inflows & custom lump sums",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = fmtCZK(income.lumpSumMonthly),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = BodyFontFamily,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            profile.children.forEach { child ->
                Spacer(modifier = Modifier.height(6.dp))
                MemberSummaryRow(
                    member = child,
                    displayName = child.name,
                    baseYear = settings.baseYear,
                    monthlyNetTotal = 0.0,
                    breakdownSubtitle = "Statutory support until age ${settings.childSupportUntilAge}",
                    onEditClick = null
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "CHRONOLOGICAL LIFE ROADMAP",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            val sortedEvents = profile.timelineEvents.sortedBy { it.targetYear }
            if (sortedEvents.isEmpty()) {
                Text(
                    text = "No timeline events configured.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                sortedEvents.forEachIndexed { index, event ->
                    TimelineEventRow(
                        event = event,
                        isLast = index == sortedEvents.lastIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberSummaryRow(
    member: HouseholdMember,
    displayName: String,
    baseYear: Int,
    monthlyNetTotal: Double,
    breakdownSubtitle: String,
    onEditClick: (() -> Unit)? = null
) {
    val currentAge = baseYear - member.birthYear
    val (roleLabel, roleColor) = when (member.role) {
        HouseholdRole.PRIMARY_EARNER -> "Primary Earner" to BrandBlue
        HouseholdRole.SPOUSE_EARNER -> "Secondary Earner" to BrandTeal
        HouseholdRole.DEPENDENT_CHILD -> "Dependent Child" to BrandGold
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(roleColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (member.role) {
                        HouseholdRole.PRIMARY_EARNER, HouseholdRole.SPOUSE_EARNER -> Icons.Default.Person
                        HouseholdRole.DEPENDENT_CHILD -> Icons.Default.School
                    },
                    contentDescription = null,
                    tint = roleColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            currentAge < 0 -> "Planned (${member.birthYear})"
                            member.role != HouseholdRole.DEPENDENT_CHILD && member.birthYear == 2000 -> "Born ${member.birthYear}"
                            else -> "Age $currentAge (${member.birthYear})"
                        },
                        fontSize = 11.sp,
                        fontFamily = BodyFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (onEditClick != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("edit_member_${member.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit $displayName",
                                tint = BrandTeal,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Text(
                    text = roleLabel,
                    fontSize = 10.sp,
                    color = roleColor,
                    fontWeight = FontWeight.Medium
                )
                if (breakdownSubtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = breakdownSubtitle,
                        fontSize = 10.sp,
                        fontFamily = BodyFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        lineHeight = 13.sp
                    )
                }
            }

            if (monthlyNetTotal > 0.0) {
                Text(
                    text = fmtCZK(monthlyNetTotal),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = BodyFontFamily,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 6.dp)
                )
            } else if (member.role == HouseholdRole.DEPENDENT_CHILD) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BrandGold.copy(alpha = 0.12f),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = "Dependent",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = BrandGold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineEventRow(
    event: LifeTimelineEvent,
    isLast: Boolean
) {
    val (nodeColor, icon) = when (event.type) {
        TimelineEventType.CHILD_BIRTH -> BrandGold to Icons.Default.Star
        TimelineEventType.CHILD_INDEPENDENCE -> GoodGreen to Icons.Default.School
        TimelineEventType.CAREER_RETURN -> BrandTeal to Icons.Default.Person
        TimelineEventType.PRIMARY_RETIREMENT, TimelineEventType.SPOUSE_RETIREMENT -> BrandBlue to Icons.Default.Flag
        TimelineEventType.STATUTORY_UNLOCK -> GoodGreen to Icons.Default.Shield
        TimelineEventType.CUSTOM_GOAL -> BrandTeal to Icons.Default.Event
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(nodeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = nodeColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (isLast) 0.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val safeMonthlyImpact = event.monthlyCashFlowImpact.finiteOrZero()
                val safeLumpImpact = event.lumpSumCapitalImpact.finiteOrZero()
                if (safeMonthlyImpact != 0.0 || safeLumpImpact != 0.0) {
                    val impactStr = when {
                        safeMonthlyImpact != 0.0 -> {
                            val prefix = if (safeMonthlyImpact > 0.0) "+" else ""
                            "$prefix${fmtCZK(safeMonthlyImpact)} / mo"
                        }
                        else -> {
                            val prefix = if (safeLumpImpact > 0.0) "+" else ""
                            "$prefix${fmtCZK(safeLumpImpact)} capital"
                        }
                    }
                    Text(
                        text = "Impact: $impactStr",
                        fontSize = 10.sp,
                        fontFamily = BodyFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = nodeColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, nodeColor.copy(alpha = 0.25f))
            ) {
                Text(
                    text = "${event.targetYear}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BodyFontFamily,
                    color = nodeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}
