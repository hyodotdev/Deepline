package dev.hyo.deepline.server.push

import org.slf4j.LoggerFactory

/**
 * Factory for creating and configuring push notification services.
 */
object PushServiceFactory {
  private val logger = LoggerFactory.getLogger(PushServiceFactory::class.java)

  /**
   * Create a push service from environment configuration.
   * Returns a composite service if any platform is configured, or a no-op service if none.
   */
  fun create(): PushService {
    val fcmService = try {
      FcmPushService.fromEnvironment()?.also {
        if (it.isAvailable()) {
          logger.info("FCM push service configured and available")
        } else {
          logger.info("FCM credentials path set but file not found")
        }
      }
    } catch (e: Exception) {
      logger.warn("Failed to initialize FCM service: ${e.message}")
      null
    }

    val apnsService = try {
      ApnsPushService.fromEnvironment()?.also {
        if (it.isAvailable()) {
          logger.info("APNs push service configured and available")
        } else {
          logger.info("APNs key path set but file not found")
        }
      }
    } catch (e: Exception) {
      logger.warn("Failed to initialize APNs service: ${e.message}")
      null
    }

    if (fcmService == null && apnsService == null) {
      logger.info("No push notification services configured - notifications disabled")
      return NoOpPushService()
    }

    return CompositePushService(fcmService, apnsService)
  }
}
