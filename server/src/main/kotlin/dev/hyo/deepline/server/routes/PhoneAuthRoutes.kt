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
import java.security.MessageDigest

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

fun Route.installPhoneAuthRoutes(
  config: DeeplineServerConfig,
  store: DeeplineStore,
  rateLimiter: RateLimiter,
) {
  route("/v1/auth/phone") {
    post("/send-code") {
      val request = call.receive<SendOtpRequest>()

      // Rate limit by phone number
      enforceRateLimit(
        call = call,
        rateLimiter = rateLimiter,
        scope = "phone_send_code",
        subject = "${request.countryCode}:${request.phoneNumber}",
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

      // Generate a 6-digit OTP
      val otp = (100000..999999).random().toString()
      val otpHash = hashOtp(otp)

      val verification = store.createPhoneVerification(
        request = PhoneVerificationRequest(
          phoneNumber = request.phoneNumber,
          countryCode = request.countryCode,
        ),
        otpHash = otpHash,
      )

      // In production, send OTP via SMS here
      // For development, we'll include the OTP in the response (REMOVE IN PRODUCTION)
      val devMessage = if (!config.strictCryptoEnforcement) {
        "Development mode: OTP is $otp"
      } else {
        "Verification code sent to ${request.countryCode} ${request.phoneNumber}"
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

      // Rate limit verification attempts
      enforceRateLimit(
        call = call,
        rateLimiter = rateLimiter,
        scope = "phone_verify",
        subject = request.verificationId,
        limit = 5,
        windowSeconds = 60, // 5 attempts per minute per verification
      )

      val otpHash = hashOtp(request.otpCode)

      val result = store.verifyOtp(
        command = VerifyOtpCommand(
          verificationId = request.verificationId,
          otpCode = request.otpCode,
        ),
        otpHash = otpHash,
      )

      call.respond(result)
    }
  }
}

private fun hashOtp(otp: String): String {
  val digest = MessageDigest.getInstance("SHA-256")
  val hashBytes = digest.digest(otp.toByteArray())
  return hashBytes.joinToString("") { "%02x".format(it) }
}
