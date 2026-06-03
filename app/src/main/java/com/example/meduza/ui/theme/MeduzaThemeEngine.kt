package com.example.meduza.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * MEDUZA Dynamic Theme Engine
 *
 * Derives a complete, harmonious color system from a single accent hue (0–360°).
 * All colors are computed using HSL color theory:
 *  - Accent & glow: high saturation / medium-high lightness at the chosen hue
 *  - Complementary: opposite hue (hue + 180°) for gradients and secondary accents
 *  - Surfaces: same hue but near-zero saturation + very low lightness for dark AMOLED look
 *
 * Made with ❤️ by Akyyra — https://github.com/akyyra/meduza
 */
object MeduzaThemeEngine {

    /**
     * Derives the full MEDUZA color system from a single [hue] (0–360°).
     *
     * @param hue Accent hue in degrees. Default 330° = original Meduza pink.
     * @return [MeduzaDynamicColors] containing all derived colors and brushes.
     */
    fun deriveColors(hue: Float): MeduzaDynamicColors {
        val h = hue.mod(360f)

        // ── Primary accent — vibrant neon ──────────────────────────────────────
        val accent      = hsl(h, 0.85f, 0.62f)
        val accentDim   = hsl(h, 0.65f, 0.42f)
        val accentGlow  = hsl(h, 1.00f, 0.72f)

        // ── Complementary (180° opposite hue) — for gradients ─────────────────
        val compHue     = (h + 180f).mod(360f)
        val complement  = hsl(compHue, 0.90f, 0.68f)

        // ── Triadic secondary (120° shift) — for gradient midpoints ───────────
        val triadHue    = (h + 120f).mod(360f)
        val triad       = hsl(triadHue, 0.88f, 0.65f)

        // ── Dark surfaces — same hue, very desaturated, extremely dark ────────
        val background  = hsl(h, 0.12f, 0.04f)   // AMOLED deep
        val surface     = hsl(h, 0.09f, 0.08f)   // Card background
        val surfaceHigh = hsl(h, 0.07f, 0.12f)   // Elevated card / modal
        val border      = hsl(h, 0.08f, 0.16f)   // Subtle border

        // ── Text colors — neutral with slight warm tint ────────────────────────
        val textPrimary   = Color(0xFFEEEFF4)
        val textSecondary = Color(0xFFACAEB8)

        // ── Brushes ────────────────────────────────────────────────────────────
        val neonGradient = Brush.linearGradient(listOf(accentGlow, complement, triad, accentGlow))
        val accentGradient = Brush.linearGradient(listOf(accent, accentGlow))

        return MeduzaDynamicColors(
            accent         = accent,
            accentDim      = accentDim,
            accentGlow     = accentGlow,
            complement     = complement,
            triad          = triad,
            background     = background,
            surface        = surface,
            surfaceHigh    = surfaceHigh,
            border         = border,
            textPrimary    = textPrimary,
            textSecondary  = textSecondary,
            neonGradient   = neonGradient,
            accentGradient = accentGradient,
            hue            = h,
        )
    }

    /**
     * Converts HSL values to [Color].
     *
     * @param h Hue in degrees [0, 360)
     * @param s Saturation [0, 1]
     * @param l Lightness [0, 1]
     */
    fun hsl(h: Float, s: Float, l: Float): Color {
        val hN = h.mod(360f) / 360f
        val sC = s.coerceIn(0f, 1f)
        val lC = l.coerceIn(0f, 1f)

        if (sC == 0f) {
            val v = (lC * 255).toInt()
            return Color(v, v, v)
        }

        val q = if (lC < 0.5f) lC * (1f + sC) else lC + sC - lC * sC
        val p = 2f * lC - q

        fun hue2rgb(t: Float): Float {
            var tN = t
            if (tN < 0f) tN += 1f
            if (tN > 1f) tN -= 1f
            return when {
                tN < 1f / 6f -> p + (q - p) * 6f * tN
                tN < 1f / 2f -> q
                tN < 2f / 3f -> p + (q - p) * (2f / 3f - tN) * 6f
                else          -> p
            }
        }

        val r = (hue2rgb(hN + 1f / 3f) * 255f).toInt().coerceIn(0, 255)
        val g = (hue2rgb(hN)            * 255f).toInt().coerceIn(0, 255)
        val b = (hue2rgb(hN - 1f / 3f) * 255f).toInt().coerceIn(0, 255)

        return Color(r, g, b)
    }

    // ── Preset Palette ─────────────────────────────────────────────────────────

    /**
     * Curated preset colors for the theme picker UI.
     * Each entry is a human-readable name + hue value.
     */
    val PRESET_HUES: List<Pair<String, Float>> = listOf(
        "Red"    to 0f,
        "Orange" to 28f,
        "Amber"  to 45f,
        "Yellow" to 60f,
        "Lime"   to 80f,
        "Green"  to 135f,
        "Teal"   to 174f,
        "Sky"    to 200f,
        "Blue"   to 225f,
        "Violet" to 265f,
        "Purple" to 290f,
        "Pink"   to 330f,   // ← Default: original Meduza brand color
        "Rose"   to 348f,
    )
}

/**
 * Complete set of colors derived by [MeduzaThemeEngine.deriveColors].
 * Consumed by Composables via [LocalMeduzaColors].
 */
data class MeduzaDynamicColors(
    val accent         : Color,   // Primary vibrant accent
    val accentDim      : Color,   // Dimmed accent for borders / inactive states
    val accentGlow     : Color,   // High-lightness glow version of accent
    val complement     : Color,   // Complementary hue (180°) for gradients
    val triad          : Color,   // Triadic hue (120°) for gradient midpoints
    val background     : Color,   // AMOLED deep background
    val surface        : Color,   // Card / elevated surface
    val surfaceHigh    : Color,   // Higher elevation (modals, dialogs)
    val border         : Color,   // Subtle dividers & borders
    val textPrimary    : Color,   // Primary text
    val textSecondary  : Color,   // Secondary / muted text
    val neonGradient   : Brush,   // Full neon gradient (progress bars, borders)
    val accentGradient : Brush,   // Accent-only gradient (play button, etc.)
    val hue            : Float,   // The source hue for reference
)
