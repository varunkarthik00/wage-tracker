package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = GoldAccent,
    secondary = GoldAccent,
    tertiary = GoldAccent,
    background = NearBlack,
    surface = SurfaceCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    primaryContainer = GoldAccentDark,
    onPrimaryContainer = GoldAccent,
    surfaceVariant = SurfaceCardElevated,
    onSurfaceVariant = TextPrimary
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme only
  dynamicColor: Boolean = false, // Force brand styling
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
