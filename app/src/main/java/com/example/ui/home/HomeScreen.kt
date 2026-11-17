package com.example.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.EvidenceItem
import com.example.domain.ExchangeRequest
import com.example.domain.ExchangeStatus
import com.example.domain.InAppNotification
import com.example.domain.NotificationActionStatus
import com.example.domain.PaymentMethod
import com.example.domain.RequestInterest
import com.example.domain.SearchRadius
import com.example.domain.SecurityValidators
import com.example.ui.SwapMoneyUiState
import com.example.ui.components.ExchangeStatusBadge
import com.example.ui.components.FriendlyEmptyStateCard
import com.example.ui.components.OfflineStatusBanner
import com.example.ui.components.RegulatoryComplianceBanner
import com.example.ui.components.SwapMoneyLogoBadge
import com.example.ui.components.VerifiedIndicatorChip
import com.example.ui.exchange.EvidenceUploadDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: SwapMoneyUiState,
    onRefreshLocation: () -> Unit,
    onSwitchPersona: (String) -> Unit,
    onOpenProfileTab: () -> Unit,
    onOpenRequestDetail: (String) -> Unit,
    onCreateRequest: (
        wanted: PaymentMethod,
        offered: PaymentMethod,
        amount: Int,
        radius: SearchRadius,
        expiryMinutes: Int,
        onDone: () -> Unit
    ) -> Unit,
    onMarkNotificationsRead: () -> Unit,
    onAcceptNotification: (notificationId: String, acceptorAddress: String) -> Unit,
    onIgnoreNotification: (String) -> Unit,
    onMarkReceived: (requestId: String) -> Unit,
    onSubmitProof: (requestId: String, refNo: String, uri: String?, mime: String?, sizeBytes: Long) -> Unit
) {
    val activeUser = uiState.activeUser ?: return
    var showCreateSheet by remember { mutableStateOf(false) }
    var presetWantedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var showLegalNoticeForBank by remember { mutableStateOf(false) }
    var proofTargetRequestId by remember { mutableStateOf<String?>(null) }
    var showHowItWorks by rememberSaveable { mutableStateOf(true) }

    val coarseLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onRefreshLocation()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SwapMoneyLogoBadge(compact = true)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Hello, ${activeUser.fullName.substringBefore(" ")}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                coarseLocationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                            .testTag("home_location_chip"),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = "Approximate area",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = activeUser.areaLabel,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            onMarkNotificationsRead()
                            showNotificationsSheet = true
                        },
                        modifier = Modifier.testTag("home_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (uiState.unreadNotificationsCount > 0) {
                                    Badge { Text("${uiState.unreadNotificationsCount}") }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Notifications"
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenProfileTab,
                        modifier = Modifier.testTag("home_profile_shortcut")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = "Profile shortcut",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Offline & Compliance Banners
        item {
            OfflineStatusBanner(
                isOnline = uiState.isOnline,
                pendingCount = uiState.pendingSyncOps.size
            )
            RegulatoryComplianceBanner(
                backendMode = uiState.preferences.backendMode,
                activeUser = activeUser,
                sandboxAccounts = uiState.allSandboxAccounts,
                onSwitchPersona = onSwitchPersona
            )
        }

        // Daily Allowance & Reputation Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DAILY EXCHANGE ALLOWANCE (IST)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                            )
                            Text(
                                text = "${SecurityValidators.formatInr(uiState.dailyRemainingInr)} remaining",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Star,
                                    contentDescription = "Customer Score",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${uiState.ratingStats?.overallScore ?: activeUser.ratingAverage}/5.0",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    val usedFraction = (uiState.dailyUsedInr.toFloat() / SecurityValidators.DAILY_LIMIT_INR.toFloat())
                        .coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { usedFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                    )
                    Text(
                        text = "Used today: ${SecurityValidators.formatInr(uiState.dailyUsedInr)} of ₹5,000 • Resets 00:00 IST",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Friendly Dismissible Quick Guide
        item {
            AnimatedVisibility(visible = showHowItWorks) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                    imageVector = Icons.Outlined.Lightbulb,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "How Swap Money Works",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { showHowItWorks = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Dismiss guide",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = "1. Create or accept a nearby swap  •  2. Acceptor sets a safe public spot  •  3. Meet, upload proof & mark Received.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Primary Actions: WANT CASH / WANT UPI / WANT BANK TRANSFER + Create Request
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Quick Exchange Actions",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionTile(
                        title = "WANT CASH",
                        subtitle = "Offer UPI",
                        icon = Icons.Outlined.Payments,
                        enabled = true,
                        modifier = Modifier.weight(1f).testTag("quick_want_cash"),
                        onClick = {
                            presetWantedMethod = PaymentMethod.CASH
                            showCreateSheet = true
                        }
                    )
                    QuickActionTile(
                        title = "WANT UPI",
                        subtitle = "Offer Cash",
                        icon = Icons.Outlined.QrCode2,
                        enabled = true,
                        modifier = Modifier.weight(1f).testTag("quick_want_upi"),
                        onClick = {
                            presetWantedMethod = PaymentMethod.UPI
                            showCreateSheet = true
                        }
                    )
                    QuickActionTile(
                        title = "WANT BANK",
                        subtitle = "Legal Hold",
                        icon = Icons.Outlined.AccountBalance,
                        enabled = false,
                        modifier = Modifier.weight(1f).testTag("quick_want_bank"),
                        onClick = { showLegalNoticeForBank = true }
                    )
                }
                Button(
                    onClick = {
                        presetWantedMethod = PaymentMethod.CASH
                        showCreateSheet = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("create_request_cta")
                ) {
                    Icon(Icons.Outlined.AddCircleOutline, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create Exchange Request")
                }
            }
        }

        // My Active Requests & Coordinated Swaps
        if (uiState.myActiveRequests.isNotEmpty()) {
            item {
                Text(
                    text = "Your Active Requests & Exchanges",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(uiState.myActiveRequests, key = { "mine_${it.id}" }) { req ->
                val reqInterests = uiState.interests.filter { it.requestId == req.id }
                val myInterest = reqInterests.firstOrNull { it.interestedUserId == activeUser.id }
                val isOwn = req.creatorId == activeUser.id
                val evidenceCount = uiState.evidenceItems.count { it.requestId == req.id }
                RequestSummaryCard(
                    request = req,
                    isOwnRequest = isOwn,
                    hasExpressedInterest = myInterest != null,
                    isAcceptedByReceiver = !isOwn && (myInterest != null || req.selectedPartnerId == activeUser.id),
                    receiverMarkedReceived = (myInterest?.receiverMarkedReceived == true) ||
                        (!isOwn && req.partnerConfirmedProvided == true),
                    evidenceCount = evidenceCount,
                    interestCount = reqInterests.size,
                    currentTimeEpochMs = uiState.currentTimeEpochMs,
                    onMarkReceived = { onMarkReceived(req.id) },
                    onOpenProof = { proofTargetRequestId = req.id },
                    onClick = { onOpenRequestDetail(req.id) }
                )
            }
        }

        // Nearby Requests Feed
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nearby Requests (${uiState.nearbyRequests.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Exact addresses hidden",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        if (uiState.nearbyRequests.isEmpty()) {
            item {
                FriendlyEmptyStateCard(
                    icon = Icons.Outlined.Explore,
                    title = "No open nearby requests right now",
                    subtitle = "Post a new exchange request or tap your area chip above to refresh nearby activity.",
                    actionLabel = "Create Request",
                    onAction = {
                        presetWantedMethod = PaymentMethod.CASH
                        showCreateSheet = true
                    }
                )
            }
        } else {
            items(uiState.nearbyRequests, key = { "nearby_${it.id}" }) { req ->
                val reqInterests = uiState.interests.filter { it.requestId == req.id }
                val myInterest = reqInterests.firstOrNull { it.interestedUserId == activeUser.id }
                val evidenceCount = uiState.evidenceItems.count { it.requestId == req.id }
                RequestSummaryCard(
                    request = req,
                    isOwnRequest = false,
                    hasExpressedInterest = myInterest != null,
                    isAcceptedByReceiver = myInterest != null,
                    receiverMarkedReceived = myInterest?.receiverMarkedReceived == true,
                    evidenceCount = evidenceCount,
                    interestCount = reqInterests.size,
                    currentTimeEpochMs = uiState.currentTimeEpochMs,
                    onMarkReceived = { onMarkReceived(req.id) },
                    onOpenProof = { proofTargetRequestId = req.id },
                    onClick = { onOpenRequestDetail(req.id) }
                )
            }
        }
    }

    if (showCreateSheet) {
        CreateRequestModal(
            initialWanted = presetWantedMethod,
            remainingDailyInr = uiState.dailyRemainingInr,
            onDismiss = { showCreateSheet = false },
            onSubmit = { wanted, offered, amount, radius, expiry ->
                onCreateRequest(wanted, offered, amount, radius, expiry) {
                    showCreateSheet = false
                }
            }
        )
    }

    if (showNotificationsSheet) {
        ModalBottomSheet(onDismissRequest = { showNotificationsSheet = false }) {
            NotificationsHistorySheet(
                notifications = uiState.notifications,
                requests = uiState.allRequests,
                interests = uiState.interests,
                evidenceItems = uiState.evidenceItems,
                activeUserId = activeUser.id,
                currentTimeEpochMs = uiState.currentTimeEpochMs,
                onAccept = { notifId, address ->
                    onAcceptNotification(notifId, address)
                    showNotificationsSheet = false
                },
                onIgnore = onIgnoreNotification,
                onMarkReceived = onMarkReceived,
                onOpenProof = { reqId ->
                    showNotificationsSheet = false
                    proofTargetRequestId = reqId
                },
                onOpenRequest = { reqId ->
                    showNotificationsSheet = false
                    onOpenRequestDetail(reqId)
                }
            )
        }
    }

    proofTargetRequestId?.let { targetReqId ->
        EvidenceUploadDialog(
            onDismiss = { proofTargetRequestId = null },
            onSubmit = { ref, uri, mime, size ->
                onSubmitProof(targetReqId, ref, uri, mime, size)
                proofTargetRequestId = null
            }
        )
    }

    if (showLegalNoticeForBank) {
        AlertDialog(
            onDismissRequest = { showLegalNoticeForBank = false },
            title = { Text("Bank Transfer Pending Legal Review") },
            text = {
                Text(
                    "Direct Bank Transfer matching is disabled in the current compliance profile until RBI regulatory review confirms the permissible exchange methods."
                )
            },
            confirmButton = {
                Button(onClick = { showLegalNoticeForBank = false }) {
                    Text("Understood")
                }
            }
        )
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(92.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun RequestSummaryCard(
    request: ExchangeRequest,
    isOwnRequest: Boolean,
    hasExpressedInterest: Boolean,
    isAcceptedByReceiver: Boolean = false,
    receiverMarkedReceived: Boolean = false,
    evidenceCount: Int = 0,
    interestCount: Int,
    currentTimeEpochMs: Long = System.currentTimeMillis(),
    onMarkReceived: () -> Unit = {},
    onOpenProof: () -> Unit = {},
    onClick: () -> Unit
) {
    val revealIdentityAndLocation = if (isOwnRequest) {
        interestCount > 0 || request.selectedPartnerId != null
    } else {
        hasExpressedInterest
    }
    val remainingMillis = request.remainingMillis(currentTimeEpochMs)
    val countdownText = request.formatCountdown(currentTimeEpochMs)
    val remainingFraction = request.remainingFraction(currentTimeEpochMs)
    val isUrgent = remainingMillis <= 5 * 60_000L
    val deadlineClock = remember(request.deadlineEpochMs) {
        SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(request.deadlineEpochMs))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("request_card_${request.swapCode}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Text(
                        text = "WANT ${request.wantedMethod.shortLabel.uppercase()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    if (isAcceptedByReceiver) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Accepted",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                ExchangeStatusBadge(status = request.status)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = SecurityValidators.formatInr(request.amountInr),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Offering: ${request.offeredMethod.shortLabel} • Request ID: ${request.swapCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                VerifiedIndicatorChip(verified = request.creatorVerified)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isOwnRequest) {
                            if (revealIdentityAndLocation) {
                                val partnerText = request.selectedPartnerName ?: "$interestCount interest(s)"
                                "Active Request • $partnerText"
                            } else {
                                "Active Request • Awaiting interest"
                            }
                        } else {
                            if (revealIdentityAndLocation) {
                                "${request.creatorDisplayName} • Approx. ${request.approximateDistanceMeters} m"
                            } else {
                                "Distance: Approx. ${request.approximateDistanceMeters} m"
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (revealIdentityAndLocation) {
                            "Customer Score: ${request.creatorScore}/5 • ${request.areaLabel}"
                        } else {
                            "Customer Score: ${request.creatorScore}/5 • Name & location hidden until Interested"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (isUrgent) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.testTag("request_countdown_${request.swapCode}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = "Deadline countdown",
                                tint = if (isUrgent) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Ends in $countdownText • Until $deadlineClock",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isUrgent) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { remainingFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = if (isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.testTag("view_request_${request.swapCode}")
                ) {
                    Text("View Request")
                }
            }

            if (isAcceptedByReceiver) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onMarkReceived,
                        enabled = !receiverMarkedReceived && !request.isPastDeadline(currentTimeEpochMs),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f).testTag("card_received_${request.swapCode}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Received",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (receiverMarkedReceived) "Received ✓" else "Received")
                    }
                    OutlinedButton(
                        onClick = onOpenProof,
                        enabled = !request.isPastDeadline(currentTimeEpochMs),
                        modifier = Modifier.weight(1f).testTag("card_proof_${request.swapCode}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = "Add Proof",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (evidenceCount == 0) "Add Proof" else "Proof ($evidenceCount)")
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateRequestModal(
    initialWanted: PaymentMethod,
    remainingDailyInr: Int,
    onDismiss: () -> Unit,
    onSubmit: (
        wanted: PaymentMethod,
        offered: PaymentMethod,
        amount: Int,
        radius: SearchRadius,
        expiryMinutes: Int
    ) -> Unit
) {
    var wanted by remember { mutableStateOf(initialWanted) }
    var offered by remember {
        mutableStateOf(if (initialWanted == PaymentMethod.CASH) PaymentMethod.UPI else PaymentMethod.CASH)
    }
    var amountText by remember { mutableStateOf("200") }
    var radius by remember { mutableStateOf(SearchRadius.RADIUS_1KM) }
    var expiryMinutes by remember { mutableIntStateOf(60) }

    val parsedAmount = amountText.toIntOrNull() ?: 0
    val validationError = SecurityValidators.validateRequestAmount(
        amountInr = parsedAmount,
        usedTodayInr = SecurityValidators.DAILY_LIMIT_INR - remainingDailyInr,
        wantedMethod = wanted,
        offeredMethod = offered
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Exchange Request", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Remaining IST Daily Allowance: ${SecurityValidators.formatInr(remainingDailyInr)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
                Text("What do you need?", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PaymentMethod.CASH, PaymentMethod.UPI).forEach { m ->
                        FilterChip(
                            selected = wanted == m,
                            onClick = {
                                wanted = m
                                offered = if (m == PaymentMethod.CASH) PaymentMethod.UPI else PaymentMethod.CASH
                            },
                            label = { Text("WANT ${m.shortLabel.uppercase()}") }
                        )
                    }
                }
                Text(
                    text = "You will offer: ${offered.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Quick Amounts", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(200, 500, 1000, 2000).filter { it <= remainingDailyInr.coerceAtLeast(200) }) { preset ->
                        FilterChip(
                            selected = parsedAmount == preset,
                            onClick = { amountText = preset.toString() },
                            label = { Text(SecurityValidators.formatInr(preset)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("Amount (₹1 - ₹5,000)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_req_amount_input")
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Your address is never shared when creating a request. The person who accepts provides the meeting spot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Text("Preferred Search Radius", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SearchRadius.entries.forEach { r ->
                        FilterChip(
                            selected = radius == r,
                            onClick = { radius = r },
                            label = { Text(r.label) }
                        )
                    }
                }
                Text("Request Deadline", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 60, 120).forEach { mins ->
                        FilterChip(
                            selected = expiryMinutes == mins,
                            onClick = { expiryMinutes = mins },
                            label = { Text("$mins min") }
                        )
                    }
                }
                if (validationError != null) {
                    Text(
                        text = validationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(wanted, offered, parsedAmount, radius, expiryMinutes) },
                enabled = validationError == null,
                modifier = Modifier.testTag("create_req_submit_button")
            ) {
                Text("Create Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun NotificationsHistorySheet(
    notifications: List<InAppNotification>,
    requests: List<ExchangeRequest>,
    interests: List<RequestInterest>,
    evidenceItems: List<EvidenceItem>,
    activeUserId: String,
    currentTimeEpochMs: Long,
    onAccept: (notificationId: String, acceptorAddress: String) -> Unit,
    onIgnore: (String) -> Unit,
    onMarkReceived: (String) -> Unit,
    onOpenProof: (String) -> Unit,
    onOpenRequest: (String) -> Unit
) {
    var pendingAddressNotifId by remember { mutableStateOf<String?>(null) }
    var addressInput by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Notifications",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Privacy-safe previews • Accept or ignore incoming requests",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (notifications.isEmpty()) {
            FriendlyEmptyStateCard(
                icon = Icons.Outlined.NotificationsNone,
                title = "You're all caught up",
                subtitle = "New nearby swap requests, partner confirmations, and deadline reminders will appear here."
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications.take(20), key = { it.id }) { n ->
                    val linkedRequest = n.linkedRequestId?.let { id ->
                        requests.firstOrNull { it.id == id }
                    }
                    val displayAmountInr = n.amountInr ?: linkedRequest?.amountInr
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = n.category.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = n.timestampLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (displayAmountInr != null) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.testTag("notif_amount_${n.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = SecurityValidators.formatInr(displayAmountInr),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        if (linkedRequest != null) {
                                            Text(
                                                text = "• Want ${linkedRequest.wantedMethod.shortLabel} • Offer ${linkedRequest.offeredMethod.shortLabel}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                            if (linkedRequest != null) {
                                val isExpired = linkedRequest.status == ExchangeStatus.EXPIRED ||
                                    linkedRequest.isPastDeadline(currentTimeEpochMs)
                                val deadlineClock = remember(linkedRequest.deadlineEpochMs) {
                                    SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(linkedRequest.deadlineEpochMs))
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.testTag("notif_deadline_${n.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Timer,
                                        contentDescription = "Deadline",
                                        tint = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isExpired) {
                                            "Deadline passed ($deadlineClock)"
                                        } else {
                                            "Deadline: $deadlineClock (${linkedRequest.formatCountdown(currentTimeEpochMs)} left)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(text = n.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = n.safePreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (n.linkedRequestId != null) {
                                when (n.actionStatus) {
                                    NotificationActionStatus.PENDING -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { onIgnore(n.id) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("notif_ignore_${n.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Close,
                                                    contentDescription = "Ignore",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("Ignore")
                                            }
                                            Button(
                                                onClick = {
                                                    if (n.category == com.example.domain.NotificationCategory.NEARBY_REQUEST) {
                                                        addressInput = ""
                                                        pendingAddressNotifId = n.id
                                                    } else {
                                                        onAccept(n.id, "")
                                                    }
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("notif_accept_${n.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Check,
                                                    contentDescription = "Accept",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("Accept")
                                            }
                                        }
                                    }
                                    NotificationActionStatus.ACCEPTED -> {
                                        val isReceiver = linkedRequest != null && linkedRequest.creatorId != activeUserId
                                        val myInterest = interests.firstOrNull {
                                            it.requestId == n.linkedRequestId && it.interestedUserId == activeUserId
                                        }
                                        val alreadyReceived = (myInterest?.receiverMarkedReceived == true) ||
                                            (linkedRequest?.selectedPartnerId == activeUserId && linkedRequest.partnerConfirmedProvided == true)
                                        val proofCount = evidenceItems.count { it.requestId == n.linkedRequestId }
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Accepted",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                TextButton(onClick = { onOpenRequest(n.linkedRequestId) }) {
                                                    Text("Open Request")
                                                }
                                            }
                                            if (isReceiver) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = { onMarkReceived(n.linkedRequestId) },
                                                        enabled = !alreadyReceived,
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.secondary
                                                        ),
                                                        modifier = Modifier.weight(1f).testTag("notif_received_${n.id}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.CheckCircle,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(if (alreadyReceived) "Received ✓" else "Received")
                                                    }
                                                    OutlinedButton(
                                                        onClick = { onOpenProof(n.linkedRequestId) },
                                                        modifier = Modifier.weight(1f).testTag("notif_proof_${n.id}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.AttachFile,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(if (proofCount == 0) "Add Proof" else "Proof ($proofCount)")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    NotificationActionStatus.IGNORED -> {
                                        Text(
                                            text = "Ignored",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    pendingAddressNotifId?.let { notifId ->
        AlertDialog(
            onDismissRequest = { pendingAddressNotifId = null },
            title = { Text("Provide Exchange Address", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The request creator does not share their address. As the request acceptor, enter or pick a safe public meeting spot:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("Indiranagar Metro Gate B", "CMH Road Coffee Point", "100 Ft Rd Main Corner")) { spot ->
                            AssistChip(
                                onClick = { addressInput = spot },
                                label = { Text(spot, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        label = { Text("Meeting / Public Area Address") },
                        placeholder = { Text("e.g. Indiranagar Metro Gate B") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAccept(notifId, addressInput.trim())
                        pendingAddressNotifId = null
                    },
                    enabled = addressInput.trim().isNotEmpty()
                ) {
                    Text("Accept & Submit Address")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAddressNotifId = null }) { Text("Cancel") }
            }
        )
    }
}
