package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.BackendMode
import com.example.domain.SecurityValidators
import com.example.domain.UserAccount
import com.example.ui.SwapMoneyUiState
import com.example.ui.components.OfflineStatusBanner
import com.example.ui.components.RegulatoryComplianceBanner
import com.example.ui.components.SwapMoneyLogoBadge

private enum class AuthScreenMode {
    LOGIN,
    REGISTER,
    RECOVER_PASSWORD
}

@Composable
fun AuthFlowScreen(
    uiState: SwapMoneyUiState,
    onLogin: (identifier: String, password: String) -> Unit,
    onSendOtp: (channel: String, target: String, onSent: () -> Unit) -> Unit,
    onVerifyOtp: (channel: String, target: String, code: String, onVerified: () -> Unit) -> Unit,
    onRegister: (name: String, mobile: String, email: String, address: String, password: String) -> Unit,
    onRecoverPassword: (identifier: String, newPassword: String, onDone: () -> Unit) -> Unit,
    onUpdateBackendConfig: (BackendMode, String) -> Unit
) {
    var mode by remember { mutableStateOf(AuthScreenMode.LOGIN) }
    var showServerConfig by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SwapMoneyLogoBadge()
                IconButton(
                    onClick = { showServerConfig = !showServerConfig },
                    modifier = Modifier.testTag("auth_server_config_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Dns,
                        contentDescription = "Configure FastAPI server or sandbox",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            OfflineStatusBanner(
                isOnline = uiState.isOnline,
                pendingCount = uiState.pendingSyncOps.size
            )

            RegulatoryComplianceBanner(
                backendMode = uiState.preferences.backendMode,
                activeUser = null,
                sandboxAccounts = emptyList(),
                onSwitchPersona = {}
            )

            AnimatedVisibility(visible = showServerConfig) {
                BackendConnectionCard(
                    currentMode = uiState.preferences.backendMode,
                    currentUrl = uiState.preferences.fastApiBaseUrl,
                    onSave = { newMode, newUrl ->
                        onUpdateBackendConfig(newMode, newUrl)
                        showServerConfig = false
                    }
                )
            }

            when (mode) {
                AuthScreenMode.LOGIN -> LoginCard(
                    backendMode = uiState.preferences.backendMode,
                    sandboxAccounts = uiState.allSandboxAccounts,
                    onLogin = onLogin,
                    onOpenRegister = { mode = AuthScreenMode.REGISTER },
                    onOpenRecovery = { mode = AuthScreenMode.RECOVER_PASSWORD }
                )
                AuthScreenMode.REGISTER -> RegistrationCard(
                    sandboxOtpHint = uiState.sandboxOtpHint,
                    onSendOtp = onSendOtp,
                    onVerifyOtp = onVerifyOtp,
                    onRegister = onRegister,
                    onBackToLogin = { mode = AuthScreenMode.LOGIN }
                )
                AuthScreenMode.RECOVER_PASSWORD -> PasswordRecoveryCard(
                    sandboxOtpHint = uiState.sandboxOtpHint,
                    onSendOtp = onSendOtp,
                    onVerifyOtp = onVerifyOtp,
                    onRecover = { id, pwd ->
                        onRecoverPassword(id, pwd) { mode = AuthScreenMode.LOGIN }
                    },
                    onBackToLogin = { mode = AuthScreenMode.LOGIN }
                )
            }
        }
    }
}

@Composable
private fun BackendConnectionCard(
    currentMode: BackendMode,
    currentUrl: String,
    onSave: (BackendMode, String) -> Unit
) {
    var selectedMode by remember(currentMode) { mutableStateOf(currentMode) }
    var urlInput by remember(currentUrl) { mutableStateOf(currentUrl) }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Backend Connection Environment",
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackendMode.entries.forEach { m ->
                    FilterChip(
                        selected = selectedMode == m,
                        onClick = { selectedMode = m },
                        label = { Text(m.label) }
                    )
                }
            }
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("External FastAPI Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onSave(selectedMode, urlInput.trim()) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Apply Connection")
            }
        }
    }
}

@Composable
private fun LoginCard(
    backendMode: BackendMode,
    sandboxAccounts: List<UserAccount>,
    onLogin: (String, String) -> Unit,
    onOpenRegister: () -> Unit,
    onOpenRecovery: () -> Unit
) {
    var identifier by remember { mutableStateOf("aarav.mehta@example.in") }
    var password by remember { mutableStateOf("SwapMoney@2026") }
    var showPassword by remember { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Welcome Back",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Sign in with your Email, Mobile number, or SWU ID to start exchanging safely.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it },
                label = { Text("Email, Mobile, or SWU ID") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_identifier_input")
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = "Toggle password visibility"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_password_input")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onOpenRecovery,
                    modifier = Modifier.testTag("forgot_password_button")
                ) {
                    Icon(Icons.Outlined.LockReset, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Forgot Password?")
                }
            }
            Button(
                onClick = { onLogin(identifier, password) },
                enabled = identifier.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("login_submit_button")
            ) {
                Text("Sign In Securely")
            }

            if (backendMode == BackendMode.COMPLIANCE_SANDBOX && sandboxAccounts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Quick Sandbox Demo Sign-In",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sandboxAccounts.take(3).forEach { account ->
                            AssistChip(
                                onClick = {
                                    identifier = account.email
                                    password = "SwapMoney@2026"
                                    onLogin(account.email, "SwapMoney@2026")
                                },
                                label = { Text(account.fullName.substringBefore(" ")) },
                                modifier = Modifier.weight(1f).testTag("quick_login_${account.swuId}")
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onOpenRegister,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_register_button")
            ) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Create New Account")
            }
        }
    }
}

@Composable
private fun RegistrationCard(
    sandboxOtpHint: String?,
    onSendOtp: (String, String, () -> Unit) -> Unit,
    onVerifyOtp: (String, String, String, () -> Unit) -> Unit,
    onRegister: (String, String, String, String, String) -> Unit,
    onBackToLogin: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var fullName by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var mobileOtp by remember { mutableStateOf("") }
    var mobileOtpSent by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var emailOtp by remember { mutableStateOf("") }
    var emailOtpSent by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val stepTitle = when (step) {
        1 -> "Verify your mobile number"
        2 -> "Verify your email address"
        else -> "Set a strong password"
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Create Account (Step $step of 3)",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stepTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { step / 3f },
                modifier = Modifier.fillMaxWidth()
            )

            if (sandboxOtpHint != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = sandboxOtpHint,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (localError != null) {
                Text(
                    text = localError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            when (step) {
                1 -> {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it; localError = null },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("reg_name_input")
                    )
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { if (it.length <= 10) mobile = it.filter { c -> c.isDigit() }; localError = null },
                        label = { Text("10-Digit Mobile Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("reg_mobile_input")
                    )
                    if (mobileOtpSent) {
                        OutlinedTextField(
                            value = mobileOtp,
                            onValueChange = { if (it.length <= 6) mobileOtp = it.filter { c -> c.isDigit() } },
                            label = { Text("Enter 6-Digit Mobile OTP") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("reg_mobile_otp_input")
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (!SecurityValidators.validateIndianMobile(mobile)) {
                                    localError = "Enter a valid 10-digit Indian mobile number."
                                } else if (fullName.trim().length < 2) {
                                    localError = "Enter your full name."
                                } else {
                                    onSendOtp("Mobile", mobile) { mobileOtpSent = true }
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("reg_send_mobile_otp")
                        ) {
                            Text(if (mobileOtpSent) "Resend OTP" else "Send Mobile OTP")
                        }
                        if (mobileOtpSent) {
                            Button(
                                onClick = {
                                    onVerifyOtp("Mobile", mobile, mobileOtp) {
                                        step = 2
                                        localError = null
                                    }
                                },
                                enabled = mobileOtp.length == 6,
                                modifier = Modifier.weight(1f).testTag("reg_verify_mobile_otp")
                            ) {
                                Text("Verify Mobile")
                            }
                        }
                    }
                }
                2 -> {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; localError = null },
                        label = { Text("Email Address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("reg_email_input")
                    )
                    if (emailOtpSent) {
                        OutlinedTextField(
                            value = emailOtp,
                            onValueChange = { if (it.length <= 6) emailOtp = it.filter { c -> c.isDigit() } },
                            label = { Text("Enter 6-Digit Email OTP") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("reg_email_otp_input")
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (!SecurityValidators.validateEmail(email)) {
                                    localError = "Enter a valid email address."
                                } else {
                                    onSendOtp("Email", email.trim()) { emailOtpSent = true }
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("reg_send_email_otp")
                        ) {
                            Text(if (emailOtpSent) "Resend OTP" else "Send Email OTP")
                        }
                        if (emailOtpSent) {
                            Button(
                                onClick = {
                                    onVerifyOtp("Email", email.trim(), emailOtp) {
                                        step = 3
                                        localError = null
                                    }
                                },
                                enabled = emailOtp.length == 6,
                                modifier = Modifier.weight(1f).testTag("reg_verify_email_otp")
                            ) {
                                Text("Verify Email")
                            }
                        }
                    }
                }
                3 -> {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Residential Address (Optional & Kept Private)") },
                        modifier = Modifier.fillMaxWidth().testTag("reg_address_input")
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; localError = null },
                        label = { Text("Password (8+ chars, uppercase & digit)") },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("reg_password_input")
                    )
                    Button(
                        onClick = {
                            val pwdErr = SecurityValidators.validatePasswordStrength(password)
                            if (pwdErr != null) {
                                localError = pwdErr
                            } else {
                                onRegister(fullName, mobile, email, address, password)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("reg_complete_button")
                    ) {
                        Text("Complete Profile & Get SWU ID")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 1) {
                    TextButton(onClick = { step -= 1; localError = null }) {
                        Text("Previous Step")
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                TextButton(onClick = onBackToLogin) {
                    Text("Back to Sign In")
                }
            }
        }
    }
}

@Composable
private fun PasswordRecoveryCard(
    sandboxOtpHint: String?,
    onSendOtp: (String, String, () -> Unit) -> Unit,
    onVerifyOtp: (String, String, String, () -> Unit) -> Unit,
    onRecover: (String, String) -> Unit,
    onBackToLogin: () -> Unit
) {
    var target by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }
    var otpVerified by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var showNewPassword by remember { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Recover Password",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Verify your registered email or mobile with a 6-digit OTP to set a new password.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (sandboxOtpHint != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = sandboxOtpHint,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("Registered Email or Mobile") },
                singleLine = true,
                enabled = !otpVerified,
                modifier = Modifier.fillMaxWidth()
            )
            if (!otpVerified) {
                if (otpSent) {
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 6) otpCode = it.filter { c -> c.isDigit() } },
                        label = { Text("6-Digit Recovery OTP") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onSendOtp("Recovery", target.trim()) { otpSent = true } },
                        enabled = target.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (otpSent) "Resend OTP" else "Send Recovery OTP")
                    }
                    if (otpSent) {
                        Button(
                            onClick = {
                                onVerifyOtp("Recovery", target.trim(), otpCode) { otpVerified = true }
                            },
                            enabled = otpCode.length == 6,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Verify OTP")
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Strong Password") },
                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showNewPassword = !showNewPassword }) {
                            Icon(
                                imageVector = if (showNewPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "Toggle password visibility"
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onRecover(target, newPassword) },
                    enabled = newPassword.length >= 8,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Reset Password")
                }
            }
            TextButton(onClick = onBackToLogin, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Back to Sign In")
            }
        }
    }
}
