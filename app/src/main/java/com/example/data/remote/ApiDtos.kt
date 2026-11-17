package com.example.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RegisterRequestDto(
    val fullName: String,
    val mobileNumber: String,
    val email: String,
    val residentialAddress: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class OtpSendRequestDto(
    val channel: String,
    val target: String
)

@JsonClass(generateAdapter = true)
data class OtpVerifyRequestDto(
    val channel: String,
    val target: String,
    val code: String
)

@JsonClass(generateAdapter = true)
data class LoginRequestDto(
    val identifier: String,
    val password: String,
    val deviceName: String
)

@JsonClass(generateAdapter = true)
data class MfaVerifyRequestDto(
    val challengeId: String,
    val code: String,
    val actionScope: String
)

@JsonClass(generateAdapter = true)
data class AuthResponseDto(
    val accessToken: String,
    val swuId: String,
    val userId: String,
    val mfaRequired: Boolean = false,
    val mfaChallengeId: String? = null
)

@JsonClass(generateAdapter = true)
data class DailyLimitDto(
    val istDateKey: String,
    val maxLimitInr: Int,
    val usedInr: Int,
    val remainingInr: Int
)

@JsonClass(generateAdapter = true)
data class CreateExchangeRequestDto(
    val wantedMethod: String,
    val offeredMethod: String,
    val amountInr: Int,
    val areaLabel: String,
    val approximateLat: Double,
    val approximateLng: Double,
    val radiusMeters: Int,
    val expiryMinutes: Int,
    val idempotencyKey: String
)

@JsonClass(generateAdapter = true)
data class ExchangeRequestDto(
    val id: String,
    val swapCode: String,
    val creatorId: String,
    val creatorSwuId: String,
    val creatorDisplayName: String,
    val creatorVerified: Boolean,
    val creatorScore: Double,
    val creatorSuccessfulCount: Int,
    val wantedMethod: String,
    val offeredMethod: String,
    val amountInr: Int,
    val areaLabel: String,
    val approximateDistanceMeters: Int,
    val radiusMeters: Int,
    val status: String,
    val createdAtIso: String
)

@JsonClass(generateAdapter = true)
data class ApprovalStageRequestDto(
    val decision: String,
    val idempotencyKey: String
)

@JsonClass(generateAdapter = true)
data class ConsentRequestDto(
    val consentGranted: Boolean
)

@JsonClass(generateAdapter = true)
data class MeetingProposalDto(
    val locationName: String,
    val meetingTimeLabel: String
)

@JsonClass(generateAdapter = true)
data class ExchangeConfirmationDto(
    val confirmed: Boolean,
    val note: String
)

@JsonClass(generateAdapter = true)
data class RatingSubmitDto(
    val communication: Int,
    val punctuality: Int,
    val cooperation: Int,
    val overallExperience: Int,
    val comment: String
)

@JsonClass(generateAdapter = true)
data class FcmTokenRegisterDto(
    val fcmToken: String,
    val deviceName: String,
    val platform: String = "android"
)

@JsonClass(generateAdapter = true)
data class ApiStatusResponseDto(
    val ok: Boolean,
    val message: String
)
