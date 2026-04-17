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
 * Firebase Cloud Messaging (FCM) push service implementation.
 *
 * Requires environment variables:
 * - DEEPLINE_FCM_CREDENTIALS_PATH: Path to Firebase service account JSON
 * - DEEPLINE_FCM_PROJECT_ID: Firebase project ID (optional, extracted from credentials if not set)
 *
 * Uses FCM HTTP v1 API with OAuth2 authentication.
 */
class FcmPushService(
  private val credentialsPath: String?,
  private val projectId: String?,
) : PushService {

  private val logger = LoggerFactory.getLogger(FcmPushService::class.java)
  private val json = Json { ignoreUnknownKeys = true }

  private val client = HttpClient(OkHttp) {
    install(ContentNegotiation) {
      json(json)
    }
  }

  // Cache for OAuth2 access token (would need proper token refresh in production)
  @Volatile
  private var accessToken: String? = null

  @Volatile
  private var tokenExpiresAt: Long = 0

  override suspend fun notifyNewMessage(envelope: EncryptedEnvelope, tokens: List<PushToken>) {
    if (!isAvailable()) {
      logger.debug("FCM not configured, skipping push notification")
      return
    }

    val payload = FcmMessage(
      message = FcmMessagePayload(
        data = mapOf(
          "conversationId" to envelope.conversationId,
          "messageId" to envelope.messageId,
          "senderUserId" to envelope.senderUserId,
          "timestamp" to envelope.createdAtEpochMs.toString(),
          "type" to "new_message",
        ),
        android = FcmAndroidConfig(
          priority = "high",
          notification = FcmNotification(
            title = "New Message",
            body = "You have a new encrypted message",
            channelId = "deepline_messages",
          ),
        ),
      ),
    )

    for (pushToken in tokens) {
      try {
        sendToToken(pushToken.token, payload)
      } catch (e: Exception) {
        logger.warn("Failed to send FCM to device ${pushToken.deviceId}: ${e.message}")
      }
    }
  }

  private suspend fun sendToToken(token: String, message: FcmMessage) {
    val messageWithToken = message.copy(
      message = message.message.copy(token = token),
    )

    val url = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send"
    val authToken = getAccessToken()

    client.post(url) {
      header(HttpHeaders.Authorization, "Bearer $authToken")
      header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
      setBody(messageWithToken)
    }
  }

  private suspend fun getAccessToken(): String {
    // In production, this should use Google OAuth2 with service account credentials
    // For now, return a placeholder that would need proper implementation
    val now = System.currentTimeMillis()
    if (accessToken != null && now < tokenExpiresAt) {
      return accessToken!!
    }

    // TODO: Implement proper OAuth2 token generation using Firebase Admin SDK
    // or manual JWT signing with the service account key
    logger.warn("FCM OAuth2 token generation not fully implemented - requires Firebase Admin SDK")
    throw UnsupportedOperationException("FCM requires proper OAuth2 implementation")
  }

  override fun isAvailable(): Boolean {
    if (credentialsPath.isNullOrBlank() || projectId.isNullOrBlank()) {
      return false
    }
    return File(credentialsPath).exists()
  }

  companion object {
    fun fromEnvironment(): FcmPushService? {
      val credentialsPath = System.getenv("DEEPLINE_FCM_CREDENTIALS_PATH")
      val projectId = System.getenv("DEEPLINE_FCM_PROJECT_ID")

      if (credentialsPath.isNullOrBlank()) {
        return null
      }

      return FcmPushService(credentialsPath, projectId)
    }
  }
}

@Serializable
private data class FcmMessage(
  val message: FcmMessagePayload,
)

@Serializable
private data class FcmMessagePayload(
  val token: String? = null,
  val data: Map<String, String>,
  val android: FcmAndroidConfig? = null,
)

@Serializable
private data class FcmAndroidConfig(
  val priority: String,
  val notification: FcmNotification? = null,
)

@Serializable
private data class FcmNotification(
  val title: String,
  val body: String,
  val channelId: String? = null,
)
