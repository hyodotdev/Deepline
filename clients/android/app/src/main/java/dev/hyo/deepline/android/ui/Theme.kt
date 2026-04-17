package dev.hyo.deepline.android.ui

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Deepline Brand Colors
private val DeeplineBlue = Color(0xFF0066FF)
private val DeeplineLightBlue = Color(0xFF3D8BFF)
private val DeeplineDarkBlue = Color(0xFF0052CC)

private val LightColors = lightColorScheme(
  primary = DeeplineBlue,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFE6F0FF),
  onPrimaryContainer = Color(0xFF001A40),
  secondary = Color(0xFF505F79),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFD6E3FF),
  onSecondaryContainer = Color(0xFF0D1D31),
  tertiary = Color(0xFF00875A),
  onTertiary = Color.White,
  tertiaryContainer = Color(0xFFB3F5D1),
  onTertiaryContainer = Color(0xFF002116),
  background = Color(0xFFF7F8FA),
  onBackground = Color(0xFF1A1D23),
  surface = Color.White,
  onSurface = Color(0xFF1A1D23),
  surfaceVariant = Color(0xFFF0F2F5),
  onSurfaceVariant = Color(0xFF44546F),
  outline = Color(0xFFDFE1E6),
  outlineVariant = Color(0xFFEBECF0),
  error = Color(0xFFDE350B),
  onError = Color.White,
  errorContainer = Color(0xFFFFEBE6),
  onErrorContainer = Color(0xFF5D1507),
  inverseSurface = Color(0xFF2C333A),
  inverseOnSurface = Color(0xFFF1F2F4),
  inversePrimary = Color(0xFF9DC3FF),
  scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
  primary = Color(0xFF579DFF),
  onPrimary = Color(0xFF00264D),
  primaryContainer = Color(0xFF0747A6),
  onPrimaryContainer = Color(0xFFD6E4FF),
  secondary = Color(0xFF9FADBC),
  onSecondary = Color(0xFF1D2125),
  secondaryContainer = Color(0xFF454F59),
  onSecondaryContainer = Color(0xFFDEE4EA),
  tertiary = Color(0xFF4ADE80),
  onTertiary = Color(0xFF003D22),
  tertiaryContainer = Color(0xFF005233),
  onTertiaryContainer = Color(0xFFB3F5D1),
  background = Color(0xFF161A1D),
  onBackground = Color(0xFFDEE4EA),
  surface = Color(0xFF1D2125),
  onSurface = Color(0xFFDEE4EA),
  surfaceVariant = Color(0xFF282E33),
  onSurfaceVariant = Color(0xFF9FADBC),
  outline = Color(0xFF454F59),
  outlineVariant = Color(0xFF333940),
  error = Color(0xFFFF6B6B),
  onError = Color(0xFF3D0800),
  errorContainer = Color(0xFF5D1507),
  onErrorContainer = Color(0xFFFFDAD4),
  inverseSurface = Color(0xFFDEE4EA),
  inverseOnSurface = Color(0xFF2C333A),
  inversePrimary = Color(0xFF0052CC),
  scrim = Color(0xFF000000),
)

private val DeeplineTypography = Typography(
  displayLarge = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 40.sp,
    letterSpacing = (-0.5).sp,
  ),
  displayMedium = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.25).sp,
  ),
  displaySmall = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 32.sp,
  ),
  headlineLarge = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
  ),
  headlineMedium = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
  ),
  headlineSmall = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 24.sp,
  ),
  titleLarge = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 17.sp,
    lineHeight = 24.sp,
  ),
  titleMedium = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.1.sp,
  ),
  titleSmall = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp,
  ),
  bodyLarge = TextStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.15.sp,
  ),
  bodyMedium = TextStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.15.sp,
  ),
  bodySmall = TextStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.2.sp,
  ),
  labelLarge = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp,
  ),
  labelMedium = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.25.sp,
  ),
  labelSmall = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.3.sp,
  ),
)

@Composable
fun DeeplineTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColors else LightColors

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      // Transparent status bar for edge-to-edge
      window.statusBarColor = AndroidColor.TRANSPARENT
      window.navigationBarColor = AndroidColor.TRANSPARENT
      WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !darkTheme
        isAppearanceLightNavigationBars = !darkTheme
      }
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = DeeplineTypography,
    content = content,
  )
}
