package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.RepoResult
import com.example.data.repository.SwapMoneyRepository
import com.example.domain.ApprovalDecision
import com.example.domain.ExchangeStatus
import com.example.domain.MfaActionType
import com.example.domain.NotificationActionStatus
import com.example.domain.NotificationCategory
import com.example.domain.PaymentMethod
import com.example.domain.SearchRadius
import com.example.domain.SecurityValidators
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SwapMoneyWorkflowTest {

    @Test
    fun `daily IST limit rejects requests exceeding 5000 cumulative`() {
        val errSingle = SecurityValidators.validateRequestAmount(
            amountInr = 5500,
            usedTodayInr = 0,
            wantedMethod = PaymentMethod.CASH,
            offeredMethod = PaymentMethod.UPI
        )
        assertNotNull(errSingle)

        val errCumulative = SecurityValidators.validateRequestAmount(
            amountInr = 4600,
            usedTodayInr = 500,
            wantedMethod = PaymentMethod.CASH,
            offeredMethod = PaymentMethod.UPI
        )
        assertNotNull(errCumulative)

        val ok = SecurityValidators.validateRequestAmount(
            amountInr = 4500,
            usedTodayInr = 500,
            wantedMethod = PaymentMethod.CASH,
            offeredMethod = PaymentMethod.UPI
        )
        assertEquals(null, ok)
    }

    @Test
    fun `restricted chat blocks phone numbers emails OTPs and UPI PINs`() {
        assertFalse(SecurityValidators.validateRestrictedChatMessage("Call me at 9876543210").allowed)
        assertFalse(SecurityValidators.validateRestrictedChatMessage("Email test@example.com").allowed)
        assertFalse(SecurityValidators.validateRestrictedChatMessage("Share your UPI PIN").allowed)
        assertTrue(SecurityValidators.validateRestrictedChatMessage("Hello, is the request available?").allowed)
    }

    @Test
    fun `full multi party P2P workflow enforces two stage approval consent meeting and dual confirmation`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SwapMoneyRepository(context)

        // Aarav creates a ₹400 request
        val createRes = repo.createExchangeRequest(
            wantedMethod = PaymentMethod.CASH,
            offeredMethod = PaymentMethod.UPI,
            amountInr = 400,
            areaLabel = "Indiranagar Metro",
            radius = SearchRadius.RADIUS_1KM,
            expiryMinutes = 60
        )
        assertTrue(createRes is RepoResult.Success)
        val request = (createRes as RepoResult.Success).data

        // Switch to Priya and express interest
        repo.switchSandboxPersona("usr_priya")
        val intRes = repo.submitInterest(request.id)
        assertTrue(intRes is RepoResult.Success)
        val interest = (intRes as RepoResult.Success).data

        // Switch back to Aarav: Stage 2 cannot run before Stage 1
        repo.switchSandboxPersona("usr_aarav")
        val prematureStage2 = repo.submitStage2PartnerConfirmation(interest.id)
        assertTrue(prematureStage2 is RepoResult.Error)

        // Stage 1 Approval -> Stage 2 Confirmation
        assertTrue(repo.submitStage1Approval(interest.id, true) is RepoResult.Success)
        assertTrue(repo.submitStage2PartnerConfirmation(interest.id) is RepoResult.Success)

        // Mutual Contact Consent
        assertTrue(repo.grantContactConsent(request.id) is RepoResult.Success)
        repo.switchSandboxPersona("usr_priya")
        assertTrue(repo.grantContactConsent(request.id) is RepoResult.Success)

        // Mutual Meeting Coordination
        assertTrue(repo.proposeMeetingLocation(request.id, "Metro Gate B", "6:00 PM") is RepoResult.Success)
        repo.switchSandboxPersona("usr_aarav")
        assertTrue(repo.approveMeetingLocation(request.id) is RepoResult.Success)

        // Dual Independent Confirmation -> CUSTOMER_REPORTED_COMPLETION
        val firstConfirm = repo.submitCustomerConfirmation(request.id, true)
        assertEquals(ExchangeStatus.AWAITING_CONFIRMATION, (firstConfirm as RepoResult.Success).data)

        repo.switchSandboxPersona("usr_priya")
        val secondConfirm = repo.submitCustomerConfirmation(request.id, true)
        assertEquals(ExchangeStatus.CUSTOMER_REPORTED_COMPLETION, (secondConfirm as RepoResult.Success).data)
    }

    @Test
    fun `MFA protected mobile change blocks duplicate numbers and succeeds on valid code`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SwapMoneyRepository(context)

        val dupRes = repo.performMfaProtectedOperation(
            MfaActionType.CHANGE_MOBILE,
            mfaCode = "123456",
            newValue = "9845011223" // Priya's mobile
        )
        assertTrue(dupRes is RepoResult.Error)

        val okRes = repo.performMfaProtectedOperation(
            MfaActionType.CHANGE_MOBILE,
            mfaCode = "123456",
            newValue = "9876500001"
        )
        assertTrue(okRes is RepoResult.Success)
    }

    @Test
    fun `offline disconnection blocks confirmation and queues request safely`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SwapMoneyRepository(context)
        repo.connectivityMonitor.setSimulatedOfflineForTesting(true)

        val offlineRes = repo.createExchangeRequest(
            wantedMethod = PaymentMethod.CASH,
            offeredMethod = PaymentMethod.UPI,
            amountInr = 300,
            areaLabel = "Indiranagar",
            radius = SearchRadius.RADIUS_1KM,
            expiryMinutes = 60
        )
        assertTrue(offlineRes is RepoResult.Error)
        assertTrue((offlineRes as RepoResult.Error).queuedOffline)
    }

    @Test
    fun `notification accept and ignore select or reject incoming offers`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SwapMoneyRepository(context)

        // Aarav creates a request so Priya receives a NEARBY_REQUEST notification
        val req = (repo.createExchangeRequest(
            wantedMethod = PaymentMethod.CASH,
            offeredMethod = PaymentMethod.UPI,
            amountInr = 350,
            areaLabel = "Indiranagar",
            radius = SearchRadius.RADIUS_3KM,
            expiryMinutes = 60
        ) as RepoResult.Success).data

        // Priya accepts the nearby request notification -> automatically submits interest
        repo.switchSandboxPersona("usr_priya")
        val priyaNotif = repo.worldState.value.notificationsByUser["usr_priya"].orEmpty()
            .first { it.linkedRequestId == req.id && it.category == NotificationCategory.NEARBY_REQUEST }
        assertEquals(350, priyaNotif.amountInr)
        assertTrue(priyaNotif.safePreview.contains("₹350"))
        val acceptNearbyRes = repo.acceptNotificationAction(priyaNotif.id, "Indiranagar Metro Gate B")
        assertTrue(acceptNearbyRes is RepoResult.Success)
        assertEquals(
            NotificationActionStatus.ACCEPTED,
            repo.worldState.value.notificationsByUser["usr_priya"].orEmpty().first { it.id == priyaNotif.id }.actionStatus
        )

        // Aarav receives INTEREST_RECEIVED notification and ignores it -> rejects Stage 1 interest
        repo.switchSandboxPersona("usr_aarav")
        val interestNotif = repo.worldState.value.notificationsByUser["usr_aarav"].orEmpty()
            .first { it.linkedRequestId == req.id && it.category == NotificationCategory.INTEREST_RECEIVED }
        val ignoreRes = repo.ignoreNotificationAction(interestNotif.id)
        assertTrue(ignoreRes is RepoResult.Success)
        assertEquals(
            NotificationActionStatus.IGNORED,
            repo.worldState.value.notificationsByUser["usr_aarav"].orEmpty().first { it.id == interestNotif.id }.actionStatus
        )
        assertEquals(
            ApprovalDecision.REJECTED,
            repo.worldState.value.interests.first { it.id == interestNotif.linkedInterestId }.stage1Status
        )
    }

    @Test
    fun `request creator does not provide address and request acceptor provides address`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SwapMoneyRepository(context)

        // Creator creates request without providing any address
        val req = (repo.createExchangeRequest(
            wantedMethod = PaymentMethod.CASH,
            offeredMethod = PaymentMethod.UPI,
            amountInr = 450,
            areaLabel = "",
            radius = SearchRadius.RADIUS_3KM,
            expiryMinutes = 60
        ) as RepoResult.Success).data
        assertEquals("Acceptor address pending", req.areaLabel)

        // Creator is blocked from proposing/providing the meeting address
        val creatorProposeRes = repo.proposeMeetingLocation(req.id, "Creator Address", "5:00 PM")
        assertTrue(creatorProposeRes is RepoResult.Error)

        // Acceptor (Priya) expresses interest and provides the exchange address
        repo.switchSandboxPersona("usr_priya")
        val acceptorAddress = "CMH Road Metro Entrance, Indiranagar"
        val interest = (repo.submitInterest(req.id, acceptorAddress) as RepoResult.Success).data
        assertEquals(acceptorAddress, interest.acceptorProvidedAddress)

        val updatedReq = repo.worldState.value.requests.first { it.id == req.id }
        assertEquals(acceptorAddress, updatedReq.areaLabel)
        assertEquals(acceptorAddress, updatedReq.meetingLocation)
    }

    @Test
    fun `active requests are scoped to creator until deadline and expire automatically past deadline`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SwapMoneyRepository(context)

        // Aarav creates a 30-minute request
        val req = (repo.createExchangeRequest(
            wantedMethod = PaymentMethod.CASH,
            offeredMethod = PaymentMethod.UPI,
            amountInr = 600,
            areaLabel = "",
            radius = SearchRadius.RADIUS_3KM,
            expiryMinutes = 30
        ) as RepoResult.Success).data

        fun activeRequestsFor(userId: String, nowMs: Long): List<String> =
            repo.worldState.value.requests.filter { r ->
                !r.isTerminalStatus &&
                    !r.isPastDeadline(nowMs) &&
                    (r.creatorId == userId || r.selectedPartnerId == userId)
            }.map { it.id }

        val beforeDeadlineMs = req.createdAtEpochMs + 10 * 60_000L
        assertEquals("20:00", req.formatCountdown(beforeDeadlineMs))
        assertEquals("19:45", req.formatCountdown(beforeDeadlineMs + 15_000L))
        // Visible in Aarav's active requests before deadline, NOT in Priya's or Rohan's
        assertTrue(req.id in activeRequestsFor("usr_aarav", beforeDeadlineMs))
        assertFalse(req.id in activeRequestsFor("usr_priya", beforeDeadlineMs))
        assertFalse(req.id in activeRequestsFor("usr_rohan", beforeDeadlineMs))

        // After deadline (31 minutes later), request expires and is no longer active for anyone
        val afterDeadlineMs = req.createdAtEpochMs + 31 * 60_000L
        assertEquals("00:00", req.formatCountdown(afterDeadlineMs))
        val expiredCount = repo.expireOverdueRequests(afterDeadlineMs)
        assertEquals(1, expiredCount)
        assertEquals(ExchangeStatus.EXPIRED, repo.worldState.value.requests.first { it.id == req.id }.status)
        assertFalse(req.id in activeRequestsFor("usr_aarav", afterDeadlineMs))
    }

    @Test
    fun `receiver marks accepted request as received and uploads proof before and through Stage 2`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SwapMoneyRepository(context)

        // Aarav creates a request
        val req = (repo.createExchangeRequest(
            wantedMethod = PaymentMethod.CASH,
            offeredMethod = PaymentMethod.UPI,
            amountInr = 700,
            areaLabel = "",
            radius = SearchRadius.RADIUS_3KM,
            expiryMinutes = 60
        ) as RepoResult.Success).data

        // Priya accepts via notification and provides exchange address
        repo.switchSandboxPersona("usr_priya")
        val priyaNotif = repo.worldState.value.notificationsByUser["usr_priya"].orEmpty()
            .first { it.linkedRequestId == req.id && it.category == NotificationCategory.NEARBY_REQUEST }
        assertTrue(repo.acceptNotificationAction(priyaNotif.id, "Indiranagar 100ft Road") is RepoResult.Success)

        // Priya marks as Received and attaches Proof
        val receivedRes = repo.markReceiverReceived(req.id)
        assertTrue(receivedRes is RepoResult.Success)
        val interest = repo.worldState.value.interests.first { it.requestId == req.id && it.interestedUserId == "usr_priya" }
        assertTrue(interest.receiverMarkedReceived)

        val proofRes = repo.submitEvidence(req.id, "UPI-REF-998811", null, null, 0L)
        assertTrue(proofRes is RepoResult.Success)
        assertEquals(1, repo.worldState.value.evidenceItems.count { it.requestId == req.id })

        // When Aarav locks Priya in Stage 2, partnerConfirmedProvided inherits true
        repo.switchSandboxPersona("usr_aarav")
        assertTrue(repo.submitStage1Approval(interest.id, true) is RepoResult.Success)
        assertTrue(repo.submitStage2PartnerConfirmation(interest.id) is RepoResult.Success)
        assertEquals(true, repo.worldState.value.requests.first { it.id == req.id }.partnerConfirmedProvided)
    }
}
