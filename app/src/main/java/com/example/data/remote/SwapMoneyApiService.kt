package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface SwapMoneyApiService {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): Response<AuthResponseDto>

    @POST("auth/otp/send")
    suspend fun sendOtp(@Body body: OtpSendRequestDto): Response<ApiStatusResponseDto>

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body body: OtpVerifyRequestDto): Response<ApiStatusResponseDto>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): Response<AuthResponseDto>

    @POST("auth/mfa/verify")
    suspend fun verifyMfa(@Body body: MfaVerifyRequestDto): Response<AuthResponseDto>

    @POST("devices/fcm")
    suspend fun registerFcmDevice(
        @Header("Authorization") bearerToken: String,
        @Body body: FcmTokenRegisterDto
    ): Response<ApiStatusResponseDto>

    @DELETE("sessions/{sessionId}")
    suspend fun terminateSession(
        @Header("Authorization") bearerToken: String,
        @Path("sessionId") sessionId: String
    ): Response<ApiStatusResponseDto>

    @GET("limits/daily")
    suspend fun getDailyLimit(
        @Header("Authorization") bearerToken: String
    ): Response<DailyLimitDto>

    @POST("requests")
    suspend fun createExchangeRequest(
        @Header("Authorization") bearerToken: String,
        @Body body: CreateExchangeRequestDto
    ): Response<ExchangeRequestDto>

    @GET("requests/nearby")
    suspend fun getNearbyRequests(
        @Header("Authorization") bearerToken: String,
        @Query("lat") approximateLat: Double,
        @Query("lng") approximateLng: Double,
        @Query("radius") radiusMeters: Int
    ): Response<List<ExchangeRequestDto>>

    @POST("requests/{requestId}/interest")
    suspend fun submitInterest(
        @Header("Authorization") bearerToken: String,
        @Path("requestId") requestId: String
    ): Response<ApiStatusResponseDto>

    @POST("interests/{interestId}/stage1")
    suspend fun submitStage1Approval(
        @Header("Authorization") bearerToken: String,
        @Path("interestId") interestId: String,
        @Body body: ApprovalStageRequestDto
    ): Response<ApiStatusResponseDto>

    @POST("interests/{interestId}/stage2")
    suspend fun submitStage2Confirmation(
        @Header("Authorization") bearerToken: String,
        @Path("interestId") interestId: String,
        @Body body: ApprovalStageRequestDto
    ): Response<ApiStatusResponseDto>

    @POST("exchanges/{requestId}/consent")
    suspend fun submitContactConsent(
        @Header("Authorization") bearerToken: String,
        @Path("requestId") requestId: String,
        @Body body: ConsentRequestDto
    ): Response<ApiStatusResponseDto>

    @POST("exchanges/{requestId}/meeting/propose")
    suspend fun proposeMeetingLocation(
        @Header("Authorization") bearerToken: String,
        @Path("requestId") requestId: String,
        @Body body: MeetingProposalDto
    ): Response<ApiStatusResponseDto>

    @POST("exchanges/{requestId}/confirm")
    suspend fun submitConfirmation(
        @Header("Authorization") bearerToken: String,
        @Path("requestId") requestId: String,
        @Body body: ExchangeConfirmationDto
    ): Response<ApiStatusResponseDto>

    @Multipart
    @POST("exchanges/{requestId}/evidence")
    suspend fun uploadEvidence(
        @Header("Authorization") bearerToken: String,
        @Path("requestId") requestId: String,
        @Part("referenceNumber") referenceNumber: RequestBody,
        @Part file: MultipartBody.Part?
    ): Response<ApiStatusResponseDto>

    @POST("exchanges/{requestId}/rate")
    suspend fun submitRating(
        @Header("Authorization") bearerToken: String,
        @Path("requestId") requestId: String,
        @Body body: RatingSubmitDto
    ): Response<ApiStatusResponseDto>
}

object FastApiClientFactory {
    fun createOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
            redactHeader("Cookie")
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    fun createApiService(baseUrl: String): SwapMoneyApiService {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(createOkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SwapMoneyApiService::class.java)
    }
}
