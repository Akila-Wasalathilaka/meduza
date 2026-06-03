package com.example.meduza.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.meduza.data.model.OnlineSong
import com.example.meduza.ui.theme.*

/**
 * Shared UI components: TrackCard, SongRow, SectionHeader, PermissionCard, VibePill.
 * All colors derived from [LocalMeduzaColors] for full dynamic theme support.
 *
 * Made with ❤️ by Akyyra — https://github.com/akyyra/meduza
 */

// ── Track Card ─────────────────────────────────────────────────────────────────
@Composable
fun TrackCard(
    song      : OnlineSong,
    isPlaying : Boolean,
    size      : Dp,
    isCircular: Boolean = false,
    onClick   : () -> Unit,
) {
    val mc      = LocalMeduzaColors.current
    val videoId = song.videoId
    val urls    = remember(videoId) {
        listOf(
            thumbUrl(videoId),
            thumbUrlFallback1(videoId),
            thumbUrlFallback2(videoId),
        )
    }
    var urlIndex by remember(videoId) { mutableIntStateOf(0) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(if (isCircular) CircleShape else RoundedCornerShape(10.dp))
                .background(mc.surface)
                .then(if (isPlaying) Modifier.border(2.dp, mc.accent, if (isCircular) CircleShape else RoundedCornerShape(10.dp)) else Modifier)
                .clickable(onClick = onClick),
        ) {
            val currentUrl = urls.getOrNull(urlIndex)
            if (currentUrl != null) {
                AsyncImage(
                    model              = currentUrl,
                    onError            = { urlIndex = (urlIndex + 1).coerceAtMost(urls.size - 1) },
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(getDeterministicGradient(song.title, song.artist)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = song.title.take(1).uppercase(),
                        fontSize   = if (isCircular) 48.sp else 28.sp,
                        fontWeight = FontWeight.Black,
                        color      = Color.White.copy(alpha = 0.8f),
                    )
                }
            }

            if (!isCircular) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(10.dp),
                ) {
                    Text(song.title,  fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Start, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                    Text(song.artist, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f),            textAlign = TextAlign.Start, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                }
            }

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(mc.accent)
                        .align(if (isCircular) Alignment.BottomCenter else Alignment.TopEnd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = mc.background, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (isCircular) {
            Spacer(Modifier.height(8.dp))
            Text(song.title,  fontSize = 13.sp, fontWeight = FontWeight.Bold, color = mc.textPrimary,   textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
            Text(song.artist, fontSize = 11.sp, color = mc.textSecondary,                               textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
        }
    }
}

// ── Song Row ───────────────────────────────────────────────────────────────────
@Composable
fun SongRow(
    title     : String,
    artist    : String,
    meta      : String,
    videoId   : String?,
    thumbUrl  : String?,
    isPlaying : Boolean,
    onClick   : () -> Unit,
) {
    val mc = LocalMeduzaColors.current

    val titleColor by animateColorAsState(
        targetValue   = if (isPlaying) mc.accentGlow else mc.textPrimary,
        animationSpec = tween(300),
        label         = "TitleColor",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!videoId.isNullOrBlank()) {
            val urls = remember(videoId) {
                listOf(
                    thumbUrl ?: "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
                    "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    "https://i.ytimg.com/vi/$videoId/sddefault.jpg",
                )
            }
            var urlIndex by remember(videoId) { mutableIntStateOf(0) }
            val currentUrl = urls.getOrNull(urlIndex)

            if (currentUrl != null) {
                AsyncImage(
                    model              = currentUrl,
                    onError            = { urlIndex = (urlIndex + 1).coerceAtMost(urls.size - 1) },
                    contentDescription = null,
                    modifier           = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .then(if (isPlaying) Modifier.border(2.dp, mc.neonGradient, RoundedCornerShape(6.dp)) else Modifier),
                    contentScale       = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier         = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(getDeterministicGradient(title, artist))
                        .then(if (isPlaying) Modifier.border(2.dp, mc.neonGradient, RoundedCornerShape(6.dp)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(title.take(1).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
            }
        } else {
            Box(
                modifier         = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(getDeterministicGradient(title, artist))
                    .then(if (isPlaying) Modifier.border(2.dp, mc.neonGradient, RoundedCornerShape(6.dp)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(title.take(1).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
        }

        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title,  fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = titleColor,       maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, fontSize = 12.sp, color = mc.textSecondary,                                   maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (meta.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(meta, fontSize = 11.sp, color = mc.textSecondary)
        }
    }
}

// ── Section Header ─────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(title: String) {
    val mc = LocalMeduzaColors.current
    Text(
        text       = title,
        fontSize   = 18.sp,
        fontWeight = FontWeight.Bold,
        color      = mc.textPrimary,
        modifier   = Modifier.padding(horizontal = 16.dp),
    )
}

// ── Permission Card ────────────────────────────────────────────────────────────
@Composable
fun PermissionCard(onRequest: () -> Unit) {
    val mc = LocalMeduzaColors.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors   = CardDefaults.cardColors(containerColor = mc.surfaceHigh),
        shape    = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Allow Audio Access", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = mc.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Meduza needs access to your audio files to play local music.",
                fontSize   = 12.sp,
                color      = mc.textSecondary,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onRequest,
                shape   = RoundedCornerShape(8.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = mc.accent),
            ) {
                Text("Grant Permission", color = mc.background, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Vibe Pill ──────────────────────────────────────────────────────────────────
@Composable
fun VibePill(
    song      : OnlineSong,
    isPlaying : Boolean,
    onClick   : () -> Unit,
) {
    val mc = LocalMeduzaColors.current
    Box(
        modifier = Modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(mc.surface)
            .then(if (isPlaying) Modifier.border(2.dp, mc.accent, RoundedCornerShape(28.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = "${song.title} • ${song.artist}",
            color      = if (isPlaying) mc.accentGlow else mc.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize   = 14.sp,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )
    }
}
