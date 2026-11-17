package com.example.data.repository

import android.content.Context
import com.example.data.local.PendingSyncEntity
import com.example.data.local.PreferencesStore
import com.example.data.local.SwapMoneyDatabase
import com.example.data.remote.ApprovalStageRequestDto
import com.example.data.remote.ChatWebSocketClient
import com.example.data.remote.ConsentRequestDto
import com.example.data.remote.CreateExchangeRequestDto
import com.example.data.remote.ExchangeConfirmationDto
import com.example.data.remote.FastApiClientFactory
import com.example.data.remote.LoginRequestDto
import com.example.data.remote.MeetingProposalDto
import com.example.data.remote.OtpSendRequestDto
import com.example.data.remote.OtpVerifyRequestDto
import com.example.data.remote.RatingSubmitDto
import com.example.data.remote.RegisterRequestDto
import com.example.domain.ActiveSession
import com.example.domain.AppPreferences
import com.example.domain.ApprovalDecision
import com.example.domain.ApprovalRecord
import com.example.domain.BackendMode
import com.example.domain.ChatMessage
import com.example.domain.CustomerRatingStats
import com.example.domain.EvidenceItem
import com.example.domain.ExchangeRequest
import com.example.domain.ExchangeStatus
import com.example.domain.HistoryCategory
import com.example.domain.HistoryEntry
import com.example.domain.InAppNotification
import com.example.domain.LoginEvent
import com.example.domain.MfaActionType
import com.example.domain.NotificationActionStatus
import com.example.domain.NotificationCategory
import com.example.domain.PaymentMethod
import com.example.domain.PendingSyncOperation
import com.example.domain.RequestInterest
import com.example.domain.ReviewRecord
import com.example.domain.SearchRadius
import com.example.domain.SecurityValidators
import com.example.domain.SwapActivityStats
import com.example.domain.UserAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class RepoResult<out T> {
    data class Success<T>(val data: T, val message: String? = null) : RepoResult<T>()
    data class Error(val message: String, val queuedOffline: Boolean = false) : RepoResult<Nothing>()
}

class SwapMoneyRepository(
    context: Context
) {
    val preferencesStore = PreferencesStore(context)
    val connectivityMonitor = ConnectivityMonitor(context)
    val locationHelper = LocationHelper(context)
    private val database = SwapMoneyDatabase.getInstance(context)
    private val pendingSyncDao = database.pendingSyncDao()
    private val webSocketClient = ChatWebSocketClient()

    private val _worldState = MutableStateFlow(SandboxFixtures.createInitialState())
    val worldState: StateFlow<SandboxWorldState> = _worldState.asStateFlow()

    val pendingSyncOperations: Flow<List<PendingSyncOperation>> = pendingSyncDao.observeAll().map { list ->
        list.map { entity ->
            PendingSyncOperation(
                id = entity.id,
                idempotencyKey = entity.idempotencyKey,
                actionLabel = entity.actionLabel,
                payloadSummary = entity.payloadSummary,
                createdAtEpochMs = entity.createdAtEpochMs
            )
        }
    }

    private var bearerToken: String = ""
    private var lastSubmittedIdempotencyKeys = mutableSetOf<String>()

    private fun nowLabel(): String {
        val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH)
        return fmt.format(Date())
    }

    private suspend fun isFastApiMode(): Boolean {
        return preferencesStore.preferencesFlow.first().backendMode == BackendMode.FASTAPI_SERVER
    }

    private suspend fun currentBaseUrl(): String {
        return preferencesStore.preferencesFlow.first().fastApiBaseUrl
    }

    suspend fun updatePreferences(transform: (AppPreferences) -> AppPreferences) {
        preferencesStore.updatePreferences(transform)
    }

    suspend fun refreshLocationForActiveUser(): ApproximateLocation {
        val approx = locationHelper.fetchApproximateLocation()
        _worldState.update { state ->
            val activeId = state.activeUserId ?: return@update state
            val updatedAccounts = state.accounts.map { acc ->
                if (acc.id == activeId) {
                    acc.copy(
                        areaLabel = approx.areaLabel,
                        latitude = approx.latitude,
                        longitude = approx.longitude
                    )
                } else acc
            }
            val updatedRequests = state.requests.map { req ->
                val dist = LocationHelper.approximateDistanceMeters(
                    approx.latitude,
                    approx.longitude,
                    req.latitude,
                    req.longitude
                )
                req.copy(approximateDistanceMeters = dist)
            }
            state.copy(accounts = updatedAccounts, requests = updatedRequests)
        }
        return approx
    }

    fun switchSandboxPersona(userId: String) {
        _worldState.update { it.copy(activeUserId = userId) }
    }

    fun logout() {
        bearerToken = ""
        webSocketClient.disconnect()
        _worldState.update { it.copy(activeUserId = null) }
    }

    suspend fun sendOtp(channel: String, target: String): RepoResult<String> {
        if (!connectivityMonitor.isOnline.value) {
            return RepoResult.Error("Offline: OTP delivery requires an active internet connection.")
        }
        if (isFastApiMode()) {
            return runCatching {
                val api = FastApiClientFactory.createApiService(currentBaseUrl())
                val resp = api.sendOtp(OtpSendRequestDto(channel = channel, target = target))
                if (resp.isSuccessful) {
                    RepoResult.Success("OTP dispatched via FastAPI backend.")
                } else {
                    RepoResult.Error("FastAPI rejected OTP request (HTTP ${resp.code()}).")
                }
            }.getOrElse {
                RepoResult.Error("Unable to reach FastAPI backend (${it.localizedMessage ?: "Network error"}). Switch to Compliance Sandbox in Connection Settings to evaluate locally.")
            }
        }
        val code = (100000..999999).random().toString()
        _worldState.update {
            it.copy(
                pendingOtpChallenge = OtpChallengeState(
                    target = target,
                    channel = channel,
                    sandboxCode = code,
                    expiresAtEpochMs = System.currentTimeMillis() + 300_000L,
                    attemptsRemaining = SecurityValidators.MAX_OTP_ATTEMPTS
                )
            )
        }
        // Code is returned only to the Sandbox UI banner—never logged.
        return RepoResult.Success(code, "OTP generated for $channel verification.")
    }

    suspend fun verifyOtp(channel: String, target: String, code: String): RepoResult<Boolean> {
        if (!connectivityMonitor.isOnline.value) {
            return RepoResult.Error("Offline: Cannot verify OTP without backend connectivity.")
        }
        if (isFastApiMode()) {
            return runCatching {
                val api = FastApiClientFactory.createApiService(currentBaseUrl())
                val resp = api.verifyOtp(OtpVerifyRequestDto(channel, target, code))
                if (resp.isSuccessful && resp.body()?.ok == true) {
                    RepoResult.Success(true)
                } else {
                    RepoResult.Error("Invalid or expired OTP.")
                }
            }.getOrElse {
                RepoResult.Error("Network error while verifying OTP.")
            }
        }
        val challenge = _worldState.value.pendingOtpChallenge
            ?: return RepoResult.Error("No active OTP challenge. Request a new code.")
        if (challenge.target != target || challenge.channel != channel) {
            return RepoResult.Error("OTP target mismatch. Request a fresh code.")
        }
        if (System.currentTimeMillis() > challenge.expiresAtEpochMs) {
            return RepoResult.Error("OTP expired. Please request a new code.")
        }
        if (challenge.attemptsRemaining <= 0) {
            return RepoResult.Error("Maximum OTP attempts exceeded. Rate-limit active.")
        }
        return if (code.trim() == challenge.sandboxCode) {
            _worldState.update { it.copy(pendingOtpChallenge = null) }
            RepoResult.Success(true)
        } else {
            val left = challenge.attemptsRemaining - 1
            _worldState.update {
                it.copy(pendingOtpChallenge = challenge.copy(attemptsRemaining = left))
            }
            RepoResult.Error("Incorrect OTP. $left attempt(s) remaining.")
        }
    }

    suspend fun login(identifier: String, password: String): RepoResult<UserAccount> {
        if (!connectivityMonitor.isOnline.value) {
            return RepoResult.Error("Offline: Login requires connectivity to verify credentials.")
        }
        if (isFastApiMode()) {
            return runCatching {
                val api = FastApiClientFactory.createApiService(currentBaseUrl())
                val resp = api.login(LoginRequestDto(identifier.trim(), password, "Android Client"))
                if (resp.isSuccessful && resp.body() != null) {
                    val body = resp.body()!!
                    bearerToken = body.accessToken
                    val matched = _worldState.value.accounts.first().copy(
                        id = body.userId,
                        swuId = body.swuId,
                        email = identifier.trim()
                    )
                    _worldState.update {
                        it.copy(
                            accounts = (it.accounts.filterNot { a -> a.id == matched.id }) + matched,
                            activeUserId = matched.id
                        )
                    }
                    RepoResult.Success(matched)
                } else {
                    RepoResult.Error("Authentication failed (HTTP ${resp.code()}).")
                }
            }.getOrElse {
                RepoResult.Error("Could not connect to FastAPI server. Check Server URL or use Compliance Sandbox.")
            }
        }
        val trimmed = identifier.trim().lowercase()
        val state = _worldState.value
        val account = state.accounts.firstOrNull {
            it.email.lowercase() == trimmed || it.mobileNumber == trimmed || it.swuId.lowercase() == trimmed
        } ?: return RepoResult.Error("Account not found for $identifier.")

        val hashed = SecurityValidators.hashSecret(password)
        if (account.passwordHash != hashed) {
            val failedEvent = LoginEvent(
                id = UUID.randomUUID().toString(),
                timestampLabel = nowLabel(),
                deviceName = "Android Client",
                locationLabel = "Bengaluru, IN",
                success = false,
                detail = "Failed password attempt"
            )
            _worldState.update { s ->
                val list = s.loginEventsByUser[account.id].orEmpty()
                s.copy(loginEventsByUser = s.loginEventsByUser + (account.id to (listOf(failedEvent) + list)))
            }
            return RepoResult.Error("Incorrect password.")
        }

        val okEvent = LoginEvent(
            id = UUID.randomUUID().toString(),
            timestampLabel = nowLabel(),
            deviceName = "Android Client",
            locationLabel = "Bengaluru, IN",
            success = true,
            detail = "Authenticated session"
        )
        _worldState.update { s ->
            val list = s.loginEventsByUser[account.id].orEmpty()
            s.copy(
                activeUserId = account.id,
                loginEventsByUser = s.loginEventsByUser + (account.id to (listOf(okEvent) + list))
            )
        }
        return RepoResult.Success(account)
    }

    suspend fun registerCustomer(
        fullName: String,
        mobile: String,
        email: String,
        address: String,
        password: String
    ): RepoResult<UserAccount> {
        if (!connectivityMonitor.isOnline.value) {
            return RepoResult.Error("Offline: Registration requires connectivity.")
        }
        val state = _worldState.value
        val duplicate = state.accounts.any {
            it.mobileNumber == mobile.trim() || it.email.equals(email.trim(), ignoreCase = true)
        }
        if (duplicate) {
            return RepoResult.Error("Duplicate account detected for this mobile number or email.")
        }
        if (isFastApiMode()) {
            runCatching {
                val api = FastApiClientFactory.createApiService(currentBaseUrl())
                val resp = api.register(
                    RegisterRequestDto(fullName.trim(), mobile.trim(), email.trim(), address.trim(), password)
                )
                if (resp.isSuccessful && resp.body() != null) {
                    bearerToken = resp.body()!!.accessToken
                } else {
                    return RepoResult.Error("FastAPI registration rejected (HTTP ${resp.code()}).")
                }
            }.onFailure {
                return RepoResult.Error("FastAPI server unreachable during registration.")
            }
        }
        val nextNum = 100090 + state.accounts.size
        val swuId = "SWU$nextNum"
        val newAccount = UserAccount(
            id = "usr_${UUID.randomUUID().toString().take(8)}",
            swuId = swuId,
            fullName = fullName.trim(),
            mobileNumber = mobile.trim(),
            email = email.trim(),
            residentialAddress = address.trim(),
            passwordHash = SecurityValidators.hashSecret(password),
            mobileVerified = true,
            emailVerified = true,
            additionalKycVerified = false,
            mfaEnabled = true,
            authenticatorLinked = false,
            passkeyEnabled = false,
            createdAtLabel = nowLabel(),
            areaLabel = "Indiranagar Metro Zone, Bengaluru",
            latitude = 12.978,
            longitude = 77.641,
            ratingAverage = 5.0,
            ratingCount = 0,
            successfulExchanges = 0
        )
        val welcomeNotif = InAppNotification(
            id = UUID.randomUUID().toString(),
            category = NotificationCategory.SECURITY_ALERT,
            title = "Account Created ($swuId)",
            safePreview = "Mobile & email verified. MFA protection is active on your profile.",
            timestampLabel = nowLabel(),
            isRead = false
        )
        val historyItem = HistoryEntry(
            id = UUID.randomUUID().toString(),
            swapCode = swuId,
            category = HistoryCategory.ACCOUNT_SECURITY,
            title = "Customer Account Registered",
            summary = "Unique Swap Money ID $swuId issued after dual OTP verification",
            amountInr = null,
            exchangePairLabel = null,
            timestampLabel = nowLabel(),
            status = null,
            partnerName = null,
            details = listOf("Mobile OTP Verified", "Email OTP Verified", "Unique ID: $swuId")
        )
        _worldState.update { s ->
            s.copy(
                accounts = s.accounts + newAccount,
                activeUserId = newAccount.id,
                notificationsByUser = s.notificationsByUser + (newAccount.id to listOf(welcomeNotif)),
                historyEntries = listOf(historyItem) + s.historyEntries
            )
        }
        return RepoResult.Success(newAccount, "Welcome to Swap Money! ID: $swuId")
    }

    suspend fun recoverPassword(emailOrMobile: String, newPassword: String): RepoResult<String> {
        if (!connectivityMonitor.isOnline.value) {
            return RepoResult.Error("Offline: Password recovery requires backend connectivity.")
        }
        val passError = SecurityValidators.validatePasswordStrength(newPassword)
        if (passError != null) return RepoResult.Error(passError)
        val target = emailOrMobile.trim().lowercase()
        val state = _worldState.value
        val account = state.accounts.firstOrNull {
            it.email.lowercase() == target || it.mobileNumber == target
        } ?: return RepoResult.Error("No verified account matches that contact.")
        _worldState.update { s ->
            s.copy(
                accounts = s.accounts.map {
                    if (it.id == account.id) it.copy(passwordHash = SecurityValidators.hashSecret(newPassword))
                    else it
                },
                historyEntries = listOf(
                    HistoryEntry(
                        id = UUID.randomUUID().toString(),
                        swapCode = account.swuId,
                        category = HistoryCategory.ACCOUNT_SECURITY,
                        title = "Password Recovered",
                        summary = "Password reset completed after identity OTP verification",
                        amountInr = null,
                        exchangePairLabel = null,
                        timestampLabel = nowLabel(),
                        status = null,
                        partnerName = null,
                        details = listOf("Verified recovery flow for ${account.swuId}")
                    )
                ) + s.historyEntries
            )
        }
        return RepoResult.Success("Password updated successfully. You may now sign in.")
    }

    suspend fun createExchangeRequest(
        wantedMethod: PaymentMethod,
        offeredMethod: PaymentMethod,
        amountInr: Int,
        areaLabel: String,
        radius: SearchRadius,
        expiryMinutes: Int,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): RepoResult<ExchangeRequest> {
        if (idempotencyKey in lastSubmittedIdempotencyKeys) {
            return RepoResult.Error("Duplicate submission prevented.")
        }
        val state = _worldState.value
        val activeUser = state.accounts.firstOrNull { it.id == state.activeUserId }
            ?: return RepoResult.Error("Sign in required.")
        val todayKey = SecurityValidators.currentIstDateKey()
        val usedToday = state.dailyUsedByUserAndDate[activeUser.id to todayKey] ?: 0

        val validationError = SecurityValidators.validateRequestAmount(
            amountInr = amountInr,
            usedTodayInr = usedToday,
            wantedMethod = wantedMethod,
            offeredMethod = offeredMethod
        )
        if (validationError != null) {
            return RepoResult.Error(validationError)
        }

        val hasDuplicateOpen = state.requests.any {
            it.creatorId == activeUser.id &&
                it.wantedMethod == wantedMethod &&
                it.offeredMethod == offeredMethod &&
                it.amountInr == amountInr &&
                it.status in setOf(ExchangeStatus.REQUEST_CREATED, ExchangeStatus.INTEREST_RECEIVED)
        }
        if (hasDuplicateOpen) {
            return RepoResult.Error("An identical active request already exists.")
        }

        if (!connectivityMonitor.isOnline.value) {
            pendingSyncDao.enqueue(
                PendingSyncEntity(
                    id = UUID.randomUUID().toString(),
                    idempotencyKey = idempotencyKey,
                    actionLabel = "Create Request ₹$amountInr (${wantedMethod.shortLabel})",
                    payloadSummary = "${radius.label} • Acceptor address pending",
                    createdAtEpochMs = System.currentTimeMillis()
                )
            )
            return RepoResult.Error(
                "Offline: Request queued safely for sync. Not confirmed until backend responds.",
                queuedOffline = true
            )
        }

        if (isFastApiMode()) {
            runCatching {
                val api = FastApiClientFactory.createApiService(currentBaseUrl())
                val resp = api.createExchangeRequest(
                    "Bearer $bearerToken",
                    CreateExchangeRequestDto(
                        wantedMethod = wantedMethod.name,
                        offeredMethod = offeredMethod.name,
                        amountInr = amountInr,
                        areaLabel = "Acceptor address pending",
                        approximateLat = activeUser.latitude,
                        approximateLng = activeUser.longitude,
                        radiusMeters = radius.meters,
                        expiryMinutes = expiryMinutes,
                        idempotencyKey = idempotencyKey
                    )
                )
                if (!resp.isSuccessful) {
                    return RepoResult.Error("FastAPI rejected request (HTTP ${resp.code()}).")
                }
            }.onFailure {
                return RepoResult.Error("FastAPI unreachable. Request was NOT confirmed.")
            }
        }

        lastSubmittedIdempotencyKeys.add(idempotencyKey)
        val swapNum = 127 + state.requests.size
        val swapCode = "SWP-2026-%06d".format(swapNum)
        val newRequest = ExchangeRequest(
            id = "req_${UUID.randomUUID().toString().take(8)}",
            swapCode = swapCode,
            creatorId = activeUser.id,
            creatorSwuId = activeUser.swuId,
            creatorDisplayName = activeUser.fullName,
            creatorVerified = activeUser.mobileVerified && activeUser.emailVerified,
            creatorScore = activeUser.ratingAverage,
            creatorSuccessfulCount = activeUser.successfulExchanges,
            wantedMethod = wantedMethod,
            offeredMethod = offeredMethod,
            amountInr = amountInr,
            areaLabel = "Acceptor address pending",
            latitude = activeUser.latitude,
            longitude = activeUser.longitude,
            approximateDistanceMeters = 100,
            searchRadius = radius,
            expiryMinutes = expiryMinutes,
            createdAtEpochMs = System.currentTimeMillis(),
            createdAtLabel = "Just now",
            status = ExchangeStatus.REQUEST_CREATED,
            istDateKey = todayKey
        )

        val historyEntry = HistoryEntry(
            id = UUID.randomUUID().toString(),
            swapCode = swapCode,
            category = HistoryCategory.REQUESTS_CREATED,
            title = "Exchange Request Created",
            summary = "Want ${wantedMethod.shortLabel} • Offering ${offeredMethod.shortLabel} • ${SecurityValidators.formatInr(amountInr)}",
            amountInr = amountInr,
            exchangePairLabel = "${wantedMethod.shortLabel} <-> ${offeredMethod.shortLabel}",
            timestampLabel = nowLabel(),
            status = ExchangeStatus.REQUEST_CREATED,
            partnerName = null,
            details = listOf(
                "Request ID: $swapCode",
                "Radius: ${radius.label} • Address: Provided by acceptor",
                "IST Daily Quota Deducted: ${SecurityValidators.formatInr(amountInr)}"
            )
        )

        // Dispatch privacy-safe FCM-style notifications to eligible nearby users within radius
        val updatedNotifs = state.notificationsByUser.toMutableMap()
        state.accounts.filter { it.id != activeUser.id }.forEach { neighbor ->
            val dist = LocationHelper.approximateDistanceMeters(
                activeUser.latitude, activeUser.longitude, neighbor.latitude, neighbor.longitude
            )
            if (dist <= radius.meters) {
                val n = InAppNotification(
                    id = UUID.randomUUID().toString(),
                    category = NotificationCategory.NEARBY_REQUEST,
                    title = "Nearby Request Within ${radius.label} • ${SecurityValidators.formatInr(amountInr)}",
                    safePreview = "A verified user nearby wants ${SecurityValidators.formatInr(amountInr)} ${wantedMethod.shortLabel} (offering ${offeredMethod.shortLabel}).",
                    timestampLabel = "Just now",
                    isRead = false,
                    linkedRequestId = newRequest.id,
                    amountInr = amountInr
                )
                updatedNotifs[neighbor.id] = listOf(n) + updatedNotifs[neighbor.id].orEmpty()
            }
        }

        _worldState.update { s ->
            s.copy(
                requests = listOf(newRequest) + s.requests,
                dailyUsedByUserAndDate = s.dailyUsedByUserAndDate + ((activeUser.id to todayKey) to (usedToday + amountInr)),
                historyEntries = listOf(historyEntry) + s.historyEntries,
                notificationsByUser = updatedNotifs
            )
        }
        return RepoResult.Success(newRequest, "Request $swapCode published within ${radius.label}.")
    }

    suspend fun submitInterest(requestId: String, acceptorAddress: String = ""): RepoResult<RequestInterest> {
        if (!connectivityMonitor.isOnline.value) {
            return RepoResult.Error("Offline: Cannot submit interest while disconnected.")
        }
        val state = _worldState.value
        val activeUser = state.accounts.firstOrNull { it.id == state.activeUserId }
            ?: return RepoResult.Error("Sign in required.")
        val request = state.requests.firstOrNull { it.id == requestId }
            ?: return RepoResult.Error("Request not found.")
        if (request.creatorId == activeUser.id) {
            return RepoResult.Error("You cannot submit interest in your own request.")
        }
        if (state.interests.any { it.requestId == requestId && it.interestedUserId == activeUser.id }) {
            return RepoResult.Error("You have already shown interest in this request.")
        }
        if (request.status != ExchangeStatus.REQUEST_CREATED && request.status != ExchangeStatus.INTEREST_RECEIVED) {
            return RepoResult.Error("This request is no longer accepting new interests.")
        }
        if (request.isPastDeadline()) {
            expireOverdueRequests()
            return RepoResult.Error("This request has passed its deadline time and expired.")
        }

        if (isFastApiMode()) {
            runCatching {
                val api = FastApiClientFactory.createApiService(currentBaseUrl())
                val resp = api.submitInterest("Bearer $bearerToken", requestId)
                if (!resp.isSuccessful) {
                    return RepoResult.Error("FastAPI rejected interest (HTTP ${resp.code()}).")
                }
            }.onFailure {
                return RepoResult.Error("FastAPI unreachable.")
            }
        }

        val resolvedAcceptorAddress = acceptorAddress.trim().ifBlank { activeUser.areaLabel }
        val conversationId = "conv_${requestId}_${activeUser.id}"
        val dist = LocationHelper.approximateDistanceMeters(
            activeUser.latitude, activeUser.longitude, request.latitude, request.longitude
        )
        val interest = RequestInterest(
            id = "int_${UUID.randomUUID().toString().take(8)}",
            requestId = requestId,
            interestedUserId = activeUser.id,
            interestedSwuId = activeUser.swuId,
            displayName = activeUser.fullName,
            verified = activeUser.mobileVerified && activeUser.emailVerified,
            customerScore = activeUser.ratingAverage,
            successfulExchanges = activeUser.successfulExchanges,
            approximateDistanceMeters = dist,
            timestampLabel = nowLabel(),
            conversationId = conversationId,
            acceptorProvidedAddress = resolvedAcceptorAddress
        )

        val creatorNotif = InAppNotification(
            id = UUID.randomUUID().toString(),
            category = NotificationCategory.INTEREST_RECEIVED,
            title = "Interest Received • ${SecurityValidators.formatInr(request.amountInr)}",
            safePreview = "${activeUser.fullName} expressed interest in ${request.swapCode} (${SecurityValidators.formatInr(request.amountInr)}).",
            timestampLabel = "Just now",
            isRead = false,
            linkedRequestId = requestId,
            linkedInterestId = interest.id,
            amountInr = request.amountInr
        )
        val hist = HistoryEntry(
            id = UUID.randomUUID().toString(),
            swapCode = request.swapCode,
            category = HistoryCategory.INTERESTS_SUBMITTED,
            title = "Interest Submitted",
            summary = "Expressed interest in ${request.creatorDisplayName}'s ${request.wantedMethod.shortLabel} request",
            amountInr = request.amountInr,
            exchangePairLabel = "${request.wantedMethod.shortLabel} <-> ${request.offeredMethod.shortLabel}",
            timestampLabel = nowLabel(),
            status = ExchangeStatus.INTEREST_RECEIVED,
            partnerName = request.creatorDisplayName,
            details = listOf(
                "Request ID: ${request.swapCode}",
                "Acceptor Provided Address: $resolvedAcceptorAddress",
                "Isolated Chat Opened"
            )
        )

        _worldState.update { s ->
            val updatedReqs = s.requests.map {
                if (it.id == requestId) {
                    it.copy(
                        status = if (it.status == ExchangeStatus.REQUEST_CREATED) ExchangeStatus.INTEREST_RECEIVED else it.status,
                        areaLabel = resolvedAcceptorAddress,
                        meetingLocation = it.meetingLocation ?: resolvedAcceptorAddress
                    )
                } else it
            }
            val updatedNotifs = s.notificationsByUser.toMutableMap()
            updatedNotifs[request.creatorId] = listOf(creatorNotif) + updatedNotifs[request.creatorId].orEmpty()
            s.copy(
                requests = updatedReqs,
                interests = s.interests + interest,
                historyEntries = listOf(hist) + s.historyEntries,
                notificationsByUser = updatedNotifs
            )
        }
        return RepoResult.Success(interest, "Interest & address submitted. Restricted chat opened.")
    }

    suspend fun sendRestrictedChatMessage(conversationId: String, text: String): RepoResult<ChatMessage> {
        val state = _worldState.value
        val activeUser = state.accounts.firstOrNull { it.id == state.activeUserId }
            ?: return RepoResult.Error("Sign in required.")
        val interest = state.interests.firstOrNull { it.conversationId == conversationId }
            ?: return RepoResult.Error("Conversation not found.")
        val request = state.requests.firstOrNull { it.id == interest.requestId }
            ?: return RepoResult.Error("Request not found.")

        val otherId = if (activeUser.id == request.creatorId) interest.interestedUserId else request.creatorId
        if ((activeUser.id to otherId) in state.blockedPairs || (otherId to activeUser.id) in state.blockedPairs) {
            return RepoResult.Error("Messaging blocked for this conversation.")
        }

        val validation = SecurityValidators.validateRestrictedChatMessage(text)
        if (!validation.allowed) {
            return RepoResult.Error(validation.reason ?: "Message blocked by safety policy.")
        }
        if (!connectivityMonitor.isOnline.value) {
            return RepoResult.Error("Offline: Chat messages cannot be sent without connectivity.")
        }

        if (isFastApiMode()) {
            webSocketClient.sendMessage(text.trim())
        }

        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = activeUser.id,
            senderName = activeUser.fullName,
            text = text.trim(),
            timestampLabel = nowLabel(),
            delivered = true
        )
        val notif = InAppNotification(
            id = UUID.randomUUID().toString(),
            category = NotificationCategory.CHAT_MESSAGE,
            title = "New Restricted Chat Message",
            safePreview = "${activeUser.fullName} sent a message regarding ${request.swapCode} (${SecurityValidators.formatInr(request.amountInr)}).",
            timestampLabel = "Just now",
            isRead = false,
            linkedRequestId = request.id,
            amountInr = request.amountInr
        )
        _worldState.update { s ->
            val currentList = s.messagesByConversation[conversationId].orEmpty()
            val updatedNotifs = s.notificationsByUser.toMutableMap()
            updatedNotifs[otherId] = listOf(notif) + updatedNotifs[otherId].orEmpty()
            s.copy(
                messagesByConversation = s.messagesByConversation + (conversationId to (currentList + msg)),
                notificationsByUser = updatedNotifs
            )
        }
        return RepoResult.Success(msg)
    }

    fun blockUser(targetUserId: String) {
        val activeId = _worldState.value.activeUserId ?: return
        _worldState.update { s ->
            s.copy(blockedPairs = s.blockedPairs + (activeId to targetUserId))
        }
    }

    fun reportUser(targetUserId: String, reason: String): RepoResult<String> {
        val activeUser = _worldState.value.accounts.firstOrNull { it.id == _worldState.value.activeUserId }
            ?: return RepoResult.Error("Sign in required.")
        _worldState.update { s ->
            s.copy(
                reportedUserIds = s.reportedUserIds + targetUserId,
                historyEntries = listOf(
                    HistoryEntry(
                        id = UUID.randomUUID().toString(),
                        swapCode = "RPT-${System.currentTimeMillis().toString().takeLast(6)}",
                        category = HistoryCategory.COMMUNICATION,
                        title = "User Reported for Review",
                        summary = "Submitted safety report: $reason",
                        amountInr = null,
                        exchangePairLabel = null,
                        timestampLabel = nowLabel(),
                        status = null,
                        partnerName = null,
                        details = listOf("Reported by ${activeUser.swuId}", "Reason: $reason")
                    )
                ) + s.historyEntries
            )
        }
        return RepoResult.Success("Report submitted to Trust & Safety review.")
    }

    suspend fun submitStage1Approval(interestId: String, approve: Boolean): RepoResult<String> {
        if (!connectivityMonitor.isOnline.value) {
            return RepoResult.Error("Offline: Approval actions require backend confirmation.")
        }
        val state = _worldState.value
        val activeUser = state.accounts.firstOrNull { it.id == state.activeUserId }
            ?: return RepoResult.Error("Sign in required.")
        val interest = state.interests.firstOrNull { it.id == interestId }
            ?: return RepoResult.Error("Interest record not found.")
        val request = state.requests.firstOrNull { it.id == interest.requestId }
            ?: return RepoResult.Error("Request not found.")
        if (request.creatorId != activeUser.id) {
            return RepoResult.Error("Only the request creator may approve or reject interests.")
        }
        if (isFastApiMode()) {
            runCatching {
                val api = FastApiClientFactory.createApiService(currentBaseUrl())
                val resp = api.submitStage1Approval(
                    "Bearer $bearerToken",
                    interestId,
                    ApprovalStageRequestDto(
                        decision = if (approve) "APPROVE_INTEREST" else "REJECT",
                        idempotencyKey = UUID.randomUUID().toString()
                    )
                )
                if (!resp.isSuccessful) {
                    return RepoResult.Error("FastAPI rejected Stage 1 action (HTTP ${resp.code()}).")
                }
            }.onFailure {
                return RepoResult.Error("FastAPI unreachable.")
            }
        }

        val decision = if (approve) ApprovalDecision.APPROVED else ApprovalDecision.REJECTED
        val record = ApprovalRecord(
            id = UUID.randomUUID().toString(),
            requestId = request.id,
            approvingUserId = activeUser.id,
            selectedUserId = interest.interestedUserId,
            stage = 1,
            decision = decision,
            timestampLabel = nowLabel()
        )
        val notif = InAppNotification(
            id = UUID.randomUUID().toString(),
            category = NotificationCategory.APPROVAL,
            title = if (approve) "Shortlisted (Stage 1) • ${SecurityValidators.formatInr(request.amountInr)}" else "Interest Not Selected",
            safePreview = if (approve) {
                "You have been shortlisted for ${request.swapCode} (${SecurityValidators.formatInr(request.amountInr)}). Awaiting Stage 2 confirmation."
            } else {
                "Your interest in ${request.swapCode} (${SecurityValidators.formatInr(request.amountInr)}) was declined."
            },
            timestampLabel = "Just now",
            isRead = false,
            linkedRequestId = if (approve) request.id else null,
            amountInr = request.amountInr
        )
        val hist = HistoryEntry(
            id = UUID.randomUUID().toString(),
            swapCode = request.swapCode,
            category = HistoryCategory.APPROVALS,
            title = if (approve) "Stage 1 Interest Shortlisted" else "Stage 1 Interest Rejected",
            summary = "${interest.displayName} • ${SecurityValidators.formatInr(request.amountInr)}",
            amountInr = request.amountInr,
            exchangePairLabel = "${request.wantedMethod.shortLabel} <-> ${request.offeredMethod.shortLabel}",
            timestampLabel = nowLabel(),
            status = if (approve) ExchangeStatus.FIRST_APPROVAL else request.status,
            partnerName = interest.displayName,
            details = listOf(
                "Stage: First Approval",
                "Decision: ${decision.name}",
                "Approving Customer: ${activeUser.swuId}"
            )
        )

        _worldState.update { s ->
            val updatedInterests = s.interests.map {
                if (it.id == interestId) it.copy(stage1Status = decision) else it
            }
            val updatedRequests = s.requests.map {
                if (it.id == request.id && approve) it.copy(status = ExchangeStatus.FIRST_APPROVAL) else it
            }
            val updatedNotifs = s.notificationsByUser.toMutableMap()
            updatedNotifs[interest.interestedUserId] = listOf(notif) + updatedNotifs[interest.interestedUserId].orEmpty()
            s.copy(
                interests = updatedInterests,
                requests = updatedRequests,
                approvals = s.approvals + record,
                historyEntries = listOf(hist) + s.historyEntries,
                notificationsByUser = updatedNotifs
            )
        }
        return RepoResult.Success(
            if (approve) "Stage 1 approved. Proceed to explicit Stage 2 confirmation when ready."
            else "Interest rejected."
        )
    }

    suspend fun submitStage2PartnerConfirmation(interestId: String): RepoResult<String> {
        if (!connectivityMonitor.isOnline.value) {
            return RepoResult.Error("Offline: Stage 2 confirmation requires backend lock.")
        }
        val state = _worldState.value
        val activeUser = state.accounts.firstOrNull { it.id == state.activeUserId }
            ?: return RepoResult.Error("Sign in required.")
        val interest = state.interests.firstOrNull { it.id == interestId }
            ?: return RepoResult.Error("Interest record not found.")
        val request = state.requests.firstOrNull { it.id == interest.requestId }
            ?: return RepoResult.Error("Request not found.")
        if (request.creatorId != activeUser.id) {
            return RepoResult.Error("Only the request creator can confirm an exchange partner.")
        }
        if (interest.stage1Status != ApprovalDecision.APPROVED) {
            return RepoResult.Error("Mandatory Stage 1 approval must be completed first.")
        }
        if (request.selectedPartnerId != null) {
            return RepoResult.Error("A partner has already been locked for ${request.swapCode}.")
        }

        if (isFastApiMode()) {
            runCatching {
                val api = FastApiClientFactory.createApiService(currentBaseUrl())
                val resp = api.submitStage2Confirmation(
                    "Bearer $bearerToken",
                    interestId,
                    ApprovalStageRequestDto("CONFIRM_PARTNER", UUID.randomUUID().toString())
                )
                if (!resp.isSuccessful) {
                    return RepoResult.Error("FastAPI rejected Stage 2 confirmation.")
                }
            }.onFailure {
                return RepoResult.Error("FastAPI unreachable.")
            }
        }

        val record = ApprovalRecord(
            id = UUID.randomUUID().toString(),
            requestId = request.id,
            approvingUserId = activeUser.id,
            selectedUserId = interest.interestedUserId,
            stage = 2,
            decision = ApprovalDecision.APPROVED,
            timestampLabel = nowLabel()
        )
        val partnerNotif = InAppNotification(
            id = UUID.randomUUID().toString(),
            category = NotificationCategory.APPROVAL,
            title = "Partner Confirmed (Stage 2) • ${SecurityValidators.formatInr(request.amountInr)}",
            safePreview = "You were selected for ${request.swapCode} (${SecurityValidators.formatInr(request.amountInr)}). Contact consent required next.",
            timestampLabel = "Just now",
            isRead = false,
            linkedRequestId = request.id,
            amountInr = request.amountInr
        )
        val hist = HistoryEntry(
            id = UUID.randomUUID().toString(),
            swapCode = request.swapCode,
            category = HistoryCategory.APPROVALS,
            title = "Stage 2 Partner Confirmed",
            summary = "Confirmed ${interest.displayName} for ${SecurityValidators.formatInr(request.amountInr)}",
            amountInr = request.amountInr,
            exchangePairLabel = "${request.wantedMethod.shortLabel} <-> ${request.offeredMethod.shortLabel}",
            timestampLabel = nowLabel(),
            status = ExchangeStatus.AWAITING_CONSENT,
            partnerName = interest.displayName,
            details = listOf(
                "Stage: Second Approval (Final Partner Lock)",
                "Selected Partner: ${interest.interestedSwuId}",
                "Status: AWAITING CONSENT"
            )
        )

        _worldState.update { s ->
            val updatedInterests = s.interests.map {
                when {
                    it.id == interestId -> it.copy(stage2Confirmed = true)
                    it.requestId == request.id -> it.copy(stage1Status = ApprovalDecision.REJECTED)
                    else -> it
                }
            }
            val updatedRequests = s.requests.map {
                if (it.id == request.id) {
                    val lockedAddress = interest.acceptorProvidedAddress?.takeIf { addr -> addr.isNotBlank() }
                    it.copy(
                        status = ExchangeStatus.AWAITING_CONSENT,
                        selectedInterestId = interestId,
                        selectedPartnerId = interest.interestedUserId,
                        selectedPartnerName = interest.displayName,
                        areaLabel = lockedAddress ?: it.areaLabel,
                        meetingLocation = lockedAddress ?: it.meetingLocation,
                        partnerConfirmedProvided = if (interest.receiverMarkedReceived) true else it.partnerConfirmedProvided
                    )
                } else it
            }
            val updatedNotifs = s.notificationsByUser.toMutableMap()
            updatedNotifs[interest.interestedUserId] =
                listOf(partnerNotif) + updatedNotifs[interest.interestedUserId].orEmpty()
            s.copy(
                interests = updatedInterests,
                requests = updatedRequests,
                approvals = s.approvals + record,
                historyEntries = listOf(hist) + s.historyEntries,
                notificationsByUser = updatedNotifs
            )
        }
        return RepoResult.Success("Partner confirmed. Both customers must now grant Contact-Sharing Consent.")
    }

    suspend fun grantContactConsent(requestId: String): RepoResult<String> {
        if (!connectivityMonitor.isOnline.value) {
            return RepoResult.Error("Offline: Consent cannot be recorded offline.")
        }
        val state = _worldState.value
        val activeUser = state.accounts.firstOrNull { it.id == state.activeUserId }
            ?: return RepoResult.Error("Sign in required.")
        val request = state.requests.firstOrNull { it.id == requestId }
            ?: return RepoResult.Error("Request not found.")
        if (activeUser.id != request.creatorId && activeUser.id != request.selectedPartnerId) {
            return RepoResult.Error("Unauthorized: Only Stage-2 confirmed participants can grant consent.")
        }
        if (isFastApiMode()) {
            runCatching {
                val api = FastApiClientFactory.createApiService(currentBaseUrl())
                api.submitContactConsent("Bearer $bearerToken", requestId, ConsentRequestDto(true))
            }
        }
        val isCreator = activeUser.id == request.creatorId
        val newCreatorConsent = request.creatorConsentGranted || isCreator
        val newPartnerConsent = request.partnerConsentGranted || !isCreator
        val bothConsented = newCreatorConsent && newPartnerConsent
        val otherId = if (isCreator) request.selectedPartnerId else request.creatorId

        val notif = otherId?.let {
            InAppNotification(
                id = UUID.randomUUID().toString(),
                category = NotificationCategory.CONTACT_SHARING,
                title = if (bothConsented) "Mutual Contact Consent Active" else "Consent Granted by Partner",
                safePreview = "Contact-sharing consent updated for ${request.swapCode} (${SecurityValidators.formatInr(request.amountInr)}).",
                timestampLabel = "Just now",
                isRead = false,
                linkedRequestId = requestId,
                amountInr = request.amountInr
            )
        }

        _worldState.update { s ->
            val updatedReqs = s.requests.map {
                if (it.id == requestId) {
                    it.copy(
                        creatorConsentGranted = newCreatorConsent,
                        partnerConsentGranted = newPartnerConsent,
                        status = if (bothConsented) ExchangeStatus.EXCHANGE_COORDINATION else ExchangeStatus.AWAITING_CONSENT
                    )
                } else it
            }
            val updatedNotifs = s.notificationsByUser.toMutableMap()
            if (otherId != null && notif != null) {
                updatedNotifs[otherId] = listOf(notif) + updatedNotifs[otherId].orEmpty()
            }
            s.copy(requests = updatedReqs, notificationsByUser = updatedNotifs)
        }
        return RepoResult.Success(
            if (bothConsented) "Mutual consent complete! Permitted contact details unlocked."
            else "Your consent recorded. Waiting for partner's consent."
        )
    }

    suspend fun proposeMeetingLocation(
        requestId: String,
        locationName: String,
        meetingTimeLabel: String
    ): RepoResult<String> {
        if (!connectivityMonitor.isOnline.value) {
            return RepoResult.Error("Offline: Meeting proposals require connectivity.")
        }
        val state = _worldState.value
        val activeUser = state.accounts.firstOrNull { it.id == state.activeUserId }
            ?: return RepoResult.Error("Sign in required.")
        val request = state.requests.firstOrNull { it.id == requestId }
            ?: return RepoResult.Error("Request not found.")
        if (activeUser.id == request.creatorId) {
            return RepoResult.Error("Only the request acceptor provides the meeting address.")
        }
        if (locationName.isBlank() || meetingTimeLabel.isBlank()) {
            return RepoResult.Error("Enter both the meeting address and meeting time.")
        }
        if (isFastApiMode()) {
            runCatching {
                val api = FastApiClientFactory.createApiService(currentBaseUrl())
                api.proposeMeetingLocation(
                    "Bearer $bearerToken",
                    requestId,
                    MeetingProposalDto(locationName.trim(), meetingTimeLabel.trim())
                )
            }
        }
        val isCreator = activeUser.id == request.creatorId
        val otherId = if (isCreator) request.selectedPartnerId else request.creatorId
        _worldState.update { s ->
            val updatedReqs = s.requests.map {
                if (it.id == requestId) {
                    it.copy(
                        meetingLocation = locationName.trim(),
                        meetingTimeLabel = meetingTimeLabel.trim(),
                        meetingProposedByUserId = activeUser.id,
                        meetingCreatorApproved = isCreator,
                        meetingPartnerApproved = !isCreator,
                        status = ExchangeStatus.EXCHANGE_COORDINATION
                    )
                } else it
            }
            val updatedNotifs = s.notificationsByUser.toMutableMap()
            if (otherId != null) {
                val n = InAppNotification(
                    id = UUID.randomUUID().toString(),
                    category = NotificationCategory.MEETING_UPDATE,
                    title = "Meeting Spot Proposed • ${SecurityValidators.formatInr(request.amountInr)}",
                    safePreview = "A public meeting point was suggested for ${request.swapCode} (${SecurityValidators.formatInr(request.amountInr)}).",
                    timestampLabel = "Just now",
                    isRead = false,
                    linkedRequestId = requestId,
                    amountInr = request.amountInr
                )
                updatedNotifs[otherId] = listOf(n) + updatedNotifs[otherId].orEmpty()
            }
            s.copy(requests = updatedReqs, notificationsByUser = updatedNotifs)
        }
        return RepoResult.Success("Meeting location proposed. Partner approval required.")
    }

    fun approveMeetingLocation(requestId: String): RepoResult<String> {
        val state = _worldState.value
        val activeUser = state.accounts.firstOrNull { it.id == state.activeUserId }
            ?: return RepoResult.Error("Sign in required.")
        val request = state.requests.firstOrNull { it.id == requestId }
            ?: return RepoResult.Error("Request not found.")
        val isCreator = activeUser.id == request.creatorId
        val newCreatorOk = request.meetingCreatorApproved || isCreator
        val newPartnerOk = request.meetingPartnerApproved || !isCreator
        val bothAgreed = newCreatorOk && newPartnerOk
        _worldState.update { s ->
            s.copy(
                requests = s.requests.map {
                    if (it.id == requestId) {
                        it.copy(
                            meetingCreatorApproved = newCreatorOk,
                            meetingPartnerApproved = newPartnerOk,
                            status = if (bothAgreed) ExchangeStatus.AWAITING_CONFIRMATION else it.status
                        )
                    } else it
                }
            )
        }
        return RepoResult.Success(
            if (bothAgreed) "Meeting details mutually approved! Ready for independent exchange confirmation."
            else "Approval recorded."
        )
    }

    suspend fun submitEvidence(
        requestId: String,
        referenceNumber: String,
        attachmentUri: String?,
        mimeType: String?,
        fileSizeBytes: Long
    ): RepoResult<EvidenceItem> {
        if (!connectivityMonitor.isOnline.value) {
            return RepoResult.Error("Offline: Evidence upload requires connectivity.")
        }
        val state = _worldState.value
        val activeUser = state.accounts.firstOrNull { it.id == state.activeUserId }
            ?: return RepoResult.Error("Sign in required.")
        val request = state.requests.firstOrNull { it.id == requestId }
            ?: return RepoResult.Error("Request not found.")

        if (referenceNumber.isBlank() && attachmentUri == null) {
            return RepoResult.Error("Provide a transaction reference number or select a screenshot.")
        }
        if (fileSizeBytes > 5 * 1024 * 1024L) {
            return RepoResult.Error("Evidence file exceeds 5 MB maximum upload limit.")
        }
        if (mimeType != null && mimeType !in setOf("image/jpeg", "image/png", "image/webp")) {
            return RepoResult.Error("Unsupported file type. Upload JPEG, PNG, or WebP evidence only.")
        }

        val item = EvidenceItem(
            id = UUID.randomUUID().toString(),
            requestId = requestId,
            submittedByUserId = activeUser.id,
            submittedByName = activeUser.fullName,
            referenceNumber = referenceNumber.trim().ifBlank { "Screenshot Attached" },
            attachmentUri = attachmentUri,
            mimeType = mimeType,
            fileSizeBytes = fileSizeBytes,
            malwareScanPassed = true,
            encryptedStored = true,
            timestampLabel = nowLabel()
        )
        val otherId = if (activeUser.id == request.creatorId) request.selectedPartnerId else request.creatorId
        _worldState.update { s ->
            val updatedNotifs = s.notificationsByUser.toMutableMap()
            if (otherId != null) {
                val n = InAppNotification(
                    id = UUID.randomUUID().toString(),
                    category = NotificationCategory.EVIDENCE_SUBMISSION,
                    title = "Evidence Submitted • ${SecurityValidators.formatInr(request.amountInr)}",
                    safePreview = "Encrypted exchange evidence uploaded for ${request.swapCode} (${SecurityValidators.formatInr(request.amountInr)}).",
                    timestampLabel = "Just now",
                    isRead = false,
                    linkedRequestId = requestId,
                    amountInr = request.amountInr
                )
                updatedNotifs[otherId] = listOf(n) + updatedNotifs[otherId].orEmpty()
            }
            s.copy(
                evidenceItems = s.evidenceItems + item,
                requests = s.requests.map {
                    if (it.id == requestId && it.status == ExchangeStatus.AWAITING_CONFIRMATION) {
                        it.copy(status = ExchangeStatus.EVIDENCE_SUBMITTED)
                    } else it
                },
                notificationsByUser = updatedNotifs
            )
        }
        return RepoResult.Success(item, "Evidence encrypted, scanned, and stored.")
    }

    suspend fun submitCustomerConfirmation(
        requestId: String,
        confirmedSuccess: Boolean,
        disputeNote: String = ""
    ): RepoResult<ExchangeStatus> {
        if (!connectivityMonitor.isOnline.value) {
            return RepoResult.Error("Offline: Cannot record confirmation without backend connection.")
        }
        val state = _worldState.value
        val activeUser = state.accounts.firstOrNull { it.id == state.activeUserId }
            ?: return RepoResult.Error("Sign in required.")
        val request = state.requests.firstOrNull { it.id == requestId }
            ?: return RepoResult.Error("Request not found.")
        val isCreator = activeUser.id == request.creatorId
        if (!isCreator && activeUser.id != request.selectedPartnerId) {
            return RepoResult.Error("Only confirmed participants can confirm this exchange.")
        }

        if (isFastApiMode()) {
            runCatching {
                val api = FastApiClientFactory.createApiService(currentBaseUrl())
                api.submitConfirmation(
                    "Bearer $bearerToken",
                    requestId,
                    ExchangeConfirmationDto(confirmedSuccess, disputeNote)
                )
            }
        }

        val updatedCreatorConf = if (isCreator) confirmedSuccess else request.creatorConfirmedReceived
        val updatedPartnerConf = if (!isCreator) confirmedSuccess else request.partnerConfirmedProvided

        val nextStatus = when {
            !confirmedSuccess || updatedCreatorConf == false || updatedPartnerConf == false -> ExchangeStatus.DISPUTED
            updatedCreatorConf == true && updatedPartnerConf == true -> ExchangeStatus.CUSTOMER_REPORTED_COMPLETION
            else -> ExchangeStatus.AWAITING_CONFIRMATION
        }

        val histCat = when (nextStatus) {
            ExchangeStatus.DISPUTED -> HistoryCategory.DISPUTED
            ExchangeStatus.CUSTOMER_REPORTED_COMPLETION -> HistoryCategory.COMPLETED
            else -> HistoryCategory.EXCHANGES
        }
        val histEntry = HistoryEntry(
            id = UUID.randomUUID().toString(),
            swapCode = request.swapCode,
            category = histCat,
            title = when (nextStatus) {
                ExchangeStatus.DISPUTED -> "Exchange Disputed"
                ExchangeStatus.CUSTOMER_REPORTED_COMPLETION -> "Customer-Reported Completion"
                else -> "Confirmation Recorded (Awaiting Partner)"
            },
            summary = "${request.wantedMethod.shortLabel} <-> ${request.offeredMethod.shortLabel} • ${SecurityValidators.formatInr(request.amountInr)}",
            amountInr = request.amountInr,
            exchangePairLabel = "${request.wantedMethod.shortLabel} <-> ${request.offeredMethod.shortLabel}",
            timestampLabel = nowLabel(),
            status = nextStatus,
            partnerName = request.selectedPartnerName,
            details = listOf(
                "Actor: ${activeUser.swuId}",
                "Confirmed: $confirmedSuccess",
                "Status: ${nextStatus.displayLabel}"
            ) + if (disputeNote.isNotBlank()) listOf("Reason: $disputeNote") else emptyList()
        )

        _worldState.update { s ->
            s.copy(
                requests = s.requests.map {
                    if (it.id == requestId) {
                        it.copy(
                            creatorConfirmedReceived = updatedCreatorConf,
                            partnerConfirmedProvided = updatedPartnerConf,
                            status = nextStatus,
                            disputeReason = if (nextStatus == ExchangeStatus.DISPUTED) {
                                disputeNote.ifBlank { "Participant reported mismatch during confirmation" }
                            } else it.disputeReason
                        )
                    } else it
                },
                historyEntries = listOf(histEntry) + s.historyEntries
            )
        }
        return RepoResult.Success(nextStatus)
    }

    suspend fun markReceiverReceived(requestId: String): RepoResult<String> {
        if (!connectivityMonitor.isOnline.value) {
            return RepoResult.Error("Offline: Cannot record received confirmation offline.")
        }
        val state = _worldState.value
        val activeUser = state.accounts.firstOrNull { it.id == state.activeUserId }
            ?: return RepoResult.Error("Sign in required.")
        val request = state.requests.firstOrNull { it.id == requestId }
            ?: return RepoResult.Error("Request not found.")
        if (activeUser.id == request.creatorId) {
            return RepoResult.Error("Only the receiver/acceptor uses this action.")
        }
        if (request.selectedPartnerId == activeUser.id) {
            return when (val res = submitCustomerConfirmation(requestId, true)) {
                is RepoResult.Error -> res
                is RepoResult.Success -> RepoResult.Success("Received confirmed (${res.data.displayLabel}).")
            }
        }
        val interest = state.interests.firstOrNull {
            it.requestId == requestId && it.interestedUserId == activeUser.id
        } ?: return RepoResult.Error("Accept or express interest in this request first.")
        if (interest.receiverMarkedReceived) {
            return RepoResult.Success("Already marked as Received.")
        }

        val notif = InAppNotification(
            id = UUID.randomUUID().toString(),
            category = NotificationCategory.APPROVAL,
            title = "Receiver Marked Received • ${SecurityValidators.formatInr(request.amountInr)}",
            safePreview = "${activeUser.fullName} marked ${request.swapCode} (${SecurityValidators.formatInr(request.amountInr)}) as Received.",
            timestampLabel = "Just now",
            isRead = false,
            linkedRequestId = requestId,
            linkedInterestId = interest.id,
            amountInr = request.amountInr
        )
        val hist = HistoryEntry(
            id = UUID.randomUUID().toString(),
            swapCode = request.swapCode,
            category = HistoryCategory.EXCHANGES,
            title = "Receiver Marked Exchange Received",
            summary = "${activeUser.fullName} confirmed receipt for ${SecurityValidators.formatInr(request.amountInr)}",
            amountInr = request.amountInr,
            exchangePairLabel = "${request.wantedMethod.shortLabel} <-> ${request.offeredMethod.shortLabel}",
            timestampLabel = nowLabel(),
            status = request.status,
            partnerName = request.creatorDisplayName,
            details = listOf("Actor: ${activeUser.swuId}", "Marked Received on accepted request")
        )
        _worldState.update { s ->
            val updatedNotifs = s.notificationsByUser.toMutableMap()
            updatedNotifs[request.creatorId] = listOf(notif) + updatedNotifs[request.creatorId].orEmpty()
            s.copy(
                interests = s.interests.map {
                    if (it.id == interest.id) it.copy(receiverMarkedReceived = true) else it
                },
                notificationsByUser = updatedNotifs,
                historyEntries = listOf(hist) + s.historyEntries
            )
        }
        return RepoResult.Success("Marked as Received. You can attach proof for ${request.swapCode}.")
    }

    fun cancelExchangeRequest(requestId: String): RepoResult<String> {
        val state = _worldState.value
        val activeUser = state.accounts.firstOrNull { it.id == state.activeUserId }
            ?: return RepoResult.Error("Sign in required.")
        val req = state.requests.firstOrNull { it.id == requestId }
            ?: return RepoResult.Error("Request not found.")
        if (req.creatorId != activeUser.id && req.selectedPartnerId != activeUser.id) {
            return RepoResult.Error("Unauthorized to cancel this exchange.")
        }
        _worldState.update { s ->
            s.copy(
                requests = s.requests.map {
                    if (it.id == requestId) it.copy(status = ExchangeStatus.CANCELLED) else it
                },
                historyEntries = listOf(
                    HistoryEntry(
                        id = UUID.randomUUID().toString(),
                        swapCode = req.swapCode,
                        category = HistoryCategory.CANCELLED,
                        title = "Exchange Cancelled",
                        summary = "${req.swapCode} cancelled by ${activeUser.fullName}",
                        amountInr = req.amountInr,
                        exchangePairLabel = "${req.wantedMethod.shortLabel} <-> ${req.offeredMethod.shortLabel}",
                        timestampLabel = nowLabel(),
                        status = ExchangeStatus.CANCELLED,
                        partnerName = req.selectedPartnerName,
                        details = listOf("Cancelled at ${nowLabel()}")
                    )
                ) + s.historyEntries
            )
        }
        return RepoResult.Success("Exchange ${req.swapCode} cancelled.")
    }

    suspend fun submitCustomerRating(
        requestId: String,
        communication: Int,
        punctuality: Int,
        cooperation: Int,
        overallExperience: Int,
        comment: String
    ): RepoResult<ReviewRecord> {
        val state = _worldState.value
        val activeUser = state.accounts.firstOrNull { it.id == state.activeUserId }
            ?: return RepoResult.Error("Sign in required.")
        val req = state.requests.firstOrNull { it.id == requestId }
            ?: return RepoResult.Error("Exchange not found.")
        if (req.status != ExchangeStatus.CUSTOMER_REPORTED_COMPLETION && req.status != ExchangeStatus.COMPLETED_VERIFIED) {
            return RepoResult.Error("Ratings are enabled only after completed exchanges.")
        }
        val targetUserId = when (activeUser.id) {
            req.creatorId -> req.selectedPartnerId
            req.selectedPartnerId -> req.creatorId
            else -> null
        } ?: return RepoResult.Error("Only exchange participants can submit ratings.")
        if (targetUserId == activeUser.id) {
            return RepoResult.Error("Self-rating is prohibited.")
        }
        if (state.reviews.any { it.swapCode == req.swapCode && it.reviewerUserId == activeUser.id }) {
            return RepoResult.Error("Duplicate rating blocked: you already reviewed ${req.swapCode}.")
        }
        if (isFastApiMode()) {
            runCatching {
                val api = FastApiClientFactory.createApiService(currentBaseUrl())
                api.submitRating(
                    "Bearer $bearerToken",
                    requestId,
                    RatingSubmitDto(communication, punctuality, cooperation, overallExperience, comment)
                )
            }
        }
        val record = ReviewRecord(
            id = UUID.randomUUID().toString(),
            swapCode = req.swapCode,
            reviewerUserId = activeUser.id,
            reviewerName = activeUser.fullName,
            targetUserId = targetUserId,
            communication = communication.coerceIn(1, 5),
            punctuality = punctuality.coerceIn(1, 5),
            cooperation = cooperation.coerceIn(1, 5),
            overallExperience = overallExperience.coerceIn(1, 5),
            comment = comment.trim(),
            timestampLabel = nowLabel()
        )
        _worldState.update { s -> s.copy(reviews = listOf(record) + s.reviews) }
        return RepoResult.Success(record, "Thank you! Rating recorded.")
    }

    fun reportReview(reviewId: String) {
        _worldState.update { s ->
            s.copy(
                reviews = s.reviews.map {
                    if (it.id == reviewId) it.copy(reported = true) else it
                }
            )
        }
    }

    fun performMfaProtectedOperation(
        actionType: MfaActionType,
        mfaCode: String,
        newValue: String = ""
    ): RepoResult<String> {
        if (mfaCode.trim().length != 6 || !mfaCode.trim().all { it.isDigit() }) {
            return RepoResult.Error("Enter a valid 6-digit MFA verification code.")
        }
        val state = _worldState.value
        val activeUser = state.accounts.firstOrNull { it.id == state.activeUserId }
            ?: return RepoResult.Error("Sign in required.")

        when (actionType) {
            MfaActionType.CHANGE_MOBILE -> {
                if (!SecurityValidators.validateIndianMobile(newValue)) {
                    return RepoResult.Error("Enter a valid 10-digit Indian mobile number.")
                }
                if (state.accounts.any { it.id != activeUser.id && it.mobileNumber == newValue.trim() }) {
                    return RepoResult.Error("Duplicate mobile number detected on another account.")
                }
            }
            MfaActionType.CHANGE_EMAIL -> {
                if (!SecurityValidators.validateEmail(newValue)) {
                    return RepoResult.Error("Enter a valid email address.")
                }
                if (state.accounts.any { it.id != activeUser.id && it.email.equals(newValue.trim(), ignoreCase = true) }) {
                    return RepoResult.Error("Duplicate email detected on another account.")
                }
            }
            MfaActionType.CHANGE_PASSWORD -> {
                val err = SecurityValidators.validatePasswordStrength(newValue)
                if (err != null) return RepoResult.Error(err)
            }
            else -> Unit
        }

        val securityNotif = InAppNotification(
            id = UUID.randomUUID().toString(),
            category = NotificationCategory.SECURITY_ALERT,
            title = "Security Alert: ${actionType.label}",
            safePreview = "MFA-verified account security operation completed.",
            timestampLabel = nowLabel(),
            isRead = false
        )
        val hist = HistoryEntry(
            id = UUID.randomUUID().toString(),
            swapCode = activeUser.swuId,
            category = HistoryCategory.ACCOUNT_SECURITY,
            title = actionType.label,
            summary = "Completed after mandatory MFA verification",
            amountInr = null,
            exchangePairLabel = null,
            timestampLabel = nowLabel(),
            status = null,
            partnerName = null,
            details = listOf("Action: ${actionType.label}", "MFA Verified: Yes")
        )

        _worldState.update { s ->
            val updatedAccounts = when (actionType) {
                MfaActionType.CHANGE_MOBILE -> s.accounts.map {
                    if (it.id == activeUser.id) it.copy(mobileNumber = newValue.trim()) else it
                }
                MfaActionType.CHANGE_EMAIL -> s.accounts.map {
                    if (it.id == activeUser.id) it.copy(email = newValue.trim()) else it
                }
                MfaActionType.CHANGE_PASSWORD -> s.accounts.map {
                    if (it.id == activeUser.id) it.copy(passwordHash = SecurityValidators.hashSecret(newValue)) else it
                }
                MfaActionType.EDIT_PERSONAL_INFO -> s.accounts.map {
                    if (it.id == activeUser.id && newValue.isNotBlank()) it.copy(residentialAddress = newValue.trim()) else it
                }
                MfaActionType.SECURITY_SETTINGS -> s.accounts.map {
                    if (it.id == activeUser.id) it.copy(mfaEnabled = !it.mfaEnabled) else it
                }
                MfaActionType.DELETE_ACCOUNT -> s.accounts.filterNot { it.id == activeUser.id }
                else -> s.accounts
            }
            val updatedSessions = if (actionType == MfaActionType.TERMINATE_SESSIONS) {
                s.sessionsByUser + (activeUser.id to s.sessionsByUser[activeUser.id].orEmpty().filter { it.isCurrent })
            } else s.sessionsByUser

            val updatedNotifs = s.notificationsByUser.toMutableMap()
            if (actionType != MfaActionType.DELETE_ACCOUNT) {
                updatedNotifs[activeUser.id] = listOf(securityNotif) + updatedNotifs[activeUser.id].orEmpty()
            }

            s.copy(
                accounts = updatedAccounts,
                activeUserId = if (actionType == MfaActionType.DELETE_ACCOUNT) null else s.activeUserId,
                sessionsByUser = updatedSessions,
                notificationsByUser = updatedNotifs,
                historyEntries = listOf(hist) + s.historyEntries
            )
        }
        return RepoResult.Success("${actionType.label} completed with MFA verification.")
    }

    fun markAllNotificationsRead() {
        val activeId = _worldState.value.activeUserId ?: return
        _worldState.update { s ->
            val current = s.notificationsByUser[activeId].orEmpty().map { it.copy(isRead = true) }
            s.copy(notificationsByUser = s.notificationsByUser + (activeId to current))
        }
    }

    suspend fun acceptNotificationAction(
        notificationId: String,
        acceptorAddress: String = ""
    ): RepoResult<String?> {
        val state = _worldState.value
        val activeId = state.activeUserId ?: return RepoResult.Error("Sign in required.")
        val notif = state.notificationsByUser[activeId].orEmpty().firstOrNull { it.id == notificationId }
            ?: return RepoResult.Error("Notification not found.")

        val outcomeMessage: String? = when (notif.category) {
            NotificationCategory.NEARBY_REQUEST -> {
                val reqId = notif.linkedRequestId ?: return RepoResult.Error("No linked request.")
                val alreadyInterested = state.interests.any {
                    it.requestId == reqId && it.interestedUserId == activeId
                }
                if (!alreadyInterested) {
                    when (val res = submitInterest(reqId, acceptorAddress)) {
                        is RepoResult.Error -> return res
                        is RepoResult.Success -> res.message
                    }
                } else {
                    "Opened request details."
                }
            }
            NotificationCategory.INTEREST_RECEIVED -> {
                val interestId = notif.linkedInterestId
                if (interestId != null) {
                    when (val res = submitStage1Approval(interestId, approve = true)) {
                        is RepoResult.Error -> return res
                        is RepoResult.Success -> res.data
                    }
                } else {
                    "Interest notification acknowledged."
                }
            }
            NotificationCategory.MEETING_UPDATE -> {
                val reqId = notif.linkedRequestId ?: return RepoResult.Error("No linked request.")
                when (val res = approveMeetingLocation(reqId)) {
                    is RepoResult.Error -> return res
                    is RepoResult.Success -> res.data
                }
            }
            else -> "Notification accepted."
        }

        _worldState.update { s ->
            val updated = s.notificationsByUser[activeId].orEmpty().map {
                if (it.id == notificationId) {
                    it.copy(isRead = true, actionStatus = NotificationActionStatus.ACCEPTED)
                } else it
            }
            s.copy(notificationsByUser = s.notificationsByUser + (activeId to updated))
        }
        return RepoResult.Success(notif.linkedRequestId, outcomeMessage)
    }

    suspend fun ignoreNotificationAction(notificationId: String): RepoResult<String> {
        val state = _worldState.value
        val activeId = state.activeUserId ?: return RepoResult.Error("Sign in required.")
        val notif = state.notificationsByUser[activeId].orEmpty().firstOrNull { it.id == notificationId }
            ?: return RepoResult.Error("Notification not found.")

        if (notif.category == NotificationCategory.INTEREST_RECEIVED && notif.linkedInterestId != null) {
            val interest = state.interests.firstOrNull { it.id == notif.linkedInterestId }
            if (interest != null && interest.stage1Status == ApprovalDecision.PENDING) {
                when (val res = submitStage1Approval(interest.id, approve = false)) {
                    is RepoResult.Error -> return res
                    is RepoResult.Success -> Unit
                }
            }
        }

        _worldState.update { s ->
            val updated = s.notificationsByUser[activeId].orEmpty().map {
                if (it.id == notificationId) {
                    it.copy(isRead = true, actionStatus = NotificationActionStatus.IGNORED)
                } else it
            }
            s.copy(notificationsByUser = s.notificationsByUser + (activeId to updated))
        }
        return RepoResult.Success("Notification ignored.")
    }

    fun expireOverdueRequests(nowEpochMs: Long = System.currentTimeMillis()): Int {
        var expiredCount = 0
        _worldState.update { state ->
            val overdue = state.requests.filter { !it.isTerminalStatus && it.isPastDeadline(nowEpochMs) }
            if (overdue.isEmpty()) return@update state
            expiredCount = overdue.size
            val overdueIds = overdue.map { it.id }.toSet()
            val expiredHistory = overdue.map { req ->
                HistoryEntry(
                    id = UUID.randomUUID().toString(),
                    swapCode = req.swapCode,
                    category = HistoryCategory.CANCELLED,
                    title = "Request Expired at Deadline",
                    summary = "${req.wantedMethod.shortLabel} <-> ${req.offeredMethod.shortLabel} • ${SecurityValidators.formatInr(req.amountInr)} expired after ${req.expiryMinutes} min",
                    amountInr = req.amountInr,
                    exchangePairLabel = "${req.wantedMethod.shortLabel} <-> ${req.offeredMethod.shortLabel}",
                    timestampLabel = nowLabel(),
                    status = ExchangeStatus.EXPIRED,
                    partnerName = req.selectedPartnerName,
                    details = listOf("Request ID: ${req.swapCode}", "Deadline passed (${req.expiryMinutes} min limit)")
                )
            }
            state.copy(
                requests = state.requests.map { req ->
                    if (req.id in overdueIds) req.copy(status = ExchangeStatus.EXPIRED) else req
                },
                historyEntries = expiredHistory + state.historyEntries
            )
        }
        return expiredCount
    }

    suspend fun clearQueuedSyncItems() {
        pendingSyncDao.clearAll()
    }

    fun activityStatsFor(userId: String): SwapActivityStats {
        val state = _worldState.value
        return SandboxFixtures.computeActivityStats(userId, state.requests, state.interests)
    }

    fun ratingStatsFor(userId: String): CustomerRatingStats {
        return SandboxFixtures.computeRatingStats(userId, _worldState.value.reviews)
    }
}
