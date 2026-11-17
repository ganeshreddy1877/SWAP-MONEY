package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.RepoResult
import com.example.data.repository.SwapMoneyRepository
import com.example.domain.ActiveSession
import com.example.domain.AppPreferences
import com.example.domain.BackendMode
import com.example.domain.ChatMessage
import com.example.domain.CustomerRatingStats
import com.example.domain.EvidenceItem
import com.example.domain.ExchangeRequest
import com.example.domain.HistoryEntry
import com.example.domain.InAppNotification
import com.example.domain.LoginEvent
import com.example.domain.MfaActionType
import com.example.domain.PaymentMethod
import com.example.domain.PendingSyncOperation
import com.example.domain.RegisteredDevice
import com.example.domain.RequestInterest
import com.example.domain.SearchRadius
import com.example.domain.SecurityValidators
import com.example.domain.SwapActivityStats
import com.example.domain.UserAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SwapMoneyUiState(
    val activeUser: UserAccount? = null,
    val allSandboxAccounts: List<UserAccount> = emptyList(),
    val preferences: AppPreferences = AppPreferences(),
    val isOnline: Boolean = true,
    val pendingSyncOps: List<PendingSyncOperation> = emptyList(),
    val dailyUsedInr: Int = 0,
    val dailyRemainingInr: Int = SecurityValidators.DAILY_LIMIT_INR,
    val nearbyRequests: List<ExchangeRequest> = emptyList(),
    val myActiveRequests: List<ExchangeRequest> = emptyList(),
    val allRequests: List<ExchangeRequest> = emptyList(),
    val interests: List<RequestInterest> = emptyList(),
    val messagesByConversation: Map<String, List<ChatMessage>> = emptyMap(),
    val blockedPairs: Set<Pair<String, String>> = emptySet(),
    val evidenceItems: List<EvidenceItem> = emptyList(),
    val historyEntries: List<HistoryEntry> = emptyList(),
    val notifications: List<InAppNotification> = emptyList(),
    val unreadNotificationsCount: Int = 0,
    val devices: List<RegisteredDevice> = emptyList(),
    val sessions: List<ActiveSession> = emptyList(),
    val loginEvents: List<LoginEvent> = emptyList(),
    val activityStats: SwapActivityStats? = null,
    val ratingStats: CustomerRatingStats? = null,
    val currentTimeEpochMs: Long = System.currentTimeMillis(),
    val sandboxOtpHint: String? = null,
    val statusBannerMessage: String? = null,
    val isErrorBanner: Boolean = false
)

class SwapMoneyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SwapMoneyRepository(application)

    private val _transientMessage = MutableStateFlow<Pair<String?, Boolean>>(null to false)
    private val _sandboxOtpHint = MutableStateFlow<String?>(null)
    private val _nowEpochMs = MutableStateFlow(System.currentTimeMillis())

    init {
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                _nowEpochMs.value = now
                repository.expireOverdueRequests(now)
                kotlinx.coroutines.delay(1_000L)
            }
        }
    }

    val uiState: StateFlow<SwapMoneyUiState> = combine(
        repository.worldState,
        repository.preferencesStore.preferencesFlow,
        repository.connectivityMonitor.isOnline,
        repository.pendingSyncOperations,
        combine(_transientMessage, _sandboxOtpHint, _nowEpochMs) { msg, otp, nowMs ->
            Triple(msg, otp, nowMs)
        }
    ) { world, prefs, online, pendingSync, transient ->
        val nowMs = transient.third
        val activeUser = world.accounts.firstOrNull { it.id == world.activeUserId }
        val todayKey = SecurityValidators.currentIstDateKey()
        val usedToday = if (activeUser != null) {
            world.dailyUsedByUserAndDate[activeUser.id to todayKey] ?: 0
        } else 0
        val remaining = SecurityValidators.calculateRemainingDailyLimit(usedToday)

        val nearby = if (activeUser != null) {
            world.requests.filter { req ->
                !req.isTerminalStatus &&
                    !req.isPastDeadline(nowMs) &&
                    req.creatorId != activeUser.id &&
                    req.selectedPartnerId == null &&
                    req.approximateDistanceMeters <= req.searchRadius.meters
            }
        } else emptyList()

        val mine = if (activeUser != null) {
            world.requests.filter { req ->
                !req.isTerminalStatus &&
                    !req.isPastDeadline(nowMs) &&
                    (req.creatorId == activeUser.id || req.selectedPartnerId == activeUser.id)
            }
        } else emptyList()

        val notifs = if (activeUser != null) world.notificationsByUser[activeUser.id].orEmpty() else emptyList()

        SwapMoneyUiState(
            activeUser = activeUser,
            allSandboxAccounts = world.accounts,
            preferences = prefs,
            isOnline = online,
            pendingSyncOps = pendingSync,
            dailyUsedInr = usedToday,
            dailyRemainingInr = remaining,
            nearbyRequests = nearby,
            myActiveRequests = mine,
            allRequests = world.requests,
            interests = world.interests,
            messagesByConversation = world.messagesByConversation,
            blockedPairs = world.blockedPairs,
            evidenceItems = world.evidenceItems,
            historyEntries = world.historyEntries,
            notifications = notifs,
            unreadNotificationsCount = notifs.count { !it.isRead },
            devices = if (activeUser != null) world.devicesByUser[activeUser.id].orEmpty() else emptyList(),
            sessions = if (activeUser != null) world.sessionsByUser[activeUser.id].orEmpty() else emptyList(),
            loginEvents = if (activeUser != null) world.loginEventsByUser[activeUser.id].orEmpty() else emptyList(),
            activityStats = activeUser?.let { repository.activityStatsFor(it.id) },
            ratingStats = activeUser?.let { repository.ratingStatsFor(it.id) },
            currentTimeEpochMs = nowMs,
            sandboxOtpHint = transient.second,
            statusBannerMessage = transient.first.first,
            isErrorBanner = transient.first.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SwapMoneyUiState()
    )

    fun clearBannerMessage() {
        _transientMessage.value = null to false
    }

    private fun showSuccess(msg: String) {
        _transientMessage.value = msg to false
    }

    private fun showError(msg: String) {
        _transientMessage.value = msg to true
    }

    fun refreshApproximateLocation() {
        viewModelScope.launch {
            val loc = repository.refreshLocationForActiveUser()
            showSuccess("Location updated: ${loc.areaLabel}")
        }
    }

    fun switchSandboxPersona(userId: String) {
        repository.switchSandboxPersona(userId)
        val user = uiState.value.allSandboxAccounts.firstOrNull { it.id == userId }
        if (user != null) {
            showSuccess("Switched Sandbox Persona to ${user.fullName}")
        }
    }

    fun login(identifier: String, password: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            when (val res = repository.login(identifier, password)) {
                is RepoResult.Success -> {
                    showSuccess("Signed in as ${res.data.fullName}")
                    onSuccess()
                }
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun sendOtp(channel: String, target: String, onSent: () -> Unit = {}) {
        viewModelScope.launch {
            when (val res = repository.sendOtp(channel, target)) {
                is RepoResult.Success -> {
                    if (uiState.value.preferences.backendMode == BackendMode.COMPLIANCE_SANDBOX) {
                        _sandboxOtpHint.value = "Sandbox $channel OTP: ${res.data}"
                    }
                    showSuccess(res.message ?: "OTP sent.")
                    onSent()
                }
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun verifyOtp(channel: String, target: String, code: String, onVerified: () -> Unit) {
        viewModelScope.launch {
            when (val res = repository.verifyOtp(channel, target, code)) {
                is RepoResult.Success -> {
                    _sandboxOtpHint.value = null
                    showSuccess("$channel verified successfully.")
                    onVerified()
                }
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun registerCustomer(
        fullName: String,
        mobile: String,
        email: String,
        address: String,
        password: String,
        onRegistered: () -> Unit
    ) {
        viewModelScope.launch {
            when (val res = repository.registerCustomer(fullName, mobile, email, address, password)) {
                is RepoResult.Success -> {
                    showSuccess(res.message ?: "Account created.")
                    onRegistered()
                }
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun recoverPassword(identifier: String, newPassword: String, onRecovered: () -> Unit) {
        viewModelScope.launch {
            when (val res = repository.recoverPassword(identifier, newPassword)) {
                is RepoResult.Success -> {
                    showSuccess(res.data)
                    onRecovered()
                }
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun logout() {
        repository.logout()
        showSuccess("Signed out securely.")
    }

    fun createRequest(
        wantedMethod: PaymentMethod,
        offeredMethod: PaymentMethod,
        amountInr: Int,
        radius: SearchRadius,
        expiryMinutes: Int,
        onCreated: () -> Unit
    ) {
        viewModelScope.launch {
            when (val res = repository.createExchangeRequest(
                wantedMethod, offeredMethod, amountInr, "", radius, expiryMinutes
            )) {
                is RepoResult.Success -> {
                    showSuccess(res.message ?: "Request created.")
                    onCreated()
                }
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun submitInterest(requestId: String, acceptorAddress: String = "", onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            when (val res = repository.submitInterest(requestId, acceptorAddress)) {
                is RepoResult.Success -> {
                    showSuccess(res.message ?: "Interest recorded.")
                    onSuccess()
                }
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun sendChatMessage(conversationId: String, text: String, onSent: () -> Unit = {}) {
        viewModelScope.launch {
            when (val res = repository.sendRestrictedChatMessage(conversationId, text)) {
                is RepoResult.Success -> onSent()
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun blockUser(userId: String) {
        repository.blockUser(userId)
        showSuccess("User blocked. Chat closed.")
    }

    fun reportUser(userId: String, reason: String) {
        when (val res = repository.reportUser(userId, reason)) {
            is RepoResult.Success -> showSuccess(res.data)
            is RepoResult.Error -> showError(res.message)
        }
    }

    fun submitStage1Approval(interestId: String, approve: Boolean) {
        viewModelScope.launch {
            when (val res = repository.submitStage1Approval(interestId, approve)) {
                is RepoResult.Success -> showSuccess(res.data)
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun submitStage2PartnerConfirmation(interestId: String, onConfirmed: () -> Unit = {}) {
        viewModelScope.launch {
            when (val res = repository.submitStage2PartnerConfirmation(interestId)) {
                is RepoResult.Success -> {
                    showSuccess(res.data)
                    onConfirmed()
                }
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun grantContactConsent(requestId: String) {
        viewModelScope.launch {
            when (val res = repository.grantContactConsent(requestId)) {
                is RepoResult.Success -> showSuccess(res.data)
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun proposeMeeting(requestId: String, location: String, timeLabel: String) {
        viewModelScope.launch {
            when (val res = repository.proposeMeetingLocation(requestId, location, timeLabel)) {
                is RepoResult.Success -> showSuccess(res.data)
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun approveMeeting(requestId: String) {
        when (val res = repository.approveMeetingLocation(requestId)) {
            is RepoResult.Success -> showSuccess(res.data)
            is RepoResult.Error -> showError(res.message)
        }
    }

    fun submitEvidence(
        requestId: String,
        referenceNumber: String,
        attachmentUri: String?,
        mimeType: String?,
        fileSizeBytes: Long,
        onSubmitted: () -> Unit = {}
    ) {
        viewModelScope.launch {
            when (val res = repository.submitEvidence(
                requestId, referenceNumber, attachmentUri, mimeType, fileSizeBytes
            )) {
                is RepoResult.Success -> {
                    showSuccess(res.message ?: "Evidence uploaded.")
                    onSubmitted()
                }
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun submitConfirmation(requestId: String, confirmedSuccess: Boolean, disputeNote: String = "") {
        viewModelScope.launch {
            when (val res = repository.submitCustomerConfirmation(requestId, confirmedSuccess, disputeNote)) {
                is RepoResult.Success -> showSuccess("Status updated: ${res.data.displayLabel}")
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun markReceiverReceived(requestId: String) {
        viewModelScope.launch {
            when (val res = repository.markReceiverReceived(requestId)) {
                is RepoResult.Success -> showSuccess(res.data)
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun cancelRequest(requestId: String) {
        when (val res = repository.cancelExchangeRequest(requestId)) {
            is RepoResult.Success -> showSuccess(res.data)
            is RepoResult.Error -> showError(res.message)
        }
    }

    fun submitRating(
        requestId: String,
        communication: Int,
        punctuality: Int,
        cooperation: Int,
        overallExperience: Int,
        comment: String,
        onSubmitted: () -> Unit = {}
    ) {
        viewModelScope.launch {
            when (val res = repository.submitCustomerRating(
                requestId, communication, punctuality, cooperation, overallExperience, comment
            )) {
                is RepoResult.Success -> {
                    showSuccess(res.message ?: "Rating saved.")
                    onSubmitted()
                }
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun reportReview(reviewId: String) {
        repository.reportReview(reviewId)
        showSuccess("Review flagged for moderation.")
    }

    fun performMfaAction(
        actionType: MfaActionType,
        mfaCode: String,
        newValue: String = "",
        onSuccess: () -> Unit = {}
    ) {
        when (val res = repository.performMfaProtectedOperation(actionType, mfaCode, newValue)) {
            is RepoResult.Success -> {
                showSuccess(res.data)
                onSuccess()
            }
            is RepoResult.Error -> showError(res.message)
        }
    }

    fun markNotificationsRead() {
        repository.markAllNotificationsRead()
    }

    fun acceptNotification(
        notificationId: String,
        acceptorAddress: String = "",
        onOpenRequest: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            when (val res = repository.acceptNotificationAction(notificationId, acceptorAddress)) {
                is RepoResult.Success -> {
                    showSuccess(res.message ?: "Request accepted.")
                    res.data?.let(onOpenRequest)
                }
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun ignoreNotification(notificationId: String) {
        viewModelScope.launch {
            when (val res = repository.ignoreNotificationAction(notificationId)) {
                is RepoResult.Success -> showSuccess(res.data)
                is RepoResult.Error -> showError(res.message)
            }
        }
    }

    fun updatePreferences(transform: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch {
            repository.updatePreferences(transform)
        }
    }

    fun toggleSimulatedOffline() {
        val currentlyOnline = repository.connectivityMonitor.isOnline.value
        repository.connectivityMonitor.setSimulatedOfflineForTesting(currentlyOnline)
        if (currentlyOnline) {
            showError("Connectivity lost — Offline resilience mode active.")
        } else {
            viewModelScope.launch {
                repository.clearQueuedSyncItems()
                showSuccess("Reconnected — Pending sync queue synchronized.")
            }
        }
    }
}
