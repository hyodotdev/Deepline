package dev.hyo.deepline.server.routes

import dev.hyo.deepline.server.DeeplineServerConfig
import dev.hyo.deepline.server.rate.RateLimiter
import dev.hyo.deepline.server.store.DeeplineStore
import dev.hyo.deepline.shared.model.PhoneVerificationRequest
import dev.hyo.deepline.shared.model.VerifyOtpCommand
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Serializable
data class SendOtpRequest(
  val phoneNumber: String,
  val countryCode: String,
)

@Serializable
data class SendOtpResponse(
  val verificationId: String,
  val expiresAtEpochMs: Long,
  val message: String,
)

@Serializable
data class VerifyOtpRequest(
  val verificationId: String,
  val otpCode: String,
)

/** Shared SecureRandom instance for OTP generation. Thread-safe. */
private val secureRandom = SecureRandom()

/**
 * Normalize phone number for consistent rate limiting and storage.
 * Strips all non-digit characters and normalizes country code.
 */
private fun normalizePhone(countryCode: String, phoneNumber: String): Pair<String, String> {
  val normalizedCountry = countryCode.trim().replace(Regex("[^0-9+]"), "").let {
    if (it.startsWith("+")) it else "+$it"
  }
  val normalizedPhone = phoneNumber.trim().replace(Regex("[^0-9]"), "")
  return normalizedCountry to normalizedPhone
}

fun Route.installPhoneAuthRoutes(
  config: DeeplineServerConfig,
  store: DeeplineStore,
  rateLimiter: RateLimiter,
) {
  val hmacSecret = config.otpHmacSecret.toByteArray(Charsets.UTF_8)

  route("/v1/auth/phone") {
    post("/send-code") {
      val request = call.receive<SendOtpRequest>()

      // Normalize phone input for consistent rate limiting and storage
      val (normalizedCountry, normalizedPhone) = normalizePhone(
        request.countryCode,
        request.phoneNumber,
      )

      // Rate limit by normalized phone number
      enforceRateLimit(
        call = call,
        rateLimiter = rateLimiter,
        scope = "phone_send_code",
        subject = "$normalizedCountry:$normalizedPhone",
        limit = 3,
        windowSeconds = 300, // 3 requests per 5 minutes per phone
      )

      // Also rate limit by IP
      enforceRateLimit(
        call = call,
        rateLimiter = rateLimiter,
        scope = "phone_send_code_ip",
        subject = rateLimitSubject(call, null),
        limit = 10,
        windowSeconds = 300, // 10 requests per 5 minutes per IP
      )

      // Generate a 6-digit OTP using cryptographically secure random
      val otp = (100000 + secureRandom.nextInt(900000)).toString()
      val otpHash = hmacOtp(otp, hmacSecret)

      val verification = store.createPhoneVerification(
        request = PhoneVerificationRequest(
          phoneNumber = normalizedPhone,
          countryCode = normalizedCountry,
        ),
        otpHash = otpHash,
      )

      // Only expose OTP in development/local/test environments (never in production)
      val isDevelopment = config.environment.equals("local", ignoreCase = true) ||
        config.environment.equals("development", ignoreCase = true) ||
        config.environment.equals("test", ignoreCase = true)

      val devMessage = if (isDevelopment) {
        "Development mode: OTP is $otp"
      } else {
        "Verification code sent to $normalizedCountry $normalizedPhone"
      }

      call.respond(
        SendOtpResponse(
          verificationId = verification.verificationId,
          expiresAtEpochMs = verification.expiresAtEpochMs,
          message = devMessage,
        )
      )
    }

    post("/verify") {
      val request = call.receive<VerifyOtpRequest>()

      // Rate limit verification attempts per verification ID (aligned with max_attempts in DB)
      enforceRateLimit(
        call = call,
        rateLimiter = rateLimiter,
        scope = "phone_verify",
        subject = request.verificationId,
        limit = 3, // Aligned with phone_verifications.max_attempts
        windowSeconds = 60,
      )

      // Also rate limit by IP to prevent distributed brute-force
      enforceRateLimit(
        call = call,
        rateLimiter = rateLimiter,
        scope = "phone_verify_ip",
        subject = rateLimitSubject(call, null),
        limit = 20,
        windowSeconds = 300, // 20 verify attempts per 5 minutes per IP
      )

      val otpHash = hmacOtp(request.otpCode, hmacSecret)

      val result = store.verifyOtp(
        command = VerifyOtpCommand(
          verificationId = request.verificationId,
          otpCode = "", // Don't pass plaintext OTP to avoid accidental logging
        ),
        otpHash = otpHash,
      )

      call.respond(result)
    }
  }
}

/**
 * Compute HMAC-SHA256 of OTP with server-side secret.
 * This prevents offline brute-force attacks if the DB is leaked.
 */
private fun hmacOtp(otp: String, secret: ByteArray): String {
  val mac = Mac.getInstance("HmacSHA256")
  mac.init(SecretKeySpec(secret, "HmacSHA256"))
  val hashBytes = mac.doFinal(otp.toByteArray(Charsets.UTF_8))
  return hashBytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
