package com.example.data.repository

import com.example.domain.ActiveSession
import com.example.domain.ApprovalDecision
import com.example.domain.ApprovalRecord
import com.example.domain.ChatMessage
import com.example.domain.CustomerRatingStats
import com.example.domain.EvidenceItem
import com.example.domain.ExchangeRequest
import com.example.domain.ExchangeStatus
import com.example.domain.HistoryCategory
import com.example.domain.HistoryEntry
import com.example.domain.InAppNotification
import com.example.domain.LoginEvent
import com.example.domain.MonthlyActivityPoint
import com.example.domain.NotificationCategory
import com.example.domain.PaymentMethod
import com.example.domain.RegisteredDevice
import com.example.domain.RequestInterest
import com.example.domain.ReviewRecord
import com.example.domain.SearchRadius
import com.example.domain.SecurityValidators
import com.example.domain.SwapActivityStats
import com.example.domain.UserAccount

data class SandboxWorldState(
    val accounts: List<UserAccount>,
    val activeUserId: String?,
    val requests: List<ExchangeRequest>,
    val interests: List<RequestInterest>,
    val approvals: List<ApprovalRecord>,
    val messagesByConversation: Map<String, List<ChatMessage>>,
    val blockedPairs: Set<Pair<String, String>>,
    val reportedUserIds: Set<String>,
    val evidenceItems: List<EvidenceItem>,
    val historyEntries: List<HistoryEntry>,
    val reviews: List<ReviewRecord>,
    val notificationsByUser: Map<String, List<InAppNotification>>,
    val devicesByUser: Map<String, List<RegisteredDevice>>,
    val sessionsByUser: Map<String, List<ActiveSession>>,
    val loginEventsByUser: Map<String, List<LoginEvent>>,
    val dailyUsedByUserAndDate: Map<Pair<String, String>, Int>,
    val pendingOtpChallenge: OtpChallengeState? = null
)

data class OtpChallengeState(
    val target: String,
    val channel: String,
    val sandboxCode: String,
    val expiresAtEpochMs: Long,
    val attemptsRemaining: Int
)

object SandboxFixtures {
    fun createInitialState(): SandboxWorldState {
        val todayIst = SecurityValidators.currentIstDateKey()
        val demoPasswordHash = SecurityValidators.hashSecret("SwapMoney@2026")

        val aarav = UserAccount(
            id = "usr_aarav",
            swuId = "SWU100025",
            fullName = "Aarav Mehta",
            mobileNumber = "9876543210",
            email = "aarav.mehta@example.in",
            residentialAddress = "Flat 402, Maple Residency, 12th Main Indiranagar, Bengaluru",
            passwordHash = demoPasswordHash,
            mobileVerified = true,
            emailVerified = true,
            additionalKycVerified = true,
            mfaEnabled = true,
            authenticatorLinked = true,
            passkeyEnabled = false,
            createdAtLabel = "14 Jan 2026",
            areaLabel = "Indiranagar Metro Zone, Bengaluru",
            latitude = 12.978,
            longitude = 77.641,
            ratingAverage = 4.8,
            ratingCount = 35,
            successfulExchanges = 40
        )

        val priya = UserAccount(
            id = "usr_priya",
            swuId = "SWU100042",
            fullName = "Priya Nair",
            mobileNumber = "9845011223",
            email = "priya.nair@example.in",
            residentialAddress = "18, 100 Feet Road, HAL 2nd Stage, Bengaluru",
            passwordHash = demoPasswordHash,
            mobileVerified = true,
            emailVerified = true,
            additionalKycVerified = true,
            mfaEnabled = true,
            authenticatorLinked = false,
            passkeyEnabled = true,
            createdAtLabel = "02 Feb 2026",
            areaLabel = "100 Feet Road, Indiranagar",
            latitude = 12.975,
            longitude = 77.640,
            ratingAverage = 4.9,
            ratingCount = 24,
            successfulExchanges = 28
        )

        val rohan = UserAccount(
            id = "usr_rohan",
            swuId = "SWU100089",
            fullName = "Rohan Kulkarni",
            mobileNumber = "9900123456",
            email = "rohan.k@example.in",
            residentialAddress = "22, Domlur Layout, Bengaluru",
            passwordHash = demoPasswordHash,
            mobileVerified = true,
            emailVerified = true,
            additionalKycVerified = false,
            mfaEnabled = false,
            authenticatorLinked = false,
            passkeyEnabled = false,
            createdAtLabel = "19 Mar 2026",
            areaLabel = "Domlur Flyover Zone",
            latitude = 12.961,
            longitude = 77.638,
            ratingAverage = 4.6,
            ratingCount = 14,
            successfulExchanges = 16
        )

        val reqNearbyCash = ExchangeRequest(
            id = "req_1001",
            swapCode = "SWP-2026-000125",
            creatorId = priya.id,
            creatorSwuId = priya.swuId,
            creatorDisplayName = priya.fullName,
            creatorVerified = true,
            creatorScore = 4.9,
            creatorSuccessfulCount = 28,
            wantedMethod = PaymentMethod.CASH,
            offeredMethod = PaymentMethod.UPI,
            amountInr = 200,
            areaLabel = "Acceptor address pending",
            latitude = priya.latitude,
            longitude = priya.longitude,
            approximateDistanceMeters = 450,
            searchRadius = SearchRadius.RADIUS_1KM,
            expiryMinutes = 60,
            createdAtEpochMs = System.currentTimeMillis() - 900_000L,
            createdAtLabel = "15 mins ago",
            status = ExchangeStatus.REQUEST_CREATED,
            istDateKey = todayIst
        )

        val reqNearbyUpi = ExchangeRequest(
            id = "req_1002",
            swapCode = "SWP-2026-000126",
            creatorId = rohan.id,
            creatorSwuId = rohan.swuId,
            creatorDisplayName = rohan.fullName,
            creatorVerified = true,
            creatorScore = 4.6,
            creatorSuccessfulCount = 16,
            wantedMethod = PaymentMethod.UPI,
            offeredMethod = PaymentMethod.CASH,
            amountInr = 1200,
            areaLabel = "Acceptor address pending",
            latitude = rohan.latitude,
            longitude = rohan.longitude,
            approximateDistanceMeters = 950,
            searchRadius = SearchRadius.RADIUS_3KM,
            expiryMinutes = 90,
            createdAtEpochMs = System.currentTimeMillis() - 1_800_000L,
            createdAtLabel = "30 mins ago",
            status = ExchangeStatus.REQUEST_CREATED,
            istDateKey = todayIst
        )

        val reqCompletedPast = ExchangeRequest(
            id = "req_0998",
            swapCode = "SWP-2026-000118",
            creatorId = aarav.id,
            creatorSwuId = aarav.swuId,
            creatorDisplayName = aarav.fullName,
            creatorVerified = true,
            creatorScore = 4.8,
            creatorSuccessfulCount = 40,
            wantedMethod = PaymentMethod.CASH,
            offeredMethod = PaymentMethod.UPI,
            amountInr = 500,
            areaLabel = aarav.areaLabel,
            latitude = aarav.latitude,
            longitude = aarav.longitude,
            approximateDistanceMeters = 350,
            searchRadius = SearchRadius.RADIUS_1KM,
            expiryMinutes = 60,
            createdAtEpochMs = System.currentTimeMillis() - 86_400_000L * 3,
            createdAtLabel = "3 days ago",
            status = ExchangeStatus.CUSTOMER_REPORTED_COMPLETION,
            selectedInterestId = "int_0998",
            selectedPartnerId = priya.id,
            selectedPartnerName = priya.fullName,
            creatorConsentGranted = true,
            partnerConsentGranted = true,
            meetingLocation = "Indiranagar Metro Gate B",
            meetingTimeLabel = "14 Nov, 5:30 PM",
            meetingProposedByUserId = aarav.id,
            meetingCreatorApproved = true,
            meetingPartnerApproved = true,
            creatorConfirmedReceived = true,
            partnerConfirmedProvided = true,
            istDateKey = "2026-11-14"
        )

        val initialHistory = listOf(
            HistoryEntry(
                id = "hist_1",
                swapCode = "SWP-2026-000118",
                category = HistoryCategory.COMPLETED,
                title = "Exchange Customer-Reported Complete",
                summary = "Cash <-> UPI • ₹500 with Priya Nair",
                amountInr = 500,
                exchangePairLabel = "Cash <-> UPI",
                timestampLabel = "14 Nov 2026, 05:42 PM",
                status = ExchangeStatus.CUSTOMER_REPORTED_COMPLETION,
                partnerName = priya.fullName,
                details = listOf(
                    "Request Created: SWP-2026-000118 (₹500 Cash for UPI)",
                    "Stage 1 Shortlist Approved: Priya Nair",
                    "Stage 2 Partner Confirmed: Priya Nair",
                    "Mutual Contact Consent Granted by both parties",
                    "Meeting Agreed: Indiranagar Metro Gate B at 5:30 PM",
                    "Evidence Uploaded: Ref #UPI6319204481",
                    "Dual Confirmation Received (Customer-Reported Completion)"
                )
            ),
            HistoryEntry(
                id = "hist_2",
                swapCode = "SWP-2026-000110",
                category = HistoryCategory.CANCELLED,
                title = "Request Cancelled Before Stage 2",
                summary = "UPI <-> Cash • ₹1,000 cancelled by creator",
                amountInr = 1000,
                exchangePairLabel = "UPI <-> Cash",
                timestampLabel = "09 Nov 2026, 02:15 PM",
                status = ExchangeStatus.CANCELLED,
                partnerName = null,
                details = listOf(
                    "Request Created: SWP-2026-000110",
                    "Cancelled prior to partner confirmation"
                )
            ),
            HistoryEntry(
                id = "hist_3",
                swapCode = "SEC-2026-0041",
                category = HistoryCategory.ACCOUNT_SECURITY,
                title = "MFA Authenticator Linked",
                summary = "TOTP authenticator verified for sensitive profile operations",
                amountInr = null,
                exchangePairLabel = null,
                timestampLabel = "01 Nov 2026, 11:05 AM",
                status = null,
                partnerName = null,
                details = listOf(
                    "Authenticator app enrollment verified",
                    "Device: Pixel Android Client"
                )
            )
        )

        val initialReviews = listOf(
            ReviewRecord(
                id = "rev_1",
                swapCode = "SWP-2026-000118",
                reviewerUserId = priya.id,
                reviewerName = priya.fullName,
                targetUserId = aarav.id,
                communication = 5,
                punctuality = 5,
                cooperation = 5,
                overallExperience = 5,
                comment = "Punctual at Indiranagar Metro Gate B and prompt confirmation.",
                timestampLabel = "14 Nov 2026"
            ),
            ReviewRecord(
                id = "rev_2",
                swapCode = "SWP-2026-000094",
                reviewerUserId = rohan.id,
                reviewerName = rohan.fullName,
                targetUserId = aarav.id,
                communication = 5,
                punctuality = 4,
                cooperation = 5,
                overallExperience = 5,
                comment = "Smooth verification and clear communication in restricted chat.",
                timestampLabel = "04 Nov 2026"
            )
        )

        val initialNotificationsAarav = listOf(
            InAppNotification(
                id = "not_1",
                category = NotificationCategory.NEARBY_REQUEST,
                title = "New Nearby Match • ${SecurityValidators.formatInr(reqNearbyCash.amountInr)}",
                safePreview = "A verified user within 500 m wants ${SecurityValidators.formatInr(reqNearbyCash.amountInr)} Cash (offering UPI). Accept or open to view details.",
                timestampLabel = "15 mins ago",
                isRead = false,
                linkedRequestId = reqNearbyCash.id,
                amountInr = reqNearbyCash.amountInr
            ),
            InAppNotification(
                id = "not_2",
                category = NotificationCategory.SECURITY_ALERT,
                title = "MFA Protection Active",
                safePreview = "Your account is protected by multi-factor authentication.",
                timestampLabel = "Today, 09:00 AM",
                isRead = true
            )
        )

        val devicesMap = mapOf(
            aarav.id to listOf(
                RegisteredDevice(
                    id = "dev_1",
                    deviceName = "Android Handheld (Primary)",
                    platform = "Android 15 • FCM Active",
                    fcmRegistered = true,
                    lastActiveLabel = "Active now",
                    isCurrentDevice = true
                )
            ),
            priya.id to listOf(
                RegisteredDevice(
                    id = "dev_2",
                    deviceName = "Pixel 8 • Android",
                    platform = "Android 15 • FCM Active",
                    fcmRegistered = true,
                    lastActiveLabel = "Active now",
                    isCurrentDevice = true
                )
            ),
            rohan.id to listOf(
                RegisteredDevice(
                    id = "dev_3",
                    deviceName = "OnePlus 12 • Android",
                    platform = "Android 15 • FCM Active",
                    fcmRegistered = true,
                    lastActiveLabel = "12 mins ago",
                    isCurrentDevice = true
                )
            )
        )

        val sessionsMap = mapOf(
            aarav.id to listOf(
                ActiveSession(
                    id = "sess_1",
                    deviceName = "Android Handheld (This Device)",
                    ipMasked = "103.21.•••.42",
                    locationLabel = "Bengaluru, IN",
                    startedAtLabel = "Today, 08:40 AM",
                    isCurrent = true
                ),
                ActiveSession(
                    id = "sess_2",
                    deviceName = "Android Tablet",
                    ipMasked = "103.21.•••.88",
                    locationLabel = "Bengaluru, IN",
                    startedAtLabel = "Yesterday, 07:15 PM",
                    isCurrent = false
                )
            )
        )

        val loginEventsMap = mapOf(
            aarav.id to listOf(
                LoginEvent(
                    id = "log_1",
                    timestampLabel = "Today, 08:40 AM",
                    deviceName = "Android Handheld",
                    locationLabel = "Bengaluru, IN",
                    success = true,
                    detail = "Password + MFA verified"
                ),
                LoginEvent(
                    id = "log_2",
                    timestampLabel = "12 Nov 2026, 10:12 PM",
                    deviceName = "Unknown Client",
                    locationLabel = "Hyderabad, IN",
                    success = false,
                    detail = "Blocked — invalid OTP attempt"
                )
            )
        )

        return SandboxWorldState(
            accounts = listOf(aarav, priya, rohan),
            activeUserId = aarav.id,
            requests = listOf(reqNearbyCash, reqNearbyUpi, reqCompletedPast),
            interests = emptyList(),
            approvals = emptyList(),
            messagesByConversation = emptyMap(),
            blockedPairs = emptySet(),
            reportedUserIds = emptySet(),
            evidenceItems = emptyList(),
            historyEntries = initialHistory,
            reviews = initialReviews,
            notificationsByUser = mapOf(aarav.id to initialNotificationsAarav),
            devicesByUser = devicesMap,
            sessionsByUser = sessionsMap,
            loginEventsByUser = loginEventsMap,
            dailyUsedByUserAndDate = mapOf((aarav.id to todayIst) to 500)
        )
    }

    fun computeActivityStats(
        userId: String,
        requests: List<ExchangeRequest>,
        interests: List<RequestInterest>
    ): SwapActivityStats {
        val participatingRequestIds = interests
            .filter { it.interestedUserId == userId && it.stage2Confirmed }
            .map { it.requestId }
            .toSet()

        val relevant = requests.filter {
            it.creatorId == userId || it.selectedPartnerId == userId || it.id in participatingRequestIds
        }
        val successful = relevant.count {
            it.status == ExchangeStatus.CUSTOMER_REPORTED_COMPLETION ||
                it.status == ExchangeStatus.COMPLETED_VERIFIED
        }
        val cancelled = relevant.count { it.status == ExchangeStatus.CANCELLED }
        val disputed = relevant.count { it.status == ExchangeStatus.DISPUTED }
        val volume = relevant.filter {
            it.status == ExchangeStatus.CUSTOMER_REPORTED_COMPLETION ||
                it.status == ExchangeStatus.COMPLETED_VERIFIED
        }.sumOf { it.amountInr }

        // Include historical verified baseline plus live sandbox completions so activity chart stays realistic
        val baseTotal = 44 + relevant.size
        val baseSuccess = 39 + successful
        val baseCancelled = 2 + cancelled
        val baseDisputed = 2 + disputed
        val baseVolume = 18000 + volume

        val series = listOf(
            MonthlyActivityPoint("Jun", 5, 2200),
            MonthlyActivityPoint("Jul", 7, 3100),
            MonthlyActivityPoint("Aug", 6, 2600),
            MonthlyActivityPoint("Sep", 8, 3400),
            MonthlyActivityPoint("Oct", 7, 3200),
            MonthlyActivityPoint("Nov", 11 + relevant.size, 3500 + volume)
        )

        return SwapActivityStats(
            totalExchanges = baseTotal,
            successfulCount = baseSuccess,
            cancelledCount = baseCancelled,
            disputedCount = baseDisputed,
            thisMonthCount = 11 + relevant.size,
            totalVolumeInr = baseVolume,
            activeMonthsCount = 6,
            monthlySeries = series
        )
    }

    fun computeRatingStats(userId: String, reviews: List<ReviewRecord>): CustomerRatingStats {
        val userReviews = reviews.filter { it.targetUserId == userId }
        if (userReviews.isEmpty()) {
            return CustomerRatingStats(
                overallScore = 4.8,
                reviewCount = 35,
                communicationAvg = 4.9,
                punctualityAvg = 4.7,
                cooperationAvg = 4.8,
                overallExperienceAvg = 4.8,
                reviews = emptyList()
            )
        }
        val comm = userReviews.map { it.communication }.average()
        val punct = userReviews.map { it.punctuality }.average()
        val coop = userReviews.map { it.cooperation }.average()
        val overall = userReviews.map { it.overallExperience }.average()
        val avg = (comm + punct + coop + overall) / 4.0
        return CustomerRatingStats(
            overallScore = ((avg * 10).toInt() / 10.0),
            reviewCount = 33 + userReviews.size,
            communicationAvg = ((comm * 10).toInt() / 10.0),
            punctualityAvg = ((punct * 10).toInt() / 10.0),
            cooperationAvg = ((coop * 10).toInt() / 10.0),
            overallExperienceAvg = ((overall * 10).toInt() / 10.0),
            reviews = userReviews
        )
    }
}
