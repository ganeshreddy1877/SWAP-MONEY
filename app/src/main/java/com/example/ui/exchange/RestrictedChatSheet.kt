package com.example.ui.exchange

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.domain.ChatMessage
import com.example.domain.SecurityValidators

@Composable
fun RestrictedChatSheetContent(
    partnerName: String,
    activeUserId: String,
    isBlocked: Boolean,
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    onBlockPartner: () -> Unit,
    onReportPartner: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var localWarning by remember { mutableStateOf<String?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }

    fun attemptSend() {
        if (draft.isBlank()) return
        val check = SecurityValidators.validateRestrictedChatMessage(draft)
        if (!check.allowed) {
            localWarning = check.reason
        } else {
            onSendMessage(draft)
            draft = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Safe Chat • $partnerName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Phone numbers, emails, OTPs & UPI PINs are auto-blocked",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Row {
                IconButton(
                    onClick = { showReportDialog = true },
                    modifier = Modifier.testTag("chat_report_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Flag,
                        contentDescription = "Report user",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
                IconButton(
                    onClick = onBlockPartner,
                    modifier = Modifier.testTag("chat_block_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Block,
                        contentDescription = "Block user",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (isBlocked) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "This conversation is blocked. Messaging is disabled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            // Permitted starter prompts
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Quick Replies",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SecurityValidators.starterPrompts.forEach { prompt ->
                        AssistChip(
                            onClick = {
                                localWarning = null
                                onSendMessage(prompt)
                            },
                            label = { Text(prompt, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 280.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            text = "Tap a quick reply above or send a safe message to coordinate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        val isMine = msg.senderId == activeUserId
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                border = if (isMine) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = msg.timestampLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isMine) {
                                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                        if (isMine && msg.delivered) {
                                            Icon(
                                                imageVector = Icons.Outlined.DoneAll,
                                                contentDescription = "Delivered",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (localWarning != null) {
                Text(
                    text = localWarning!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it; localWarning = null },
                    placeholder = { Text("Type a safe message...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { attemptSend() }),
                    modifier = Modifier.weight(1f).testTag("chat_message_input")
                )
                FilledIconButton(
                    onClick = { attemptSend() },
                    enabled = draft.isNotBlank(),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.size(48.dp).testTag("chat_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Send chat message"
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showReportDialog) {
        var reason by remember { mutableStateOf("Requested prohibited PII / off-platform payment") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report Conversation") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Safety concern") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onReportPartner(reason)
                    showReportDialog = false
                }) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
            }
        )
    }
}
