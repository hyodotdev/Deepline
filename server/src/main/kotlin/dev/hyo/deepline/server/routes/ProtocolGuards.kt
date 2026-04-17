package dev.hyo.deepline.server.routes

import dev.hyo.deepline.server.DeeplineServerConfig
import dev.hyo.deepline.server.rate.RateLimiter
import dev.hyo.deepline.shared.model.AttachmentMetadataCommand
import dev.hyo.deepline.shared.model.DeviceBundle
import dev.hyo.deepline.shared.model.EncryptedEnvelope
import dev.hyo.deepline.shared.model.ProtocolType
import io.ktor.server.application.ApplicationCall

internal fun rejectUnreadyGroupProtocolIfNeeded(
  config: DeeplineServerConfig,
  protocolType: ProtocolType,
) {
  if (config.strictCryptoEnforcement && protocolType == ProtocolType.MLS_GROUP) {
    throw IllegalStateException("MLS group support is not production-ready yet.")
  }
}

internal fun rejectDemoBundleIfNeeded(
  config: DeeplineServerConfig,
  bundle: DeviceBundle,
) {
  if (!config.strictCryptoEnforcement) {
    return
  }

  if (bundle.protocolVersion.startsWith("demo", ignoreCase = true)) {
    throw IllegalStateException("Strict crypto enforcement rejected a demo device bundle.")
  }
}

internal fun rejectDemoEnvelopeIfNeeded(
  config: DeeplineServerConfig,
  envelope: EncryptedEnvelope,
) {
  if (!config.strictCryptoEnforcement) {
    return
  }

  if (envelope.protocolVersion.startsWith("demo", ignoreCase = true)) {
    throw IllegalStateException("Strict crypto enforcement rejected a demo envelope protocol.")
  }

  if (envelope.encryptionMetadata["isDemo"] == "true") {
    throw IllegalStateException("Strict crypto enforcement rejected a demo encryption metadata flag.")
  }
}

internal fun rejectDemoAttachmentIfNeeded(
  config: DeeplineServerConfig,
  command: AttachmentMetadataCommand,
) {
  if (!config.strictCryptoEnforcement) {
    return
  }

  if (command.protocolVersion.startsWith("demo", ignoreCase = true)) {
    throw IllegalStateException("Strict crypto enforcement rejected demo attachment metadata.")
  }
}

internal class RateLimitExceededException(
  val retryAfterSeconds: Long,
) : IllegalStateException("Rate limit exceeded. Retry in $retryAfterSeconds seconds.")

internal fun enforceRateLimit(
  call: ApplicationCall,
  rateLimiter: RateLimiter,
  scope: String,
  subject: String,
  limit: Int,
  windowSeconds: Int,
) {
  val decision = rateLimiter.check(
    scope = scope,
    subject = subject,
    limit = limit,
    windowSeconds = windowSeconds,
  )

  if (!decision.allowed) {
    throw RateLimitExceededException(decision.retryAfterSeconds)
  }
}

internal fun rateLimitSubject(
  call: ApplicationCall,
  stableId: String? = null,
): String {
  return stableId
    ?: call.request.headers["x-forwarded-for"]
    ?: call.request.headers["x-real-ip"]
    ?: "anonymous"
}
