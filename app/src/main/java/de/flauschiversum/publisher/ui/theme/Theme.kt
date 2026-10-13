package de.flauschiversum.publisher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/** Wahl des Farbmodus in den Einstellungen. */
enum class ThemeMode { SYSTEM, LIGHT, DARK;
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

val LightColors: ColorScheme = lightColorScheme(
    primary = Nebel.Light.accent,
    onPrimary = Nebel.Light.accentInk,
    primaryContainer = Nebel.Light.accentSoft,
    onPrimaryContainer = Nebel.Light.text,
    secondary = Nebel.Light.link,
    onSecondary = Nebel.Light.accentInk,
    secondaryContainer = Nebel.Light.accentSoft,
    onSecondaryContainer = Nebel.Light.text,
    tertiary = Nebel.Light.gold,
    onTertiary = Nebel.Light.onGold,
    background = Nebel.Light.bg,
    onBackground = Nebel.Light.text,
    surface = Nebel.Light.card,
    onSurface = Nebel.Light.text,
    surfaceVariant = Nebel.Light.accentSoft,
    onSurfaceVariant = Nebel.Light.muted,
    surfaceContainerLowest = Nebel.Light.card,
    surfaceContainerLow = Nebel.Light.card,
    surfaceContainer = Nebel.Light.card,
    surfaceContainerHigh = Nebel.Light.accentSoft,
    surfaceContainerHighest = Nebel.Light.accentSoft,
    outline = Nebel.Light.borderStrong,
    outlineVariant = Nebel.Light.border,
    error = Nebel.Light.error,
    onError = Nebel.Light.onError,
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Nebel.Dark.accent,
    onPrimary = Nebel.Dark.accentInk,
    primaryContainer = Nebel.Dark.accentSoft,
    onPrimaryContainer = Nebel.Dark.text,
    secondary = Nebel.Dark.link,
    onSecondary = Nebel.Dark.accentInk,
    secondaryContainer = Nebel.Dark.accentSoft,
    onSecondaryContainer = Nebel.Dark.text,
    tertiary = Nebel.Dark.gold,
    onTertiary = Nebel.Dark.onGold,
    background = Nebel.Dark.bg,
    onBackground = Nebel.Dark.text,
    surface = Nebel.Dark.card,
    onSurface = Nebel.Dark.text,
    surfaceVariant = Nebel.Dark.accentSoft,
    onSurfaceVariant = Nebel.Dark.muted,
    surfaceContainerLowest = Nebel.Dark.bg,
    surfaceContainerLow = Nebel.Dark.card,
    surfaceContainer = Nebel.Dark.card,
    surfaceContainerHigh = Nebel.Dark.accentSoft,
    surfaceContainerHighest = Nebel.Dark.accentSoft,
    outline = Nebel.Dark.borderStrong,
    outlineVariant = Nebel.Dark.border,
    error = Nebel.Dark.error,
    onError = Nebel.Dark.onError,
)

@Composable
fun FlauschiversumPublisherTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = mode.isDark(isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
