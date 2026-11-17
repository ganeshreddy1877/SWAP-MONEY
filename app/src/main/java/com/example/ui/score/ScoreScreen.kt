package com.example.ui.score

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.CustomerRatingStats
import com.example.domain.MonthlyActivityPoint
import com.example.domain.SecurityValidators
import com.example.domain.SwapActivityStats
import com.example.ui.SwapMoneyUiState
import com.example.ui.components.FriendlyEmptyStateCard

@Composable
fun ScoreScreen(
    uiState: SwapMoneyUiState,
    onReportReview: (String) -> Unit
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    val activityStats = uiState.activityStats
    val ratingStats = uiState.ratingStats

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Score & Reputation",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Activity metrics and peer ratings are strictly separated.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            TabRow(
                selectedTabIndex = selectedSection,
                modifier = Modifier.testTag("score_section_tabs")
            ) {
                Tab(
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    text = { Text("A. Swap Activity") },
                    modifier = Modifier.testTag("tab_swap_activity")
                )
                Tab(
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    text = { Text("B. Customer Rating") },
                    modifier = Modifier.testTag("tab_customer_rating")
                )
            }
        }

        if (selectedSection == 0 && activityStats != null) {
            item { SwapActivitySummarySection(stats = activityStats) }
            item { ActivityBarChartCard(series = activityStats.monthlySeries) }
        } else if (selectedSection == 1 && ratingStats != null) {
            item { CustomerRatingSummarySection(stats = ratingStats) }
            if (ratingStats.reviews.isEmpty()) {
                item {
                    FriendlyEmptyStateCard(
                        icon = Icons.Outlined.RateReview,
                        title = "No written reviews yet",
                        subtitle = "Verified partner reviews appear here after completed swaps."
                    )
                }
            } else {
                items(ratingStats.reviews, key = { it.id }) { rev ->
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${rev.reviewerName} • ${rev.swapCode}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = { onReportReview(rev.id) },
                                    enabled = !rev.reported
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Flag,
                                        contentDescription = "Report review for moderation",
                                        tint = if (rev.reported) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = "Comm ${rev.communication}/5 • Punctuality ${rev.punctuality}/5 • Coop ${rev.cooperation}/5 • Overall ${rev.overallExperience}/5",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (rev.comment.isNotBlank()) {
                                Text(rev.comment, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (rev.reported) {
                                Text(
                                    text = "Flagged for moderation review",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwapActivitySummarySection(stats: SwapActivityStats) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Swap Activity reflects historical volume and completion consistency. Excessive or unnecessary transactions are never rewarded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(12.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                label = "TOTAL EXCHANGES",
                value = "${stats.totalExchanges}",
                icon = Icons.Outlined.SwapHoriz,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "SUCCESSFUL",
                value = "${stats.successfulCount} (${stats.successRatePercent}%)",
                icon = Icons.Outlined.CheckCircle,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                label = "CANCELLED",
                value = "${stats.cancelledCount}",
                icon = Icons.Outlined.Cancel,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "DISPUTED",
                value = "${stats.disputedCount}",
                icon = Icons.Outlined.WarningAmber,
                tint = if (stats.disputedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                label = "THIS MONTH",
                value = "${stats.thisMonthCount}",
                icon = Icons.Outlined.CalendarMonth,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "TOTAL VOLUME",
                value = SecurityValidators.formatInr(stats.totalVolumeInr),
                icon = Icons.Outlined.Payments,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
        MetricCard(
            label = "ACTIVE MONTHS",
            value = "${stats.activeMonthsCount} consecutive months",
            icon = Icons.Outlined.Timeline,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActivityBarChartCard(series: List<MonthlyActivityPoint>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary
    val guideColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val maxCount = (series.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Monthly Exchange Frequency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                drawLine(
                    color = guideColor,
                    start = Offset(0f, size.height * 0.5f),
                    end = Offset(size.width, size.height * 0.5f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = guideColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2f
                )
                val barCount = series.size.coerceAtLeast(1)
                val slotWidth = size.width / barCount
                val barWidth = slotWidth * 0.48f
                series.forEachIndexed { idx, point ->
                    val ratio = point.count.toFloat() / maxCount.toFloat()
                    val barHeight = (size.height - 12f) * ratio
                    val x = idx * slotWidth + (slotWidth - barWidth) / 2f
                    val y = size.height - barHeight
                    drawRoundRect(
                        color = if (idx == series.lastIndex) accentColor else primaryColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(12f, 12f)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                series.forEach { point ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${point.count}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(point.monthLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerRatingSummarySection(stats: CustomerRatingStats) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(30.dp))
                Text(
                    text = "${stats.overallScore} / 5.0",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Based on ${stats.reviewCount} eligible post-exchange reviews",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            RatingAxisRow("Communication", stats.communicationAvg)
            RatingAxisRow("Punctuality", stats.punctualityAvg)
            RatingAxisRow("Cooperation", stats.cooperationAvg)
            RatingAxisRow("Overall Experience", stats.overallExperienceAvg)
        }
    }
}

@Composable
private fun RatingAxisRow(label: String, score: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("$score / 5", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { (score / 5.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
