package com.example.meduza.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * MeduzaColors — static palette constants and utility functions.
 *
 * Static color values are used in non-Composable contexts (e.g. SettingsManager,
 * notification icons). For Composable code, prefer [LocalMeduzaColors.current]
 * which provides the user's chosen dynamic theme colors.
 *
 * Made with ❤️ by Akyyra — https://github.com/akyyra/meduza
 */

// ── Static fallback palette (used as defaults before theme loads) ──────────────
val MeduzaGreen          = Color(0xFF00FFCC)
val MeduzaNeonTurquoise  = Color(0xFF00FFCC)
val MeduzaNeonPurple     = Color(0xFF9D4EDD)
val MeduzaNeonPink       = Color(0xFFFF007F)
val MeduzaNeonCyan       = Color(0xFF00E5FF)
val MeduzaNeon           = Color(0xFF00FFCC)
val MeduzaBlack          = Color(0xFF0A0A0A)
val MeduzaSurface        = Color(0xFF181818)
val MeduzaCard           = Color(0xFF242424)
val MeduzaBorder         = Color(0xFF2A2A2A)
val MeduzaTextPrimary    = Color(0xFFEEEFF4)
val MeduzaTextSecondary  = Color(0xFFACAEB8)
val MeduzaPink           = Color(0xFFFF007F)  // Legacy brand color

// ── Static gradient (fallback before dynamic theme loads) ─────────────────────
val NeonGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFF007F),
        Color(0xFF9D4EDD),
        Color(0xFF00E5FF),
        Color(0xFF00FFCC),
        Color(0xFFFF007F),
    )
)

// ── Compat aliases — kept for backward compat ─────────────────────────────────
val SpotifyGreen     = MeduzaGreen
val SpotifyBlack     = MeduzaBlack
val SpotifyDarkGrey  = MeduzaSurface
val SpotifyLightGrey = MeduzaCard
val SpotifyTextWhite = MeduzaTextPrimary
val SpotifyTextGrey  = MeduzaTextSecondary

// ── Premium Color.kt compat aliases ───────────────────────────────────────────
val DarkAmoledBg         = Color(0xFF070709)
val DarkSurface          = Color(0xFF0F1015)
val DarkSurfaceVariant   = Color(0xFF1B1D25)
val PremiumAccent        = Color(0xFF4FC3F7)
val NeonPlay             = Color(0xFF64FFDA)
val PremiumOnBackground  = Color(0xFFE2E4EB)
val PremiumOnSurface     = Color(0xFFC4C7D0)
val PremiumMuted         = Color(0xFF8A8F9E)
val WavyProgressColor    = Color(0xAA4FC3F7)

// ── Composable accessors (dynamic — use in @Composable functions) ──────────────

/** Current theme's accent color. Use this instead of [MeduzaPink] in Composables. */
val accentColor: Color
    @Composable @ReadOnlyComposable
    get() = LocalMeduzaColors.current.accent

/** Current theme's neon gradient brush. */
val dynamicNeonGradient: Brush
    @Composable @ReadOnlyComposable
    get() = LocalMeduzaColors.current.neonGradient

// ── Thumbnail URL helpers ──────────────────────────────────────────────────────

fun thumbUrl(videoId: String): String =
    "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

fun thumbUrlFallback1(videoId: String): String =
    "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"

fun thumbUrlFallback2(videoId: String): String =
    "https://i.ytimg.com/vi/$videoId/sddefault.jpg"

// ── Gradient helpers ──────────────────────────────────────────────────────────

/** Returns a deterministic gradient based on track title + artist hash. */
fun getDeterministicGradient(title: String, artist: String): Brush {
    val hash   = (title + artist).hashCode()
    val colors = when (kotlin.math.abs(hash) % 6) {
        0    -> listOf(Color(0xFFF857A6), Color(0xFFFF5858))
        1    -> listOf(Color(0xFF00C6FF), Color(0xFF0072FF))
        2    -> listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
        3    -> listOf(Color(0xFF11998E), Color(0xFF38EF7D))
        4    -> listOf(Color(0xFFF3904F), Color(0xFF3B4371))
        else -> listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))
    }
    return Brush.verticalGradient(colors)
}

// ── Formatting helpers ─────────────────────────────────────────────────────────

/** Formats a duration in milliseconds as "m:ss". */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

// ── Search Categories ──────────────────────────────────────────────────────────

data class CategoryItem(val name: String, val query: String, val color: Color)

val SearchCategories = listOf(
    CategoryItem("Pop",        "pop hits",          Color(0xFFE1306C)),
    CategoryItem("Rock",       "rock classics",     Color(0xFF405DE6)),
    CategoryItem("Hip-hop",    "hip hop rap",       Color(0xFFFD1D1D)),
    CategoryItem("Jazz",       "jazz study relax",  Color(0xFFF77737)),
    CategoryItem("Electronic", "synthwave edm",     Color(0xFF5851DB)),
    CategoryItem("Acoustic",   "acoustic live",     Color(0xFF833AB4)),
)
