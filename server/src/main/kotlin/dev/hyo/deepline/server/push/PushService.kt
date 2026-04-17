package dev.hyo.deepline.server.push

import dev.hyo.deepline.shared.model.EncryptedEnvelope
import dev.hyo.deepline.shared.model.PushPlatform
import dev.hyo.deepline.shared.model.PushToken

/**
 * Service interface for sending push notifications.
 * Implementations handle FCM (Android) and APNs (iOS).
 */
interface PushService {
  /**
   * Send a push notification for a new message to all recipients' devices.
   *
   * @param envelope The encrypted message envelope
   * @param tokens Push tokens for devices to notify (excluding sender's device)
   */
  suspend fun notifyNewMessage(envelope: EncryptedEnvelope, tokens: List<PushToken>)

  /**
   * Check if the service is configured and available.
   */
  fun isAvailable(): Boolean
}

/**
 * Data class representing a push notification payload.
 * Contains only routing metadata; actual message content is encrypted.
 */
data class PushNotificationPayload(
  val conversationId: String,
  val messageId: String,
  val senderUserId: String,
  val timestamp: Long,
)

/**
 * No-op implementation for development when push services are not configured.
 */
class NoOpPushService : PushService {
  override suspend fun notifyNewMessage(envelope: EncryptedEnvelope, tokens: List<PushToken>) {
    // No-op: Push notifications are disabled
  }

  override fun isAvailable(): Boolean = false
}

/**
 * Composite push service that delegates to FCM and APNs services.
 */
class CompositePushService(
  private val fcmService: PushService?,
  private val apnsService: PushService?,
) : PushService {

  override suspend fun notifyNewMessage(envelope: EncryptedEnvelope, tokens: List<PushToken>) {
    val fcmTokens = tokens.filter { it.platform == PushPlatform.FCM }
    val apnsTokens = tokens.filter { it.platform == PushPlatform.APNS }

    fcmService?.let { service ->
      if (fcmTokens.isNotEmpty() && service.isAvailable()) {
        runCatching { service.notifyNewMessage(envelope, fcmTokens) }
      }
    }

    apnsService?.let { service ->
      if (apnsTokens.isNotEmpty() && service.isAvailable()) {
        runCatching { service.notifyNewMessage(envelope, apnsTokens) }
      }
    }
  }

  override fun isAvailable(): Boolean =
    (fcmService?.isAvailable() ?: false) || (apnsService?.isAvailable() ?: false)
}
