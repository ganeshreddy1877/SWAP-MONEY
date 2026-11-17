package com.example.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.HistoryCategory
import com.example.domain.HistoryEntry
import com.example.domain.SecurityValidators
import com.example.ui.SwapMoneyUiState
import com.example.ui.components.ExchangeStatusBadge
import com.example.ui.components.FriendlyEmptyStateCard

@Composable
fun HistoryScreen(uiState: SwapMoneyUiState) {
    var selectedCategory by remember { mutableStateOf(HistoryCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredEntries = remember(uiState.historyEntries, selectedCategory, searchQuery) {
        val byCategory = if (selectedCategory == HistoryCategory.ALL) {
            uiState.historyEntries
        } else {
            uiState.historyEntries.filter { it.category == selectedCategory }
        }
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            byCategory
        } else {
            byCategory.filter { entry ->
                entry.swapCode.contains(query, ignoreCase = true) ||
                    entry.title.contains(query, ignoreCase = true) ||
                    entry.summary.contains(query, ignoreCase = true) ||
                    (entry.amountInr?.toString()?.contains(query) == true)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Activity & Audit History",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Immutable customer ledger • Read-only records",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Read-only immutable records",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by swap ID, amount, or detail") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("history_search_input")
            )
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("history_category_filters")
            ) {
                items(HistoryCategory.entries) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.label) }
                    )
                }
            }
        }

        if (filteredEntries.isEmpty()) {
            item {
                FriendlyEmptyStateCard(
                    icon = Icons.Outlined.History,
                    title = if (searchQuery.isNotBlank()) "No matching records" else "No records in ${selectedCategory.label}",
                    subtitle = "Try clearing your search or switching back to all activities.",
                    actionLabel = if (selectedCategory != HistoryCategory.ALL || searchQuery.isNotBlank()) "Show All Activities" else null,
                    onAction = {
                        selectedCategory = HistoryCategory.ALL
                        searchQuery = ""
                    }
                )
            }
        } else {
            items(filteredEntries, key = { it.id }) { entry ->
                HistoryEntryCard(entry = entry)
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(entry: HistoryEntry) {
    var expanded by remember { mutableStateOf(false) }
    val categoryIcon = when (entry.category) {
        HistoryCategory.REQUESTS_CREATED,
        HistoryCategory.INTERESTS_SUBMITTED,
        HistoryCategory.EXCHANGES,
        HistoryCategory.COMPLETED,
        HistoryCategory.APPROVALS -> Icons.Outlined.SwapHoriz
        HistoryCategory.COMMUNICATION,
        HistoryCategory.ACCOUNT_SECURITY -> Icons.Outlined.Security
        HistoryCategory.DISPUTED,
        HistoryCategory.CANCELLED -> Icons.Outlined.Gavel
        HistoryCategory.ALL -> Icons.Outlined.History
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("history_card_${entry.swapCode}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = entry.swapCode,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (entry.status != null) {
                    ExchangeStatusBadge(status = entry.status)
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = entry.category.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(text = entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildString {
                        append(entry.timestampLabel)
                        if (entry.amountInr != null) {
                            append(" • ${SecurityValidators.formatInr(entry.amountInr)}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (expanded) "Hide audit" else "Audit trail",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = "Expand audit details",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Immutable Audit Trail",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        entry.details.forEach { line ->
                            Text(
                                text = "• $line",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
