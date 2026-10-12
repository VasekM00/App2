package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.HouseholdMember
import com.example.domain.HouseholdProfile
import com.example.domain.HouseholdRole
import com.example.domain.LifeTimelineEvent
import com.example.domain.TimelineEventType
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK

@Composable
fun HouseholdTimelineCard(
    settings: SettingsEntity,
    modifier: Modifier = Modifier
) {
    val profile = HouseholdProfile.fromSettings(settings)
    val totalMembers = 1 + (if (!profile.isSingleHousehold) 1 else 0) + profile.children.size

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("household_timeline_card"),
        shape = RoundedCornerShape(20.dp),
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

            Text(
                text = "FAMILY MEMBERS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            MemberSummaryRow(member = profile.primaryEarner, baseYear = settings.baseYear)

            if (profile.spouseEarner != null) {
                Spacer(modifier = Modifier.height(6.dp))
                MemberSummaryRow(member = profile.spouseEarner, baseYear = settings.baseYear)
            }

            profile.children.forEach { child ->
                Spacer(modifier = Modifier.height(6.dp))
                MemberSummaryRow(member = child, baseYear = settings.baseYear)
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
    baseYear: Int
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
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
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (currentAge >= 0) "Age $currentAge (${member.birthYear})" else "Planned (${member.birthYear})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = roleLabel,
                    fontSize = 10.sp,
                    color = roleColor,
                    fontWeight = FontWeight.Medium
                )
            }

            if (member.currentMonthlyGrossSalary > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = fmtCZK(member.currentMonthlyGrossSalary),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "gross/mo",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
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
                        .height(24.dp)
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
                if (event.monthlyCashFlowImpact != 0.0 || event.lumpSumCapitalImpact != 0.0) {
                    val impactStr = when {
                        event.monthlyCashFlowImpact != 0.0 -> "${fmtCZK(event.monthlyCashFlowImpact)} / mo"
                        else -> fmtCZK(event.lumpSumCapitalImpact)
                    }
                    Text(
                        text = "Impact: $impactStr",
                        fontSize = 10.sp,
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
                    fontFamily = FontFamily.Monospace,
                    color = nodeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}
