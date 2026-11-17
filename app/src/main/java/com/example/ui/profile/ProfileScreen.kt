package com.example.ui.profile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.AppPreferences
import com.example.domain.BackendMode
import com.example.domain.MfaActionType
import com.example.domain.SecurityValidators
import com.example.ui.SwapMoneyUiState
import com.example.ui.components.MfaChallengeDialog
import com.example.ui.components.VerifiedIndicatorChip

@Composable
fun ProfileScreen(
    uiState: SwapMoneyUiState,
    onMfaAction: (MfaActionType, String, String, () -> Unit) -> Unit,
    onUpdatePreferences: ((AppPreferences) -> AppPreferences) -> Unit,
    onToggleSimulatedOffline: () -> Unit,
    onLogout: () -> Unit
) {
    val user = uiState.activeUser ?: return
    val clipboardManager = LocalClipboardManager.current
    var uidCopied by remember(user.swuId) { mutableStateOf(false) }
    var revealPrivateAddress by remember { mutableStateOf(false) }
    var showDeveloperSettings by remember { mutableStateOf(false) }
    var activeMfaChallenge by remember { mutableStateOf<MfaActionType?>(null) }
    var serverUrlDraft by remember(uiState.preferences.fastApiBaseUrl) {
        mutableStateOf(uiState.preferences.fastApiBaseUrl)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onUpdatePreferences { it.copy(notificationsEnabled = granted) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(user.fullName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "UID: ${user.swuId} • Member since ${user.createdAtLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("profile_uid_header")
                    )
                    Spacer(Modifier.height(4.dp))
                    VerifiedIndicatorChip(verified = user.mobileVerified && user.emailVerified)
                }
                OutlinedButton(
                    onClick = onLogout,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("profile_header_logout_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = "Logout",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Logout")
                }
            }
        }

        // Personal Information & Verification
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Personal Information & Verification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    ProfileDetailRow("Swap Money UID", user.swuId) {
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(user.swuId))
                                uidCopied = true
                            },
                            modifier = Modifier.testTag("copy_uid_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy UID",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (uidCopied) "Copied" else "Copy")
                        }
                    }
                    ProfileDetailRow("Mobile", "${SecurityValidators.maskMobile(user.mobileNumber)} • Verified") {
                        TextButton(
                            onClick = { activeMfaChallenge = MfaActionType.CHANGE_MOBILE },
                            modifier = Modifier.testTag("change_mobile_mfa_btn")
                        ) { Text("Change (MFA)") }
                    }
                    ProfileDetailRow("Email", "${SecurityValidators.maskEmail(user.email)} • Verified") {
                        TextButton(
                            onClick = { activeMfaChallenge = MfaActionType.CHANGE_EMAIL },
                            modifier = Modifier.testTag("change_email_mfa_btn")
                        ) { Text("Change (MFA)") }
                    }
                    ProfileDetailRow(
                        label = "Private Address",
                        value = if (revealPrivateAddress) user.residentialAddress.ifBlank { "Not provided" } else "Hidden for privacy"
                    ) {
                        TextButton(onClick = { revealPrivateAddress = !revealPrivateAddress }) {
                            Text(if (revealPrivateAddress) "Hide" else "Reveal")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VerifiedIndicatorChip(user.mobileVerified, "Mobile OTP")
                        VerifiedIndicatorChip(user.emailVerified, "Email OTP")
                        VerifiedIndicatorChip(user.additionalKycVerified, "KYC Profile")
                    }
                }
            }
        }

        // MFA & Security Dashboard
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("MFA & Security Dashboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Multi-Factor Authentication (MFA)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Authenticator: ${if (user.authenticatorLinked) "Linked" else "OTP Fallback"} • Passkey: ${if (user.passkeyEnabled) "Enabled" else "Available"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = user.mfaEnabled,
                            onCheckedChange = { activeMfaChallenge = MfaActionType.SECURITY_SETTINGS },
                            modifier = Modifier.testTag("mfa_toggle_switch")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { activeMfaChallenge = MfaActionType.CHANGE_PASSWORD },
                            modifier = Modifier.weight(1f).testTag("change_password_mfa_btn")
                        ) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Password")
                        }
                        OutlinedButton(
                            onClick = { activeMfaChallenge = MfaActionType.TERMINATE_SESSIONS },
                            modifier = Modifier.weight(1f).testTag("terminate_sessions_mfa_btn")
                        ) {
                            Text("End Other Sessions")
                        }
                    }

                    Text("Registered Devices (${uiState.devices.size})", style = MaterialTheme.typography.labelLarge)
                    uiState.devices.forEach { d ->
                        Text(
                            text = "• ${d.deviceName} (${d.platform}) — ${d.lastActiveLabel}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Text("Active Sessions (${uiState.sessions.size})", style = MaterialTheme.typography.labelLarge)
                    uiState.sessions.forEach { s ->
                        Text(
                            text = "• ${s.deviceName} • ${s.locationLabel} (${s.ipMasked})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Text("Login & Failed Attempt History", style = MaterialTheme.typography.labelLarge)
                    uiState.loginEvents.take(4).forEach { e ->
                        Text(
                            text = "• [${if (e.success) "OK" else "FAILED"}] ${e.timestampLabel}: ${e.detail}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (e.success) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Everyday App Preferences
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("App Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                    PreferenceSwitchRow(
                        title = "Dark Mode",
                        icon = Icons.Outlined.DarkMode,
                        checked = uiState.preferences.darkMode
                    ) { enabled ->
                        onUpdatePreferences { it.copy(darkMode = enabled) }
                    }
                    PreferenceSwitchRow(
                        title = "Push & In-App Notifications",
                        icon = Icons.Outlined.Notifications,
                        checked = uiState.preferences.notificationsEnabled
                    ) { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onUpdatePreferences { it.copy(notificationsEnabled = enabled) }
                        }
                    }
                    PreferenceSwitchRow(
                        title = "Share Approximate Location Only",
                        icon = Icons.Outlined.MyLocation,
                        checked = uiState.preferences.shareApproximateLocation
                    ) { enabled ->
                        onUpdatePreferences { it.copy(shareApproximateLocation = enabled) }
                    }
                }
            }
        }

        // Collapsible Developer & Backend Connectivity Settings
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeveloperSettings = !showDeveloperSettings }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Developer & Connectivity Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Mode: ${uiState.preferences.backendMode.label} • ${if (uiState.isOnline) "Online" else "Simulated Offline"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = if (showDeveloperSettings) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = "Toggle developer settings"
                        )
                    }

                    AnimatedVisibility(visible = showDeveloperSettings) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PreferenceSwitchRow(
                                title = "Simulate Network Outage (Test Offline Sync)",
                                icon = null,
                                checked = !uiState.isOnline
                            ) {
                                onToggleSimulatedOffline()
                            }
                            Text("Backend Mode", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BackendMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = uiState.preferences.backendMode == mode,
                                        onClick = { onUpdatePreferences { it.copy(backendMode = mode) } },
                                        label = { Text(mode.label) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = serverUrlDraft,
                                onValueChange = { serverUrlDraft = it },
                                label = { Text("External FastAPI Base URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = { onUpdatePreferences { it.copy(fastApiBaseUrl = serverUrlDraft.trim()) } },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Save Endpoint")
                            }
                        }
                    }
                }
            }
        }

        // Logout & Account Deletion
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onLogout,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("profile_logout_button")
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "Logout")
                    Spacer(Modifier.width(6.dp))
                    Text("Logout")
                }
                OutlinedButton(
                    onClick = { activeMfaChallenge = MfaActionType.DELETE_ACCOUNT },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Delete Account")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    activeMfaChallenge?.let { action ->
        MfaChallengeDialog(
            actionType = action,
            onDismiss = { activeMfaChallenge = null },
            onConfirm = { code, newValue ->
                onMfaAction(action, code, newValue) {
                    activeMfaChallenge = null
                }
            }
        )
    }
}

@Composable
private fun ProfileDetailRow(
    label: String,
    value: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        trailing()
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    icon: ImageVector?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
