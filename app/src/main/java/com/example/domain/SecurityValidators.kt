package com.example.domain

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object SecurityValidators {
    const val DAILY_LIMIT_INR = 5000
    const val MAX_SINGLE_REQUEST_INR = 5000
    const val MAX_OTP_ATTEMPTS = 3

    private val istZone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val istDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val phoneRegex = Regex("(\\+?91[-\\s]?)?[6-9]\\d{9}|\\b\\d{10,12}\\b")
    private val emailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val cardOrAccountRegex = Regex("\\b\\d{12,19}\\b")
    private val restrictedKeywordRegex = Regex(
        "\\b(otp|upi\\s*pin|mpin|cvv|password|passcode|atm\\s*pin|ifsc|account\\s*no|house\\s*no|flat\\s*no)\\b",
        RegexOption.IGNORE_CASE
    )

    data class ChatValidationResult(
        val allowed: Boolean,
        val reason: String? = null
    )

    val starterPrompts: List<String> = listOf(
        "Hello.",
        "Is the request available?",
        "I am interested.",
        "Are you available?",
        "Which payment method do you prefer?"
    )

    fun currentIstDateKey(epochMs: Long = System.currentTimeMillis()): String {
        return Instant.ofEpochMilli(epochMs).atZone(istZone).toLocalDate().format(istDateFormatter)
    }

    fun calculateRemainingDailyLimit(usedTodayInr: Int): Int {
        return (DAILY_LIMIT_INR - usedTodayInr).coerceAtLeast(0)
    }

    fun validateRequestAmount(
        amountInr: Int,
        usedTodayInr: Int,
        wantedMethod: PaymentMethod,
        offeredMethod: PaymentMethod
    ): String? {
        if (!wantedMethod.legallyApprovedInSandbox || !offeredMethod.legallyApprovedInSandbox) {
            return "${if (!wantedMethod.legallyApprovedInSandbox) wantedMethod.label else offeredMethod.label} is disabled pending RBI regulatory assessment."
        }
        if (wantedMethod == offeredMethod) {
            return "Requested and offered exchange methods must differ."
        }
        if (amountInr <= 0) {
            return "Enter a valid exchange amount greater than ₹0."
        }
        if (amountInr > MAX_SINGLE_REQUEST_INR) {
            return "Maximum single request limit is ₹$MAX_SINGLE_REQUEST_INR."
        }
        val remaining = calculateRemainingDailyLimit(usedTodayInr)
        if (amountInr > remaining) {
            return "Exceeds remaining IST daily limit (₹$remaining remaining of ₹$DAILY_LIMIT_INR)."
        }
        return null
    }

    fun validateRestrictedChatMessage(message: String): ChatValidationResult {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) {
            return ChatValidationResult(false, "Message cannot be empty.")
        }
        if (phoneRegex.containsMatchIn(trimmed)) {
            return ChatValidationResult(
                false,
                "Blocked: Sharing phone numbers is prohibited before mutual Stage-2 consent."
            )
        }
        if (emailRegex.containsMatchIn(trimmed)) {
            return ChatValidationResult(
                false,
                "Blocked: Sharing email addresses is prohibited in restricted P2P chat."
            )
        }
        if (cardOrAccountRegex.containsMatchIn(trimmed)) {
            return ChatValidationResult(
                false,
                "Blocked: Sensitive bank account or card numbers are prohibited."
            )
        }
        if (restrictedKeywordRegex.containsMatchIn(trimmed)) {
            return ChatValidationResult(
                false,
                "Blocked: Never share OTPs, UPI PINs, passwords, or exact home addresses."
            )
        }
        return ChatValidationResult(true)
    }

    fun validateEmail(email: String): Boolean = emailRegex.matches(email.trim())

    fun validateIndianMobile(mobile: String): Boolean {
        val digits = mobile.filter { it.isDigit() }
        return digits.length == 10 && digits.first() in '6'..'9'
    }

    fun validatePasswordStrength(password: String): String? {
        if (password.length < 8) return "Password must be at least 8 characters."
        if (!password.any { it.isUpperCase() }) return "Include at least one uppercase letter."
        if (!password.any { it.isDigit() }) return "Include at least one digit."
        return null
    }

    fun hashSecret(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun maskMobile(mobile: String): String {
        val digits = mobile.filter { it.isDigit() }
        return if (digits.length >= 10) {
            "+91 ••••••${digits.takeLast(4)}"
        } else {
            "••••••"
        }
    }

    fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return "•••@•••"
        val name = parts[0]
        val maskedName = if (name.length <= 2) "${name.firstOrNull() ?: '*'}••" else "${name.take(2)}•••"
        return "$maskedName@${parts[1]}"
    }

    fun formatInr(amount: Int): String {
        val s = amount.toString()
        if (s.length <= 3) return "₹$s"
        val lastThree = s.takeLast(3)
        val remaining = s.dropLast(3)
        val formattedHead = remaining.reversed().chunked(2).joinToString(",").reversed()
        return "₹$formattedHead,$lastThree"
    }
}
