package com.example.meduza.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meduza.ui.theme.*

/**
 * SettingsDialog — full-featured settings sheet with dynamic theme color picker.
 *
 * Made with ❤️ by Akyyra — https://github.com/akyyra/meduza
 */
@Composable
fun SettingsDialog(
    audioQuality    : String,
    lowNetworkMode  : Boolean,
    cacheEnabled    : Boolean,
    accentHue       : Float,
    onQualityChange : (String) -> Unit,
    onLowNetChange  : (Boolean) -> Unit,
    onCacheChange   : (Boolean) -> Unit,
    onHueChange     : (Float) -> Unit,
    onDismiss       : () -> Unit,
) {
    val mc = LocalMeduzaColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = mc.surfaceHigh,
        shape            = RoundedCornerShape(24.dp),
        title = {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            Column {
                Text(
                    "Settings",
                    color      = mc.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize   = 20.sp,
                )
                Text(
                    "Meduza by Akyyra",
                    color    = mc.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://akyyra.com")
                    }
                )
            }
        },
        text = {
            Column(
                modifier            = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {

                // ── Theme Color Picker ─────────────────────────────────────────
                Column {
                    Text(
                        "ACCENT COLOR",
                        color         = mc.textSecondary,
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(12.dp))

                    // Continuous Color Hue Slider
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Current Hue: ${accentHue.toInt()}°",
                                fontSize = 11.sp,
                                color = mc.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(mc.accent)
                                    .border(1.dp, mc.border, CircleShape)
                            )
                        }
                        Slider(
                            value = accentHue,
                            onValueChange = onHueChange,
                            valueRange = 0f..360f,
                            colors = SliderDefaults.colors(
                                thumbColor = mc.accent,
                                activeTrackColor = mc.accent,
                                inactiveTrackColor = mc.surface
                            )
                        )
                    }
                }

                HorizontalDivider(color = mc.border)

                // ── Audio Quality ──────────────────────────────────────────────
                Column {
                    Text(
                        "AUDIO QUALITY",
                        color         = mc.textSecondary,
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("low", "medium", "high").forEach { q ->
                            val selected = audioQuality == q
                            OutlinedButton(
                                onClick        = { onQualityChange(q) },
                                modifier       = Modifier.weight(1f),
                                shape          = RoundedCornerShape(10.dp),
                                colors         = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) mc.accent else Color.Transparent,
                                    contentColor   = if (selected) mc.background else mc.textSecondary,
                                ),
                                border         = BorderStroke(1.dp, if (selected) mc.accent else mc.border),
                                contentPadding = PaddingValues(vertical = 8.dp),
                            ) {
                                Text(
                                    q.replaceFirstChar { it.uppercase() },
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = mc.border)

                // ── Low Data Mode ──────────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth(),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Low Data Mode",
                            color      = mc.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 13.sp,
                        )
                        Text(
                            "Less bandwidth. Active immediately.",
                            color    = mc.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked         = lowNetworkMode,
                        onCheckedChange = onLowNetChange,
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor   = mc.background,
                            checkedTrackColor   = mc.accent,
                            uncheckedThumbColor = mc.textSecondary,
                            uncheckedTrackColor = mc.surface,
                        ),
                    )
                }

                // ── Cache Streams ──────────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth(),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Cache Streams",
                            color      = mc.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 13.sp,
                        )
                        Text(
                            "Faster re-play. Disable on 1GB RAM devices.",
                            color    = mc.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked         = cacheEnabled,
                        onCheckedChange = onCacheChange,
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor   = mc.background,
                            checkedTrackColor   = mc.accent,
                            uncheckedThumbColor = mc.textSecondary,
                            uncheckedTrackColor = mc.surface,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = mc.accent, fontWeight = FontWeight.Bold)
            }
        },
    )
}
