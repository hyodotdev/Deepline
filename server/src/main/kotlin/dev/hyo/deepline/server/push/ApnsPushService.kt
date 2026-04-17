package dev.hyo.deepline.server.push

import dev.hyo.deepline.shared.model.EncryptedEnvelope
import dev.hyo.deepline.shared.model.PushToken
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Apple Push Notification service (APNs) implementation.
 *
 * Requires environment variables:
 * - DEEPLINE_APNS_KEY_PATH: Path to .p8 key file
 * - DEEPLINE_APNS_KEY_ID: Key ID from Apple Developer Console
 * - DEEPLINE_APNS_TEAM_ID: Team ID from Apple Developer Console
 * - DEEPLINE_APNS_BUNDLE_ID: App bundle identifier
 * - DEEPLINE_APNS_PRODUCTION: "true" for production, "false" for sandbox (default)
 *
 * Uses APNs HTTP/2 API with token-based authentication.
 */
class ApnsPushService(
  private val keyPath: String?,
  private val keyId: String?,
  private val teamId: String?,
  private val bundleId: String?,
  private val isProduction: Boolean,
) : PushService {

  private val logger = LoggerFactory.getLogger(ApnsPushService::class.java)
  private val json = Json { ignoreUnknownKeys = true }

  private val client = HttpClient(OkHttp) {
    install(ContentNegotiation) {
      json(json)
    }
  }

  // Cache for JWT token
  @Volatile
  private var jwtToken: String? = null

  @Volatile
  private var tokenGeneratedAt: Long = 0

  private val apnsHost: String
    get() = if (isProduction) {
      "https://api.push.apple.com"
    } else {
      "https://api.sandbox.push.apple.com"
    }

  override suspend fun notifyNewMessage(envelope: EncryptedEnvelope, tokens: List<PushToken>) {
    if (!isAvailable()) {
      logger.debug("APNs not configured, skipping push notification")
      return
    }

    val payload = ApnsPayload(
      aps = ApnsAps(
        alert = ApnsAlert(
          title = "New Message",
          body = "You have a new encrypted message",
        ),
        sound = "default",
        contentAvailable = 1,
        mutableContent = 1,
      ),
      conversationId = envelope.conversationId,
      messageId = envelope.messageId,
      senderUserId = envelope.senderUserId,
      timestamp = envelope.createdAtEpochMs,
    )

    for (pushToken in tokens) {
      try {
        sendToDevice(pushToken.token, payload)
      } catch (e: Exception) {
        logger.warn("Failed to send APNs to device ${pushToken.deviceId}: ${e.message}")
      }
    }
  }

  private suspend fun sendToDevice(deviceToken: String, payload: ApnsPayload) {
    val url = "$apnsHost/3/device/$deviceToken"
    val authToken = getJwtToken()

    client.post(url) {
      header(HttpHeaders.Authorization, "bearer $authToken")
      header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
      header("apns-topic", bundleId)
      header("apns-push-type", "alert")
      header("apns-priority", "10")
      setBody(payload)
    }
  }

  private fun getJwtToken(): String {
    val now = System.currentTimeMillis()
    // APNs tokens are valid for up to 1 hour, refresh after 50 minutes
    if (jwtToken != null && now - tokenGeneratedAt < 50 * 60 * 1000) {
      return jwtToken!!
    }

    // TODO: Implement proper JWT signing with ES256 algorithm using the .p8 key
    // This requires reading the private key and signing a JWT with:
    // - Header: {"alg": "ES256", "kid": keyId}
    // - Payload: {"iss": teamId, "iat": timestamp}
    logger.warn("APNs JWT token generation not fully implemented")
    throw UnsupportedOperationException("APNs requires proper JWT implementation")
  }

  override fun isAvailable(): Boolean {
    if (keyPath.isNullOrBlank() || keyId.isNullOrBlank() ||
      teamId.isNullOrBlank() || bundleId.isNullOrBlank()
    ) {
      return false
    }
    return File(keyPath).exists()
  }

  companion object {
    fun fromEnvironment(): ApnsPushService? {
      val keyPath = System.getenv("DEEPLINE_APNS_KEY_PATH")
      val keyId = System.getenv("DEEPLINE_APNS_KEY_ID")
      val teamId = System.getenv("DEEPLINE_APNS_TEAM_ID")
      val bundleId = System.getenv("DEEPLINE_APNS_BUNDLE_ID")
      val isProduction = System.getenv("DEEPLINE_APNS_PRODUCTION")?.toBoolean() ?: false

      if (keyPath.isNullOrBlank() || keyId.isNullOrBlank() ||
        teamId.isNullOrBlank() || bundleId.isNullOrBlank()
      ) {
        return null
      }

      return ApnsPushService(keyPath, keyId, teamId, bundleId, isProduction)
    }
  }
}

@Serializable
private data class ApnsPayload(
  val aps: ApnsAps,
  val conversationId: String,
  val messageId: String,
  val senderUserId: String,
  val timestamp: Long,
)

@Serializable
private data class ApnsAps(
  val alert: ApnsAlert,
  val sound: String? = null,
  val badge: Int? = null,
  val contentAvailable: Int? = null,
  val mutableContent: Int? = null,
)

@Serializable
private data class ApnsAlert(
  val title: String,
  val body: String,
)
