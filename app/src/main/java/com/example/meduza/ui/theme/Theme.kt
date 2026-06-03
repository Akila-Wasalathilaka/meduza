package com.example.meduza.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Composition local that provides the current [MeduzaDynamicColors] to the tree.
 * Access anywhere with `LocalMeduzaColors.current`.
 *
 * Made with ❤️ by Akyyra — https://github.com/akyyra/meduza
 */
val LocalMeduzaColors = compositionLocalOf {
    MeduzaThemeEngine.deriveColors(330f)   // Default: Meduza pink
}

/** Convenience accessor — equivalent to `LocalMeduzaColors.current`. */
val meduzaColors: MeduzaDynamicColors
    @Composable @ReadOnlyComposable
    get() = LocalMeduzaColors.current

/**
 * MeduzaTheme — the root theme composable for the entire app.
 *
 * Accepts [accentHue] (0–360°) and uses [MeduzaThemeEngine] to derive a complete
 * color system. The derived colors are both:
 *  - Provided via [LocalMeduzaColors] for direct Compose access
 *  - Mapped to Material 3's color scheme tokens for Material components
 *
 * @param accentHue Hue in degrees. Default 330° = original Meduza pink.
 * @param content   The Composable content tree.
 */
@Composable
fun MeduzaTheme(
    accentHue: Float = 330f,
    content: @Composable () -> Unit,
) {
    val colors = MeduzaThemeEngine.deriveColors(accentHue)

    val colorScheme = darkColorScheme(
        primary          = colors.accent,
        secondary        = colors.accentGlow,
        tertiary         = colors.complement,
        background       = colors.background,
        surface          = colors.surface,
        surfaceVariant   = colors.surfaceHigh,
        onPrimary        = colors.background,
        onSecondary      = colors.background,
        onTertiary       = colors.background,
        onBackground     = colors.textPrimary,
        onSurface        = colors.textPrimary,
        onSurfaceVariant = colors.textSecondary,
        outline          = colors.border,
        outlineVariant   = colors.border.copy(alpha = 0.5f),
    )

    CompositionLocalProvider(LocalMeduzaColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            content     = content,
        )
    }
}