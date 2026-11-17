package com.example.ui.exchange

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.ApprovalDecision
import com.example.domain.ExchangeRequest
import com.example.domain.ExchangeStatus
import com.example.domain.RequestInterest
import com.example.domain.SecurityValidators
import com.example.ui.SwapMoneyUiState
import com.example.ui.components.ExchangeStatusBadge
import com.example.ui.components.ExchangeWorkflowStepper
import com.example.ui.components.OfflineStatusBanner
import com.example.ui.components.VerifiedIndicatorChip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeDetailScreen(
    requestId: String,
    uiState: SwapMoneyUiState,
    onBack: () -> Unit,
    onSubmitInterest: (requestId: String, acceptorAddress: String) -> Unit,
    onSendChatMessage: (String, String) -> Unit,
    onBlockUser: (String) -> Unit,
    onReportUser: (String, String) -> Unit,
    onStage1Decision: (interestId: String, approve: Boolean) -> Unit,
    onStage2ConfirmPartner: (interestId: String) -> Unit,
    onGrantConsent: (String) -> Unit,
    onProposeMeeting: (requestId: String, location: String, timeLabel: String) -> Unit,
    onApproveMeeting: (String) -> Unit,
    onSubmitEvidence: (requestId: String, refNo: String, uri: String?, mime: String?, sizeBytes: Long) -> Unit,
    onMarkReceiverReceived: (requestId: String) -> Unit,
    onConfirmExchange: (requestId: String, confirmedSuccess: Boolean, disputeNote: String) -> Unit,
    onCancelRequest: (String) -> Unit,
    onSubmitRating: (String, Int, Int, Int, Int, String) -> Unit
) {
    BackHandler { onBack() }

    val activeUser = uiState.activeUser ?: return
    val request = uiState.allRequests.firstOrNull { it.id == requestId }
    if (request == null) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Text("Request not found.", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onBack) { Text("Back to Home") }
        }
        return
    }

    val isCreator = request.creatorId == activeUser.id
    val isSelectedPartner = request.selectedPartnerId == activeUser.id
    val requestInterests = uiState.interests.filter { it.requestId == request.id }
    val myInterest = requestInterests.firstOrNull { it.interestedUserId == activeUser.id }

    var activeChatInterest by remember { mutableStateOf<RequestInterest?>(null) }
    var stage2ConfirmTarget by remember { mutableStateOf<RequestInterest?>(null) }
    var showEvidenceDialog by remember { mutableStateOf(false) }
    var showDisputeDialog by remember { mutableStateOf(false) }
    var showRatingDialog by remember { mutableStateOf(false) }
    var showAcceptorAddressDialog by remember { mutableStateOf(false) }

    val requestEvidence = uiState.evidenceItems.filter { it.requestId == request.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("exchange_back_button")) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Exchange ${request.swapCode}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${request.wantedMethod.shortLabel} <-> ${request.offeredMethod.shortLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ExchangeStatusBadge(status = request.status)
            }
        }

        item {
            OfflineStatusBanner(
                isOnline = uiState.isOnline,
                pendingCount = uiState.pendingSyncOps.size
            )
        }

        // Request Header Summary Card
        item {
            Card(
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
                        Text(
                            text = SecurityValidators.formatInr(request.amountInr),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                        VerifiedIndicatorChip(verified = request.creatorVerified)
                    }
                    val revealIdentityAndLocation = if (isCreator) {
                        requestInterests.isNotEmpty() || request.selectedPartnerId != null
                    } else {
                        myInterest != null
                    }
                    Text(
                        text = "Creator wants ${request.wantedMethod.label} • Offering ${request.offeredMethod.label}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (revealIdentityAndLocation) {
                            "Area: ${request.areaLabel} • Radius ${request.searchRadius.label} • Approx. ${request.approximateDistanceMeters} m"
                        } else {
                            "Location: Hidden until Interested • Radius ${request.searchRadius.label} • Approx. ${request.approximateDistanceMeters} m"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (revealIdentityAndLocation) {
                            "Creator: ${request.creatorDisplayName} • Score ${request.creatorScore}/5 (${request.creatorSuccessfulCount} swaps)"
                        } else {
                            "Name: Hidden until Interested • Score ${request.creatorScore}/5 (${request.creatorSuccessfulCount} swaps)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    val isExpired = request.status == ExchangeStatus.EXPIRED ||
                        request.isPastDeadline(uiState.currentTimeEpochMs)
                    val remainingMillis = request.remainingMillis(uiState.currentTimeEpochMs)
                    val countdownText = request.formatCountdown(uiState.currentTimeEpochMs)
                    val remainingFraction = request.remainingFraction(uiState.currentTimeEpochMs)
                    val isUrgent = isExpired || remainingMillis <= 5 * 60_000L
                    val deadlineClock = remember(request.deadlineEpochMs) {
                        SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(request.deadlineEpochMs))
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (isUrgent) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.fillMaxWidth().testTag("detail_deadline_countdown")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Timer,
                                    contentDescription = "Deadline countdown",
                                    tint = if (isUrgent) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isExpired) {
                                        "Expired • Deadline $deadlineClock passed (00:00)"
                                    } else {
                                        "Time Left: $countdownText • Ends at $deadlineClock"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUrgent) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                )
                            }
                            LinearProgressIndicator(
                                progress = { if (isExpired) 0f else remainingFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(CircleShape),
                                color = if (isUrgent) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    if (!isCreator && myInterest == null && request.selectedPartnerId == null && !isExpired) {
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = { showAcceptorAddressDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("im_interested_button")
                        ) {
                            Text("I'M INTERESTED")
                        }
                    } else if (!isCreator && myInterest != null) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { activeChatInterest = myInterest },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_my_chat_button")
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open Restricted Chat with Creator")
                        }
                    }
                }
            }
        }

        // Visual 4-Step Progress Guide
        item {
            val mutualConsent = request.creatorConsentGranted && request.partnerConsentGranted
            val meetingReady = request.meetingCreatorApproved && request.meetingPartnerApproved
            val completedAll = request.status == ExchangeStatus.CUSTOMER_REPORTED_COMPLETION ||
                request.status == ExchangeStatus.COMPLETED_VERIFIED
            val currentStep = when {
                request.selectedPartnerId == null -> 0
                !mutualConsent -> 1
                !meetingReady -> 2
                else -> 3
            }
            val nextHint = when {
                request.status == ExchangeStatus.EXPIRED || request.isPastDeadline(uiState.currentTimeEpochMs) ->
                    "This request has passed its deadline and can no longer be modified."
                request.status == ExchangeStatus.CANCELLED ->
                    "This request was cancelled."
                completedAll ->
                    "Both customers confirmed completion. You can rate your exchange partner below."
                request.selectedPartnerId == null && isCreator ->
                    if (requestInterests.isEmpty()) {
                        "Next: Wait for a nearby customer to tap I'm Interested and provide a meeting spot."
                    } else {
                        "Next: Approve an interested customer (Stage 1), then confirm your partner (Stage 2)."
                    }
                request.selectedPartnerId == null && myInterest == null ->
                    "Next: Tap I'm Interested above and enter a safe public meeting address."
                request.selectedPartnerId == null ->
                    "Next: Waiting for the creator to confirm you as partner. You can upload proof or mark Received if already completed."
                !mutualConsent ->
                    "Next: Both sides must grant Contact-Sharing Consent below before coordinating."
                !meetingReady ->
                    if (isCreator) {
                        "Next: Review and approve the acceptor's meeting address below."
                    } else {
                        "Next: Submit or confirm your public meeting address & time below."
                    }
                else ->
                    "Next: Meet at the agreed spot, upload proof, and confirm receipt to complete."
            }
            ExchangeWorkflowStepper(
                currentStep = currentStep,
                completedAll = completedAll,
                nextStepHint = nextHint
            )
        }

        // Creator's Interested Customers List & Mandatory Two-Stage Approval
        if (isCreator && request.selectedPartnerId == null && request.status != ExchangeStatus.CANCELLED) {
            item {
                Text(
                    text = "Interested Customers (${requestInterests.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (requestInterests.isEmpty()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No nearby customers have submitted interest yet. Switch Sandbox Actor to Priya or Rohan to test multi-party interest!",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(requestInterests, key = { it.id }) { interest ->
                    InterestCandidateCard(
                        interest = interest,
                        onOpenChat = { activeChatInterest = interest },
                        onApproveStage1 = { onStage1Decision(interest.id, true) },
                        onRejectStage1 = { onStage1Decision(interest.id, false) },
                        onOpenStage2Confirm = { stage2ConfirmTarget = interest }
                    )
                }
            }
        }

        // Rejected user notice
        if (!isCreator && request.selectedPartnerId != null && !isSelectedPartner) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Another partner was confirmed for this request. Contact and meeting details remain strictly private.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        // Accepted Request Receiver Actions: Deadline, Received Option, & Proof Upload
        if (!isCreator && (myInterest != null || isSelectedPartner)) {
            val alreadyReceived = (myInterest?.receiverMarkedReceived == true) ||
                (request.partnerConfirmedProvided == true)
            val preConfirmationMode = !(isSelectedPartner &&
                request.creatorConsentGranted &&
                request.partnerConsentGranted &&
                request.meetingCreatorApproved &&
                request.meetingPartnerApproved)
            if (preConfirmationMode && request.status != ExchangeStatus.CANCELLED) {
                item {
                    ReceiverAcceptedActionsCard(
                        request = request,
                        currentTimeEpochMs = uiState.currentTimeEpochMs,
                        alreadyReceived = alreadyReceived,
                        evidenceItems = requestEvidence,
                        onMarkReceived = { onMarkReceiverReceived(request.id) },
                        onOpenProof = { showEvidenceDialog = true }
                    )
                }
            }
        }

        // Stage-2 Confirmed Participants Workflow: Mutual Consent, Meeting, Evidence, Confirmation
        if ((isCreator || isSelectedPartner) && request.selectedPartnerId != null) {
            val partnerAccount = uiState.allSandboxAccounts.firstOrNull {
                it.id == if (isCreator) request.selectedPartnerId else request.creatorId
            }
            val mutualConsentGranted = request.creatorConsentGranted && request.partnerConsentGranted

            // 1. Contact-Sharing Consent Card
            item {
                ContactConsentSection(
                    request = request,
                    isCreator = isCreator,
                    mutualConsentGranted = mutualConsentGranted,
                    partnerMobile = partnerAccount?.mobileNumber,
                    partnerEmail = partnerAccount?.email,
                    onGrantConsent = { onGrantConsent(request.id) }
                )
            }

            // 2. Meeting Location Coordination
            if (mutualConsentGranted) {
                item {
                    MeetingCoordinationSection(
                        request = request,
                        isCreator = isCreator,
                        onProposeMeeting = { loc, time -> onProposeMeeting(request.id, loc, time) },
                        onApproveMeeting = { onApproveMeeting(request.id) }
                    )
                }
            }

            // 3. Independent Customer Confirmation & Evidence
            if (mutualConsentGranted && request.meetingCreatorApproved && request.meetingPartnerApproved) {
                item {
                    ExchangeConfirmationSection(
                        request = request,
                        isCreator = isCreator,
                        evidenceCount = requestEvidence.size,
                        onConfirmSuccess = { onConfirmExchange(request.id, true, "") },
                        onReportDispute = { showDisputeDialog = true },
                        onOpenEvidence = { showEvidenceDialog = true },
                        onOpenRating = { showRatingDialog = true }
                    )
                }
            }
        }

        // Cancel Option before completion
        if ((isCreator || isSelectedPartner) &&
            request.status !in setOf(
                ExchangeStatus.CUSTOMER_REPORTED_COMPLETION,
                ExchangeStatus.COMPLETED_VERIFIED,
                ExchangeStatus.CANCELLED,
                ExchangeStatus.DISPUTED
            )
        ) {
            item {
                OutlinedButton(
                    onClick = { onCancelRequest(request.id) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().testTag("cancel_exchange_button")
                ) {
                    Text("Cancel Exchange")
                }
            }
        }
    }

    // Separate Stage 2 Confirmation Screen / Modal
    stage2ConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { stage2ConfirmTarget = null },
            title = {
                Text("CONFIRM EXCHANGE PARTNER", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stage 2 Final Partner Confirmation (Distinct Mandatory Step)")
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Customer: ${target.displayName} (${target.interestedSwuId})", fontWeight = FontWeight.SemiBold)
                            Text("Amount: ${SecurityValidators.formatInr(request.amountInr)}")
                            Text("Exchange: ${request.wantedMethod.shortLabel} <-> ${request.offeredMethod.shortLabel}")
                            Text("Score: ${target.customerScore}/5 • Approx. ${target.approximateDistanceMeters} m")
                        }
                    }
                    Text(
                        text = "Only one partner can be locked. Other interested users will be closed automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onStage2ConfirmPartner(target.id)
                        stage2ConfirmTarget = null
                    },
                    modifier = Modifier.testTag("stage2_confirm_partner_button")
                ) {
                    Text("CONFIRM PARTNER")
                }
            },
            dismissButton = {
                TextButton(onClick = { stage2ConfirmTarget = null }) {
                    Text("CANCEL")
                }
            }
        )
    }

    // Restricted Chat Modal Bottom Sheet
    activeChatInterest?.let { interest ->
        val partnerId = if (isCreator) interest.interestedUserId else request.creatorId
        val partnerName = if (isCreator) interest.displayName else request.creatorDisplayName
        val isBlocked = (activeUser.id to partnerId) in uiState.blockedPairs ||
            (partnerId to activeUser.id) in uiState.blockedPairs
        ModalBottomSheet(onDismissRequest = { activeChatInterest = null }) {
            RestrictedChatSheetContent(
                partnerName = partnerName,
                activeUserId = activeUser.id,
                isBlocked = isBlocked,
                messages = uiState.messagesByConversation[interest.conversationId].orEmpty(),
                onSendMessage = { text -> onSendChatMessage(interest.conversationId, text) },
                onBlockPartner = {
                    onBlockUser(partnerId)
                    activeChatInterest = null
                },
                onReportPartner = { reason -> onReportUser(partnerId, reason) }
            )
        }
    }

    // Evidence Upload Dialog (Photo Picker)
    if (showEvidenceDialog) {
        EvidenceUploadDialog(
            onDismiss = { showEvidenceDialog = false },
            onSubmit = { ref, uri, mime, size ->
                onSubmitEvidence(request.id, ref, uri, mime, size)
                showEvidenceDialog = false
            }
        )
    }

    // Dispute Dialog
    if (showDisputeDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDisputeDialog = false },
            title = { Text("Initiate Exchange Dispute") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Describe the discrepancy or non-receipt") },
                    modifier = Modifier.fillMaxWidth().testTag("dispute_reason_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirmExchange(request.id, false, reason)
                        showDisputeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("submit_dispute_button")
                ) {
                    Text("File Dispute")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisputeDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Post-Completion Customer Rating Dialog
    if (showRatingDialog) {
        RatingSubmissionDialog(
            onDismiss = { showRatingDialog = false },
            onSubmit = { comm, punct, coop, overall, comment ->
                onSubmitRating(request.id, comm, punct, coop, overall, comment)
                showRatingDialog = false
            }
        )
    }

    if (showAcceptorAddressDialog) {
        var acceptorAddress by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAcceptorAddressDialog = false },
            title = { Text("Provide Exchange Address", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The request creator does not provide an address. As the request acceptor, enter or pick a safe public meeting spot:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("Indiranagar Metro Gate B", "CMH Road Coffee Point", "100 Ft Rd Main Corner")) { spot ->
                            AssistChip(
                                onClick = { acceptorAddress = spot },
                                label = { Text(spot, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = acceptorAddress,
                        onValueChange = { acceptorAddress = it },
                        label = { Text("Meeting / Public Area Address") },
                        placeholder = { Text("e.g. Indiranagar Metro Gate B") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("acceptor_address_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSubmitInterest(request.id, acceptorAddress.trim())
                        showAcceptorAddressDialog = false
                    },
                    enabled = acceptorAddress.trim().isNotEmpty(),
                    modifier = Modifier.testTag("submit_acceptor_interest_button")
                ) {
                    Text("Submit Interest & Address")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAcceptorAddressDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun InterestCandidateCard(
    interest: RequestInterest,
    onOpenChat: () -> Unit,
    onApproveStage1: () -> Unit,
    onRejectStage1: () -> Unit,
    onOpenStage2Confirm: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(interest.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Score ${interest.customerScore}/5 • ${interest.successfulExchanges} swaps • ~${interest.approximateDistanceMeters} m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!interest.acceptorProvidedAddress.isNullOrBlank()) {
                        Text(
                            text = "Acceptor Address: ${interest.acceptorProvidedAddress}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (interest.receiverMarkedReceived) {
                        Text(
                            text = "Receiver marked exchange as Received",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                VerifiedIndicatorChip(verified = interest.verified)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenChat,
                    modifier = Modifier.weight(1f).testTag("chat_interest_${interest.interestedSwuId}")
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chat")
                }
                when (interest.stage1Status) {
                    ApprovalDecision.PENDING -> {
                        OutlinedButton(
                            onClick = onRejectStage1,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f).testTag("stage1_reject_${interest.interestedSwuId}")
                        ) {
                            Text("Reject")
                        }
                        Button(
                            onClick = onApproveStage1,
                            modifier = Modifier.weight(1f).testTag("stage1_approve_${interest.interestedSwuId}")
                        ) {
                            Text("Approve Interest")
                        }
                    }
                    ApprovalDecision.APPROVED -> {
                        Button(
                            onClick = onOpenStage2Confirm,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1.5f).testTag("open_stage2_${interest.interestedSwuId}")
                        ) {
                            Text("Stage 2: Confirm Partner")
                        }
                    }
                    ApprovalDecision.REJECTED -> {
                        Text(
                            text = "Rejected",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactConsentSection(
    request: ExchangeRequest,
    isCreator: Boolean,
    mutualConsentGranted: Boolean,
    partnerMobile: String?,
    partnerEmail: String?,
    onGrantConsent: () -> Unit
) {
    val myConsent = if (isCreator) request.creatorConsentGranted else request.partnerConsentGranted

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Handshake, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Contact-Sharing Consent", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "Creator Consent: ${if (request.creatorConsentGranted) "Granted" else "Pending"} • Partner Consent: ${if (request.partnerConsentGranted) "Granted" else "Pending"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (mutualConsentGranted) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Permitted Contact Details Released",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Mobile: ${partnerMobile ?: "Verified"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Email: ${partnerEmail ?: "Verified"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Residential addresses remain strictly confidential.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            } else if (!myConsent) {
                Button(
                    onClick = onGrantConsent,
                    modifier = Modifier.fillMaxWidth().testTag("grant_contact_consent_button")
                ) {
                    Text("Grant Contact-Sharing Consent")
                }
            }
        }
    }
}

@Composable
private fun MeetingCoordinationSection(
    request: ExchangeRequest,
    isCreator: Boolean,
    onProposeMeeting: (String, String) -> Unit,
    onApproveMeeting: () -> Unit
) {
    var locationInput by remember(request.meetingLocation) {
        mutableStateOf(request.meetingLocation ?: "")
    }
    var timeInput by remember(request.meetingTimeLabel) {
        mutableStateOf(request.meetingTimeLabel ?: "Today, 5:30 PM")
    }
    val myApproved = if (isCreator) request.meetingCreatorApproved else request.meetingPartnerApproved
    val bothApproved = request.meetingCreatorApproved && request.meetingPartnerApproved

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Meeting Address Coordination", style = MaterialTheme.typography.titleMedium)
            }
            if (bothApproved && request.meetingLocation != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Agreed Acceptor Address: ${request.meetingLocation}", fontWeight = FontWeight.Bold)
                        Text("Agreed Time: ${request.meetingTimeLabel ?: "Confirmed"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else if (isCreator) {
                if (request.meetingLocation != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Address Provided by Acceptor:", style = MaterialTheme.typography.labelSmall)
                            Text(request.meetingLocation, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Time: ${request.meetingTimeLabel ?: "Flexible"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (!myApproved) {
                        Button(
                            onClick = onApproveMeeting,
                            modifier = Modifier.fillMaxWidth().testTag("approve_meeting_button")
                        ) {
                            Text("Approve Acceptor Address")
                        }
                    } else {
                        Text(
                            text = "You approved this address. Waiting for partner confirmation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    Text(
                        text = "Waiting for the request acceptor to provide the meeting address.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "As the request acceptor, provide or pick a safe public meeting spot & time:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("Indiranagar Metro Gate B", "CMH Road Coffee Point", "100 Ft Rd Main Corner")) { spot ->
                        AssistChip(
                            onClick = { locationInput = spot },
                            label = { Text(spot, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = locationInput,
                    onValueChange = { locationInput = it },
                    label = { Text("Meeting Address (Provided by Acceptor)") },
                    placeholder = { Text("e.g. Indiranagar Metro Gate B") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("meeting_location_input")
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("In 15 min", "In 30 min", "Today, 5:30 PM", "Today, 6:30 PM")) { t ->
                        AssistChip(
                            onClick = { timeInput = t },
                            label = { Text(t, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = timeInput,
                    onValueChange = { timeInput = it },
                    label = { Text("Meeting Time") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("meeting_time_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onProposeMeeting(locationInput, timeInput) },
                        enabled = locationInput.isNotBlank() && timeInput.isNotBlank(),
                        modifier = Modifier.weight(1f).testTag("propose_meeting_button")
                    ) {
                        Text("Submit Address")
                    }
                    if (request.meetingLocation != null && !myApproved) {
                        OutlinedButton(
                            onClick = onApproveMeeting,
                            modifier = Modifier.weight(1f).testTag("approve_meeting_button")
                        ) {
                            Text("Confirm Spot")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExchangeConfirmationSection(
    request: ExchangeRequest,
    isCreator: Boolean,
    evidenceCount: Int,
    onConfirmSuccess: () -> Unit,
    onReportDispute: () -> Unit,
    onOpenEvidence: () -> Unit,
    onOpenRating: () -> Unit
) {
    val myConfirmation = if (isCreator) request.creatorConfirmedReceived else request.partnerConfirmedProvided
    val isCompleted = request.status == ExchangeStatus.CUSTOMER_REPORTED_COMPLETION ||
        request.status == ExchangeStatus.COMPLETED_VERIFIED

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text("Independent Exchange Confirmation", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "Swap Money does not execute payments. Both customers must independently confirm external completion.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Creator Receipt Confirmed: ${request.creatorConfirmedReceived?.toString() ?: "Pending"} • Partner Delivery Confirmed: ${request.partnerConfirmedProvided?.toString() ?: "Pending"}",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "Uploaded Encrypted Evidence Items: $evidenceCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )

            OutlinedButton(
                onClick = onOpenEvidence,
                modifier = Modifier.fillMaxWidth().testTag("attach_evidence_button")
            ) {
                Icon(Icons.Outlined.AttachFile, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add Proof (Ref # or Screenshot)")
            }

            if (myConfirmation == null && request.status != ExchangeStatus.DISPUTED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onConfirmSuccess,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f).testTag("confirm_exchange_success_button")
                    ) {
                        Text(if (isCreator) "Confirm Received" else "Received")
                    }
                    OutlinedButton(
                        onClick = onReportDispute,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f).testTag("open_dispute_button")
                    ) {
                        Icon(Icons.Outlined.ReportProblem, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Dispute")
                    }
                }
            }

            if (isCompleted) {
                Button(
                    onClick = onOpenRating,
                    modifier = Modifier.fillMaxWidth().testTag("rate_partner_button")
                ) {
                    Icon(Icons.Outlined.Star, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Rate Exchange Partner")
                }
            }
        }
    }
}

@Composable
private fun ReceiverAcceptedActionsCard(
    request: ExchangeRequest,
    currentTimeEpochMs: Long,
    alreadyReceived: Boolean,
    evidenceItems: List<com.example.domain.EvidenceItem>,
    onMarkReceived: () -> Unit,
    onOpenProof: () -> Unit
) {
    val isExpired = request.status == ExchangeStatus.EXPIRED || request.isPastDeadline(currentTimeEpochMs)
    val countdownText = request.formatCountdown(currentTimeEpochMs)
    val deadlineClock = remember(request.deadlineEpochMs) {
        SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(request.deadlineEpochMs))
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Accepted Request • Receiver Actions", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (isExpired) {
                    "Deadline passed ($deadlineClock)"
                } else {
                    "Deadline: $deadlineClock ($countdownText remaining)"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onMarkReceived,
                    enabled = !alreadyReceived && !isExpired,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.weight(1f).testTag("receiver_received_button")
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (alreadyReceived) "Received ✓" else "Received")
                }
                OutlinedButton(
                    onClick = onOpenProof,
                    enabled = !isExpired,
                    modifier = Modifier.weight(1f).testTag("receiver_add_proof_button")
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (evidenceItems.isEmpty()) "Add Proof" else "Proof (${evidenceItems.size})")
                }
            }
            if (evidenceItems.isNotEmpty()) {
                evidenceItems.forEach { item ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Proof: ${item.referenceNumber} • ${item.timestampLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EvidenceUploadDialog(
    onDismiss: () -> Unit,
    onSubmit: (ref: String, uri: String?, mime: String?, sizeBytes: Long) -> Unit
) {
    val context = LocalContext.current
    var refNo by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedMime by remember { mutableStateOf<String?>(null) }
    var selectedSize by remember { mutableStateOf(0L) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedMime = context.contentResolver.getType(uri) ?: "image/jpeg"
            selectedSize = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && idx >= 0) cursor.getLong(idx) else 240_000L
            } ?: 240_000L
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit Encrypted Evidence") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Never enter OTPs, UPI PINs, passwords, or full card numbers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedTextField(
                    value = refNo,
                    onValueChange = { refNo = it },
                    label = { Text("Transaction Reference Number (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("evidence_ref_input")
                )
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (selectedUri != null) "Screenshot Selected" else "Choose Screenshot (Photo Picker)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(refNo, selectedUri?.toString(), selectedMime, selectedSize) },
                enabled = refNo.isNotBlank() || selectedUri != null,
                modifier = Modifier.testTag("submit_evidence_confirm")
            ) {
                Text("Upload Evidence")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RatingSubmissionDialog(
    onDismiss: () -> Unit,
    onSubmit: (Int, Int, Int, Int, String) -> Unit
) {
    var comm by remember { mutableIntStateOf(5) }
    var punct by remember { mutableIntStateOf(5) }
    var coop by remember { mutableIntStateOf(5) }
    var overall by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate Exchange Partner") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RatingAxisSlider("Communication", comm) { comm = it }
                RatingAxisSlider("Punctuality", punct) { punct = it }
                RatingAxisSlider("Cooperation", coop) { coop = it }
                RatingAxisSlider("Overall Experience", overall) { overall = it }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Feedback note") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(comm, punct, coop, overall, comment) }) {
                Text("Submit Rating")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RatingAxisSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("$value / 5", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(1, 5)) },
            valueRange = 1f..5f,
            steps = 3
        )
    }
}
