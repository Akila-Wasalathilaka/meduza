package com.example.meduza.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.meduza.ui.theme.LocalMeduzaColors

/**
 * MeduzaBackground — the signature mesh-gradient ambient background.
 *
 * The blob colors are derived from the current dynamic theme:
 *  - Blob 1: accent color (user-chosen hue)
 *  - Blob 2: complement color (180° opposite)
 *  - Blob 3: triadic color (120° shift)
 *
 * This ensures the entire app atmosphere changes when the user picks a new theme.
 *
 * Made with ❤️ by Akyyra — https://github.com/akyyra/meduza
 */
@Composable
fun MeduzaBackground(content: @Composable () -> Unit) {
    val mc = LocalMeduzaColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(mc.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .align(Alignment.TopCenter)
                .drawWithCache {
                    val w = size.width
                    val h = size.height

                    // Three dynamic blobs — colored by the theme engine
                    val brush1 = Brush.radialGradient(
                        colors = listOf(mc.accent.copy(alpha = 0.15f), mc.accent.copy(alpha = 0.05f), Color.Transparent),
                        center = Offset(w * 0.15f, h * 0.1f),
                        radius = w * 0.6f,
                    )
                    val brush2 = Brush.radialGradient(
                        colors = listOf(mc.complement.copy(alpha = 0.12f), mc.complement.copy(alpha = 0.04f), Color.Transparent),
                        center = Offset(w * 0.85f, h * 0.2f),
                        radius = w * 0.65f,
                    )
                    val brush3 = Brush.radialGradient(
                        colors = listOf(mc.triad.copy(alpha = 0.10f), mc.triad.copy(alpha = 0.03f), Color.Transparent),
                        center = Offset(w * 0.5f, h * 0.5f),
                        radius = w * 0.7f,
                    )
                    val fade = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, mc.background.copy(alpha = 0.5f), mc.background),
                        startY = h * 0.3f,
                        endY   = h,
                    )
                    onDrawBehind {
                        drawRect(brush = brush1)
                        drawRect(brush = brush2)
                        drawRect(brush = brush3)
                        drawRect(brush = fade)
                    }
                },
        )
        content()
    }
}
