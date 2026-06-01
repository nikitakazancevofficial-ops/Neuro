package com.kazancev.ai_chat_companion.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppThemeMode(val storageValue: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromStorage(value: String?): AppThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: System
    }
}

data class NeuroPalette(
    val background: Color,
    val elevated: Color,
    val softSurface: Color,
    val drawerBackground: Color,
    val codeSurface: Color,
    val text: Color,
    val subtleText: Color,
    val mutedText: Color,
    val actionIcon: Color,
    val accent: Color,
    val accentDark: Color,
    val blue: Color,
    val userBubble: Color,
    val userText: Color,
    val inputBackground: Color,
    val thinkingBubble: Color,
    val divider: Color,
    val danger: Color
)

private val LightPalette = NeuroPalette(
    background = Color.White,
    elevated = Color.White,
    softSurface = Color(0xFFF3F0FF),
    drawerBackground = Color(0xFFFBFAFF),
    codeSurface = Color(0xFFEAE6F5),
    text = Color(0xFF17132A),
    subtleText = Color(0xFF817B96),
    mutedText = Color(0xFFB8B1CC),
    actionIcon = Color(0xFF655E7A),
    accent = Color(0xFF7657FF),
    accentDark = Color(0xFF4C34C7),
    blue = Color(0xFF397BFF),
    userBubble = Color(0xFFE6DFFF),
    userText = Color(0xFF3C278D),
    inputBackground = Color.White,
    thinkingBubble = Color(0xFFF2F2F2),
    divider = Color(0xFFE5E0F0),
    danger = Color(0xFFE5484D)
)

private val DarkPalette = NeuroPalette(
    background = Color(0xFF0C0915),
    elevated = Color(0xFF171225),
    softSurface = Color(0xFF261F3A),
    drawerBackground = Color(0xFF100C1C),
    codeSurface = Color(0xFF211B30),
    text = Color(0xFFF5F5F5),
    subtleText = Color(0xFFA3A3A3),
    mutedText = Color(0xFF6E6682),
    actionIcon = Color(0xFFD4CDED),
    accent = Color(0xFF9A82FF),
    accentDark = Color(0xFF5D45CB),
    blue = Color(0xFF6EA2FF),
    userBubble = Color(0xFF3A286B),
    userText = Color(0xFFF5F5F5),
    inputBackground = Color(0xFF151515),
    thinkingBubble = Color(0xFF211B30),
    divider = Color(0xFF392F4D),
    danger = Color(0xFFFF5A5F)
)

val LocalNeuroPalette = staticCompositionLocalOf { LightPalette }
val LocalAppThemeMode = staticCompositionLocalOf { AppThemeMode.System }
val LocalSetAppThemeMode = staticCompositionLocalOf<(AppThemeMode) -> Unit> { {} }

private const val THEME_PREFS = "neuro_theme"
private const val THEME_MODE_KEY = "mode"

fun loadAppThemeMode(context: Context): AppThemeMode =
    AppThemeMode.fromStorage(
        context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
            .getString(THEME_MODE_KEY, null)
    )

private fun saveAppThemeMode(context: Context, mode: AppThemeMode) {
    context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(THEME_MODE_KEY, mode.storageValue)
        .apply()
}

fun isDarkTheme(context: Context, mode: AppThemeMode): Boolean = when (mode) {
    AppThemeMode.System ->
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    AppThemeMode.Light -> false
    AppThemeMode.Dark -> true
}

fun applySystemBars(window: Window, darkTheme: Boolean) {
    val background = if (darkTheme) DarkPalette.background else LightPalette.background
    window.setBackgroundDrawable(ColorDrawable(background.toArgb()))
    window.statusBarColor = background.toArgb()
    window.navigationBarColor = background.toArgb()
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !darkTheme
        isAppearanceLightNavigationBars = !darkTheme
    }
}

@Composable
fun NeuroThemeHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(loadAppThemeMode(context)) }
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (mode) {
        AppThemeMode.System -> systemDark
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
    val updateMode = remember(context) {
        { nextMode: AppThemeMode ->
            saveAppThemeMode(context, nextMode)
            mode = nextMode
        }
    }

    CompositionLocalProvider(
        LocalAppThemeMode provides mode,
        LocalSetAppThemeMode provides updateMode
    ) {
        NeuroTheme(darkTheme = darkTheme, content = content)
    }
}

@Composable
fun NeuroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            secondary = palette.blue,
            background = palette.background,
            surface = palette.elevated,
            onSurface = palette.text
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            secondary = palette.blue,
            background = palette.background,
            surface = palette.elevated,
            onSurface = palette.text
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            applySystemBars((view.context as Activity).window, darkTheme)
        }
    }

    CompositionLocalProvider(LocalNeuroPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}
