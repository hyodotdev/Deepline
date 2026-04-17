package dev.hyo.deepline.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import dev.hyo.deepline.android.ui.DeeplineLaunchConfig
import dev.hyo.deepline.android.ui.DeeplineAndroidApp

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val launchConfig = DeeplineLaunchConfig(
      resetState = intent?.getBooleanExtra(EXTRA_RESET_STATE, false) == true,
      overrideServerUrl = intent?.getStringExtra(EXTRA_SERVER_URL),
      overrideUserId = intent?.getStringExtra(EXTRA_USER_ID),
      overrideDeviceId = intent?.getStringExtra(EXTRA_DEVICE_ID),
      overrideDisplayName = intent?.getStringExtra(EXTRA_DISPLAY_NAME),
      overrideDeviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME),
    )
    setContent {
      DeeplineAndroidApp(launchConfig = launchConfig)
    }
  }

  companion object {
    const val EXTRA_RESET_STATE = "deepline.reset_state"
    const val EXTRA_SERVER_URL = "deepline.server_url"
    const val EXTRA_USER_ID = "deepline.user_id"
    const val EXTRA_DEVICE_ID = "deepline.device_id"
    const val EXTRA_DISPLAY_NAME = "deepline.display_name"
    const val EXTRA_DEVICE_NAME = "deepline.device_name"
  }
}
