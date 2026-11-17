package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.BackendMode
import com.example.domain.ExchangeStatus
import com.example.domain.MfaActionType
import com.example.domain.UserAccount

@Composable
fun SwapMoneyLogoBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 36.dp else 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.SwapHoriz,
                contentDescription = "Swap Money emblem",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(if (compact) 22.dp else 26.dp)
            )
        }
        Column {
            Text(
                text = "SWAP MONEY",
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!compact) {
                Text(
                    text = "CONNECT. EXCHANGE. VERIFY.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun RegulatoryComplianceBanner(
    backendMode: BackendMode,
    activeUser: UserAccount?,
    sandboxAccounts: List<UserAccount>,
    onSwitchPersona: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPersonaSheet by remember { mutableStateOf(false) }
    var showLegalNotice by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { showLegalNotice = !showLegalNotice }
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Gavel,
                        contentDescription = "Regulatory compliance status",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (backendMode == BackendMode.COMPLIANCE_SANDBOX) {
                            "Sandbox Mode • Non-custodial"
                        } else {
                            "FastAPI Live Server (Pending Review)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Toggle compliance details",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.size(15.dp)
                    )
                }
                if (backendMode == BackendMode.COMPLIANCE_SANDBOX && activeUser != null && sandboxAccounts.size > 1) {
                    TextButton(
                        onClick = { showPersonaSheet = !showPersonaSheet },
                        modifier = Modifier.testTag("sandbox_persona_toggle")
                    ) {
                        Text(
                            text = if (showPersonaSheet) "Hide Actors" else "Switch Actor",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            AnimatedVisibility(visible = showLegalNotice) {
                Text(
                    text = "Non-custodial P2P matching only. Swap Money never holds funds, wallets, or executes payments. Live financial facilitation awaits RBI legal assessment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }
            AnimatedVisibility(visible = showPersonaSheet) {
                Column(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                    Text(
                        text = "Test multi-party workflows as:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        sandboxAccounts.take(3).forEach { acc ->
                            val isSelected = acc.id == activeUser?.id
                            OutlinedButton(
                                onClick = {
                                    onSwitchPersona(acc.id)
                                    showPersonaSheet = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("persona_btn_${acc.swuId}"),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    contentColor = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            ) {
                                Text(
                                    text = acc.fullName.substringBefore(" "),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
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
fun ExchangeWorkflowStepper(
    currentStep: Int, // 0..3 (Match, Consent, Meet, Verify)
    completedAll: Boolean = false,
    nextStepHint: String,
    modifier: Modifier = Modifier
) {
    val steps = listOf("Match", "Consent", "Meet", "Verify")
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                steps.forEachIndexed { idx, label ->
                    val isDone = completedAll || idx < currentStep
                    val isActive = !completedAll && idx == currentStep
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isDone -> MaterialTheme.colorScheme.secondary
                                        isActive -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDone) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "$label completed",
                                    tint = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isActive || isDone) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isDone -> MaterialTheme.colorScheme.secondary
                                isActive -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1
                        )
                    }
                }
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = nextStepHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun FriendlyEmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun OfflineStatusBanner(
    isOnline: Boolean,
    pendingCount: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = !isOnline, modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.errorContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = "Offline status",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Offline — Backend Unreachable",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = if (pendingCount > 0) {
                            "$pendingCount action(s) queued safely. Confirmed records preserved; no false completion."
                        } else {
                            "Confirmed history preserved. Submissions require connectivity."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
fun ExchangeStatusBadge(
    status: ExchangeStatus,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (status) {
        ExchangeStatus.CUSTOMER_REPORTED_COMPLETION,
        ExchangeStatus.COMPLETED_VERIFIED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        ExchangeStatus.DISPUTED,
        ExchangeStatus.CANCELLED,
        ExchangeStatus.EXPIRED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        ExchangeStatus.FIRST_APPROVAL,
        ExchangeStatus.SECOND_APPROVAL,
        ExchangeStatus.AWAITING_CONSENT,
        ExchangeStatus.EXCHANGE_COORDINATION,
        ExchangeStatus.AWAITING_CONFIRMATION,
        ExchangeStatus.EVIDENCE_SUBMITTED,
        ExchangeStatus.UNDER_REVIEW -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        ExchangeStatus.REQUEST_CREATED,
        ExchangeStatus.INTEREST_RECEIVED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = bg
    ) {
        Text(
            text = status.displayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun VerifiedIndicatorChip(
    verified: Boolean,
    label: String = if (verified) "Verified" else "Pending"
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (verified) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.VerifiedUser,
                contentDescription = label,
                tint = if (verified) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (verified) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun MfaChallengeDialog(
    actionType: MfaActionType,
    onDismiss: () -> Unit,
    onConfirm: (mfaCode: String, newValue: String) -> Unit
) {
    var mfaCode by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    val needsNewInput = actionType in setOf(
        MfaActionType.CHANGE_MOBILE,
        MfaActionType.CHANGE_EMAIL,
        MfaActionType.CHANGE_PASSWORD,
        MfaActionType.EDIT_PERSONAL_INFO
    )
    val inputLabel = when (actionType) {
        MfaActionType.CHANGE_MOBILE -> "New 10-digit Mobile Number"
        MfaActionType.CHANGE_EMAIL -> "New Email Address"
        MfaActionType.CHANGE_PASSWORD -> "New Strong Password"
        MfaActionType.EDIT_PERSONAL_INFO -> "Updated Residential Address (Private)"
        else -> ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "MFA security verification",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "MFA Verification Required",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Confirm '${actionType.label}' with your 6-digit Authenticator / OTP code. A security alert will be sent.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (needsNewInput) {
                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        label = { Text(inputLabel) },
                        singleLine = true,
                        visualTransformation = if (actionType == MfaActionType.CHANGE_PASSWORD) {
                            PasswordVisualTransformation()
                        } else {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when (actionType) {
                                MfaActionType.CHANGE_MOBILE -> KeyboardType.Phone
                                MfaActionType.CHANGE_EMAIL -> KeyboardType.Email
                                MfaActionType.CHANGE_PASSWORD -> KeyboardType.Password
                                else -> KeyboardType.Text
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mfa_new_value_input")
                    )
                }
                OutlinedTextField(
                    value = mfaCode,
                    onValueChange = { if (it.length <= 6) mfaCode = it.filter { ch -> ch.isDigit() } },
                    label = { Text("6-Digit Authenticator / MFA Code") },
                    placeholder = { Text("e.g. 123456") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mfa_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(mfaCode, newValue) },
                enabled = mfaCode.length == 6 && (!needsNewInput || newValue.isNotBlank()),
                modifier = Modifier.testTag("mfa_confirm_button")
            ) {
                Text("Verify & Proceed")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
