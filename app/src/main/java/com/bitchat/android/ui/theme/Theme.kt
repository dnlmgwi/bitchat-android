package com.bitchat.android.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

// Colors that match the iOS bitchat theme
private val DarkColorScheme = darkColorScheme(
    primary = Color.White,              // White (was bright green)
    onPrimary = Color.Black,
    secondary = Color(0xFFE0E0E0),      // Light gray (was darker green)
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,         // White on black
    surface = Color(0xFF111111),        // Very dark gray
    onSurface = Color.White,            // White text
    error = Color(0xFFFF5555),          // Red for errors
    onError = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = Color.Black,              // Black (was dark green)
    onPrimary = Color.White,
    secondary = Color(0xFF404040),      // Dark gray (was even darker green)
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color.Black,         // Black on white
    surface = Color(0xFFF8F8F8),        // Very light gray
    onSurface = Color.Black,            // Black text
    error = Color(0xFFCC0000),          // Dark red for errors
    onError = Color.White
)

@Composable
fun BitchatTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    // App-level override from ThemePreferenceManager
    val themePref by ThemePreferenceManager.themeFlow.collectAsState(initial = ThemePreference.System)
    val shouldUseDark = when (darkTheme) {
        true -> true
        false -> false
        null -> when (themePref) {
            ThemePreference.Dark -> true
            ThemePreference.Light -> false
            ThemePreference.System -> isSystemInDarkTheme()
        }
    }

    val colorScheme = if (shouldUseDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    if (!shouldUseDark) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (!shouldUseDark) {
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else 0
            }
            window.navigationBarColor = colorScheme.background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
