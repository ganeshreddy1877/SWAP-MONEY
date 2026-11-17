package com.example.domain

enum class PaymentMethod(
    val label: String,
    val shortLabel: String,
    val legallyApprovedInSandbox: Boolean
) {
    CASH("Cash", "Cash", true),
    UPI("UPI", "UPI", true),
    BANK_TRANSFER("Bank Transfer", "Bank Transfer", false)
}

enum class SearchRadius(val meters: Int, val label: String) {
    RADIUS_500M(500, "500 m"),
    RADIUS_1KM(1000, "1 km"),
    RADIUS_3KM(3000, "3 km")
}

enum class ExchangeStatus(val displayLabel: String) {
    REQUEST_CREATED("REQUEST CREATED"),
    INTEREST_RECEIVED("INTEREST RECEIVED"),
    FIRST_APPROVAL("FIRST APPROVAL"),
    SECOND_APPROVAL("SECOND APPROVAL"),
    AWAITING_CONSENT("AWAITING CONSENT"),
    EXCHANGE_COORDINATION("EXCHANGE COORDINATION"),
    AWAITING_CONFIRMATION("AWAITING CONFIRMATION"),
    EVIDENCE_SUBMITTED("EVIDENCE SUBMITTED"),
    UNDER_REVIEW("UNDER REVIEW"),
    CUSTOMER_REPORTED_COMPLETION("CUSTOMER-REPORTED COMPLETION"),
    COMPLETED_VERIFIED("COMPLETED / VERIFIED"),
    DISPUTED("DISPUTED"),
    CANCELLED("CANCELLED"),
    EXPIRED("EXPIRED")
}

enum class NotificationCategory(val label: String) {
    NEARBY_REQUEST("Nearby Request"),
    INTEREST_RECEIVED("Interest Received"),
    APPROVAL("Approval"),
    CHAT_MESSAGE("Chat Message"),
    CONTACT_SHARING("Contact Sharing"),
    MEETING_UPDATE("Meeting Update"),
    EVIDENCE_SUBMISSION("Evidence Submission"),
    DISPUTE("Dispute"),
    SECURITY_ALERT("Security Alert")
}

enum class HistoryCategory(val label: String) {
    ALL("All Activities"),
    REQUESTS_CREATED("Requests Created"),
    INTERESTS_SUBMITTED("Interests Submitted"),
    EXCHANGES("Exchanges"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    DISPUTED("Disputed"),
    APPROVALS("Approvals"),
    COMMUNICATION("Communication"),
    ACCOUNT_SECURITY("Account Security")
}

enum class BackendMode(val label: String) {
    COMPLIANCE_SANDBOX("Compliance Sandbox"),
    FASTAPI_SERVER("External FastAPI Server")
}

enum class MfaActionType(val label: String) {
    CHANGE_MOBILE("Change Mobile Number"),
    CHANGE_EMAIL("Change Email Address"),
    CHANGE_PASSWORD("Change Password"),
    EDIT_PERSONAL_INFO("Update Personal Information"),
    SECURITY_SETTINGS("Modify Security Settings"),
    TERMINATE_SESSIONS("Terminate Active Sessions"),
    DELETE_ACCOUNT("Delete Account"),
    ACCOUNT_RECOVERY("Account Recovery")
}

data class UserAccount(
    val id: String,
    val swuId: String,
    val fullName: String,
    val mobileNumber: String,
    val email: String,
    val residentialAddress: String,
    val passwordHash: String,
    val mobileVerified: Boolean,
    val emailVerified: Boolean,
    val additionalKycVerified: Boolean,
    val mfaEnabled: Boolean,
    val authenticatorLinked: Boolean,
    val passkeyEnabled: Boolean,
    val createdAtLabel: String,
    val areaLabel: String,
    val latitude: Double,
    val longitude: Double,
    val ratingAverage: Double,
    val ratingCount: Int,
    val successfulExchanges: Int
)

data class RegisteredDevice(
    val id: String,
    val deviceName: String,
    val platform: String,
    val fcmRegistered: Boolean,
    val lastActiveLabel: String,
    val isCurrentDevice: Boolean
)

data class ActiveSession(
    val id: String,
    val deviceName: String,
    val ipMasked: String,
    val locationLabel: String,
    val startedAtLabel: String,
    val isCurrent: Boolean
)

data class LoginEvent(
    val id: String,
    val timestampLabel: String,
    val deviceName: String,
    val locationLabel: String,
    val success: Boolean,
    val detail: String
)

data class ExchangeRequest(
    val id: String,
    val swapCode: String,
    val creatorId: String,
    val creatorSwuId: String,
    val creatorDisplayName: String,
    val creatorVerified: Boolean,
    val creatorScore: Double,
    val creatorSuccessfulCount: Int,
    val wantedMethod: PaymentMethod,
    val offeredMethod: PaymentMethod,
    val amountInr: Int,
    val areaLabel: String,
    val latitude: Double,
    val longitude: Double,
    val approximateDistanceMeters: Int,
    val searchRadius: SearchRadius,
    val expiryMinutes: Int,
    val createdAtEpochMs: Long,
    val createdAtLabel: String,
    val status: ExchangeStatus,
    val selectedInterestId: String? = null,
    val selectedPartnerId: String? = null,
    val selectedPartnerName: String? = null,
    val creatorConsentGranted: Boolean = false,
    val partnerConsentGranted: Boolean = false,
    val meetingLocation: String? = null,
    val meetingTimeLabel: String? = null,
    val meetingProposedByUserId: String? = null,
    val meetingCreatorApproved: Boolean = false,
    val meetingPartnerApproved: Boolean = false,
    val creatorConfirmedReceived: Boolean? = null,
    val partnerConfirmedProvided: Boolean? = null,
    val disputeReason: String? = null,
    val istDateKey: String = ""
) {
    val deadlineEpochMs: Long
        get() = createdAtEpochMs + expiryMinutes * 60_000L

    val isTerminalStatus: Boolean
        get() = status in setOf(
            ExchangeStatus.CUSTOMER_REPORTED_COMPLETION,
            ExchangeStatus.COMPLETED_VERIFIED,
            ExchangeStatus.CANCELLED,
            ExchangeStatus.DISPUTED,
            ExchangeStatus.EXPIRED
        )

    fun isPastDeadline(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        nowEpochMs >= deadlineEpochMs

    fun remainingMillis(nowEpochMs: Long = System.currentTimeMillis()): Long =
        (deadlineEpochMs - nowEpochMs).coerceAtLeast(0L)

    fun formatCountdown(nowEpochMs: Long = System.currentTimeMillis()): String {
        val totalSeconds = remainingMillis(nowEpochMs) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%02d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    fun remainingFraction(nowEpochMs: Long = System.currentTimeMillis()): Float {
        val totalMs = (expiryMinutes * 60_000L).coerceAtLeast(1L)
        return (remainingMillis(nowEpochMs).toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    }
}

data class RequestInterest(
    val id: String,
    val requestId: String,
    val interestedUserId: String,
    val interestedSwuId: String,
    val displayName: String,
    val verified: Boolean,
    val customerScore: Double,
    val successfulExchanges: Int,
    val approximateDistanceMeters: Int,
    val timestampLabel: String,
    val stage1Status: ApprovalDecision = ApprovalDecision.PENDING,
    val stage2Confirmed: Boolean = false,
    val conversationId: String,
    val acceptorProvidedAddress: String? = null,
    val receiverMarkedReceived: Boolean = false
)

enum class ApprovalDecision {
    PENDING,
    APPROVED,
    REJECTED
}

data class ApprovalRecord(
    val id: String,
    val requestId: String,
    val approvingUserId: String,
    val selectedUserId: String,
    val stage: Int,
    val decision: ApprovalDecision,
    val timestampLabel: String
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestampLabel: String,
    val delivered: Boolean = true,
    val flaggedByModeration: Boolean = false
)

data class EvidenceItem(
    val id: String,
    val requestId: String,
    val submittedByUserId: String,
    val submittedByName: String,
    val referenceNumber: String,
    val attachmentUri: String?,
    val mimeType: String?,
    val fileSizeBytes: Long,
    val malwareScanPassed: Boolean,
    val encryptedStored: Boolean,
    val timestampLabel: String
)

data class HistoryEntry(
    val id: String,
    val swapCode: String,
    val category: HistoryCategory,
    val title: String,
    val summary: String,
    val amountInr: Int?,
    val exchangePairLabel: String?,
    val timestampLabel: String,
    val status: ExchangeStatus?,
    val partnerName: String?,
    val details: List<String>
)

data class MonthlyActivityPoint(
    val monthLabel: String,
    val count: Int,
    val volumeInr: Int
)

data class SwapActivityStats(
    val totalExchanges: Int,
    val successfulCount: Int,
    val cancelledCount: Int,
    val disputedCount: Int,
    val thisMonthCount: Int,
    val totalVolumeInr: Int,
    val activeMonthsCount: Int,
    val monthlySeries: List<MonthlyActivityPoint>
) {
    val successRatePercent: Int
        get() = if (totalExchanges == 0) 0 else ((successfulCount * 100.0) / totalExchanges).toInt()
}

data class ReviewRecord(
    val id: String,
    val swapCode: String,
    val reviewerUserId: String,
    val reviewerName: String,
    val targetUserId: String,
    val communication: Int,
    val punctuality: Int,
    val cooperation: Int,
    val overallExperience: Int,
    val comment: String,
    val timestampLabel: String,
    val reported: Boolean = false
)

data class CustomerRatingStats(
    val overallScore: Double,
    val reviewCount: Int,
    val communicationAvg: Double,
    val punctualityAvg: Double,
    val cooperationAvg: Double,
    val overallExperienceAvg: Double,
    val reviews: List<ReviewRecord>
)

enum class NotificationActionStatus {
    PENDING,
    ACCEPTED,
    IGNORED
}

data class InAppNotification(
    val id: String,
    val category: NotificationCategory,
    val title: String,
    val safePreview: String,
    val timestampLabel: String,
    val isRead: Boolean,
    val linkedRequestId: String? = null,
    val linkedInterestId: String? = null,
    val amountInr: Int? = null,
    val actionStatus: NotificationActionStatus = NotificationActionStatus.PENDING
)

data class AppPreferences(
    val darkMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val nearbyAlertsEnabled: Boolean = true,
    val securityAlertsEnabled: Boolean = true,
    val shareApproximateLocation: Boolean = true,
    val language: String = "English (India)",
    val backendMode: BackendMode = BackendMode.COMPLIANCE_SANDBOX,
    val fastApiBaseUrl: String = com.example.BuildConfig.DEFAULT_FASTAPI_BASE_URL
)

data class PendingSyncOperation(
    val id: String,
    val idempotencyKey: String,
    val actionLabel: String,
    val payloadSummary: String,
    val createdAtEpochMs: Long
)
