package dev.hyo.deepline.android.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.hyo.deepline.android.MainActivity
import dev.hyo.deepline.android.R

/**
 * Firebase Cloud Messaging service for handling push notifications.
 *
 * This service:
 * - Receives new FCM tokens and registers them with the Deepline server
 * - Handles incoming push notifications for new messages
 * - Displays notifications when the app is in the background
 */
class DeeplineMessagingService : FirebaseMessagingService() {

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    // Store token for later registration when user identity is ready
    savePendingFcmToken(token)
  }

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)

    val data = message.data
    val conversationId = data["conversationId"] ?: return
    val messageId = data["messageId"] ?: return
    val senderUserId = data["senderUserId"] ?: return

    // Check if the app is in the foreground
    // If so, the WebSocket connection should handle the message
    // Only show notification if app is in background
    if (!isAppInForeground()) {
      showNotification(conversationId, messageId, senderUserId)
    }
  }

  private fun showNotification(conversationId: String, messageId: String, senderUserId: String) {
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val intent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra("conversationId", conversationId)
    }

    val pendingIntent = PendingIntent.getActivity(
      this,
      conversationId.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_dialog_email) // TODO: Replace with app icon
      .setContentTitle("New Message")
      .setContentText("You have a new encrypted message")
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setAutoCancel(true)
      .setContentIntent(pendingIntent)
      .build()

    notificationManager.notify(conversationId.hashCode(), notification)
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Deepline Messages",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Notifications for new encrypted messages"
        enableVibration(true)
      }

      val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun savePendingFcmToken(token: String) {
    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
      .putString(KEY_PENDING_FCM_TOKEN, token)
      .putBoolean(KEY_TOKEN_NEEDS_REGISTRATION, true)
      .apply()
  }

  private fun isAppInForeground(): Boolean {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val appProcesses = activityManager.runningAppProcesses ?: return false

    for (process in appProcesses) {
      if (process.processName == packageName) {
        return process.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
      }
    }
    return false
  }

  companion object {
    const val CHANNEL_ID = "deepline_messages"
    const val PREFS_NAME = "deepline_fcm"
    const val KEY_PENDING_FCM_TOKEN = "pending_fcm_token"
    const val KEY_TOKEN_NEEDS_REGISTRATION = "token_needs_registration"

    /**
     * Get any pending FCM token that needs to be registered with the server.
     */
    fun getPendingFcmToken(context: Context): String? {
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      val needsRegistration = prefs.getBoolean(KEY_TOKEN_NEEDS_REGISTRATION, false)
      if (!needsRegistration) return null
      return prefs.getString(KEY_PENDING_FCM_TOKEN, null)
    }

    /**
     * Mark the FCM token as successfully registered with the server.
     */
    fun markFcmTokenRegistered(context: Context) {
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      prefs.edit()
        .putBoolean(KEY_TOKEN_NEEDS_REGISTRATION, false)
        .apply()
    }

    /**
     * Clear all FCM token data (for logout/account switch).
     */
    fun clearFcmToken(context: Context) {
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      prefs.edit().clear().apply()
    }
  }
}
