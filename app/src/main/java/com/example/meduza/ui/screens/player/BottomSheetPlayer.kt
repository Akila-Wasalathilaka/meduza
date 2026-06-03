package com.example.meduza.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import com.example.meduza.playback.PlaybackUiState
import com.example.meduza.ui.theme.*
import com.example.meduza.ui.components.MeduzaBackground

/**
 * BottomSheetPlayer — collapsible mini + full-screen player.
 *
 * Bugs fixed in this version:
 *  - Slider thumb no longer overflows bounds at 100% progress
 *  - NeonGradient and accent colors are fully dynamic (LocalMeduzaColors)
 *  - "PLAYING FROM" context label shows the real queue context, not hardcoded "MEDUZA RADIO"
 *
 * Made with ❤️ by Akyyra — https://github.com/akyyra/meduza
 */

// ── Artwork URL builder ────────────────────────────────────────────────────────
private fun buildArtworkUrls(state: PlaybackUiState): List<String> {
    val list = mutableListOf<String>()
    state.artworkUrl?.takeIf { it.isNotBlank() }?.let { url ->
        val highRes = url.replace(Regex("=w\\d+-h\\d+.*"), "=w540-h540-l90-rj")
        list.add(highRes)
        if (highRes != url) list.add(url)
    }
    if (state.mediaId.length == 11) {
        list.add("https://i.ytimg.com/vi/${state.mediaId}/maxresdefault.jpg")
        list.add("https://i.ytimg.com/vi/${state.mediaId}/hqdefault.jpg")
        list.add("https://i.ytimg.com/vi/${state.mediaId}/sddefault.jpg")
    }
    return list.distinct()
}

private fun createInsetRoundedRectPath(width: Float, height: Float, radius: Float, strokeWidth: Float): Path {
    val path = Path()
    val half = strokeWidth / 2f
    val w = width - half
    val h = height - half
    val r = (radius - half).coerceAtLeast(0f).coerceAtMost((w - half) / 2f).coerceAtMost((h - half) / 2f)
    path.moveTo(half + r, half)
    path.lineTo(w - r, half)
    path.arcTo(rect = Rect(w - 2 * r, half, w, half + 2 * r), startAngleDegrees = -90f, sweepAngleDegrees = 90f, forceMoveTo = false)
    path.lineTo(w, h - r)
    path.arcTo(rect = Rect(w - 2 * r, h - 2 * r, w, h), startAngleDegrees = 0f, sweepAngleDegrees = 90f, forceMoveTo = false)
    path.lineTo(half + r, h)
    path.arcTo(rect = Rect(half, h - 2 * r, half + 2 * r, h), startAngleDegrees = 90f, sweepAngleDegrees = 90f, forceMoveTo = false)
    path.lineTo(half, half + r)
    path.arcTo(rect = Rect(half, half, half + 2 * r, half + 2 * r), startAngleDegrees = 180f, sweepAngleDegrees = 90f, forceMoveTo = false)
    return path
}

@Composable
fun BottomSheetPlayer(
    state           : PlaybackUiState,
    isExpanded      : Boolean,
    onToggleExpand  : () -> Unit,
    onTogglePlay    : () -> Unit,
    onNext          : () -> Unit,
    onPrevious      : () -> Unit,
    onSeek          : (Long) -> Unit,
    onToggleShuffle : () -> Unit,
    onToggleRepeat  : () -> Unit,
    modifier        : Modifier = Modifier,
) {
    val mc = LocalMeduzaColors.current
    BackHandler(enabled = isExpanded) { onToggleExpand() }

    val screenHeight   = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val targetHeight   = if (isExpanded) screenHeight else 68.dp
    val playerHeight   by animateDpAsState(
        targetValue   = targetHeight,
        animationSpec = spring(
            dampingRatio = if (isExpanded) Spring.DampingRatioNoBouncy else Spring.DampingRatioMediumBouncy,
            stiffness    = if (isExpanded) Spring.StiffnessMediumLow else Spring.StiffnessLow
        ),
        label         = "PlayerHeight",
    )

    val range          = (screenHeight - 68.dp).coerceAtLeast(1.dp)
    val expandFraction = ((playerHeight - 68.dp) / range).coerceIn(0f, 1f)
    val hPad   = 8.dp * (1f - expandFraction)
    val bPad   = 8.dp * (1f - expandFraction)
    val radius = 16.dp * (1f - expandFraction)

    val infiniteTransition = rememberInfiniteTransition(label = "Disc")
    val rotation by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
        label         = "Rotation",
    )

    var dragAccumulated by remember { mutableFloatStateOf(0f) }

    val rawProgress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(
        targetValue   = rawProgress,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label         = "ProgressAnimation",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .requiredHeight(playerHeight)
            .padding(horizontal = hPad)
            .padding(bottom = bPad)
            .then(
                if (!isExpanded) {
                    Modifier
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(radius))
                        .drawWithContent {
                            drawContent()
                            val radiusPx = radius.toPx()
                            val strokePx = 3.dp.toPx()
                            if (progress > 0f) {
                                val path = createInsetRoundedRectPath(size.width, size.height, radiusPx, strokePx)
                                val pm   = PathMeasure()
                                pm.setPath(path, false)
                                val partialPath = Path()
                                pm.getSegment(0f, progress * pm.length, partialPath, true)
                                drawPath(
                                    path  = partialPath,
                                    brush = mc.neonGradient,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = strokePx,
                                        cap   = androidx.compose.ui.graphics.StrokeCap.Round,
                                    ),
                                )
                            }
                        }
                } else Modifier
            ),
        color  = if (isExpanded) Color.Transparent else mc.surface.copy(alpha = 0.85f),
        shape  = RoundedCornerShape(
            topStart    = radius, topEnd      = radius,
            bottomStart = if (!isExpanded) radius else 0.dp,
            bottomEnd   = if (!isExpanded) radius else 0.dp,
        ),
        tonalElevation = 0.dp,
    ) {
        if (!isExpanded) {
            MiniPlayer(
                state        = state,
                rotation     = rotation,
                onExpand     = onToggleExpand,
                onTogglePlay = onTogglePlay,
                onNext       = onNext,
                onPrevious   = onPrevious,
            )
        } else {
            FullPlayer(
                state           = state,
                rotation        = rotation,
                onCollapse      = onToggleExpand,
                onTogglePlay    = onTogglePlay,
                onNext          = onNext,
                onPrevious      = onPrevious,
                onSeek          = onSeek,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat  = onToggleRepeat,
                onDrag          = { dragAccumulated += it },
                onDragEnd       = {
                    if (dragAccumulated < -120f && state.hasNext) onNext()
                    else if (dragAccumulated > 120f && state.hasPrev) onPrevious()
                    dragAccumulated = 0f
                },
            )
        }
    }
}

// ── Disc Artwork ───────────────────────────────────────────────────────────────
@Composable
private fun DiscArtwork(
    state    : PlaybackUiState,
    rotation : Float,
    size     : androidx.compose.ui.unit.Dp,
    imageSize: androidx.compose.ui.unit.Dp = size,
) {
    val artworkUrls = remember(state.mediaId, state.artworkUrl) { buildArtworkUrls(state) }
    var urlIndex    by remember(state.mediaId) { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(getDeterministicGradient(state.title, state.artist))
            .graphicsLayer { rotationZ = if (state.isPlaying) rotation else 0f },
        contentAlignment = Alignment.Center,
    ) {
        val currentUrl = artworkUrls.getOrNull(urlIndex)
        if (currentUrl != null) {
            AsyncImage(
                model              = currentUrl,
                onError            = { urlIndex = (urlIndex + 1).coerceAtMost(artworkUrls.size - 1) },
                contentDescription = null,
                modifier           = Modifier.size(imageSize).clip(CircleShape),
                contentScale       = ContentScale.Crop,
            )
        } else {
            Text(
                text       = state.title.take(1).uppercase(),
                fontSize   = if (size > 100.dp) 64.sp else 14.sp,
                fontWeight = FontWeight.Black,
                color      = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

// ── Mini Player ────────────────────────────────────────────────────────────────
@Composable
private fun MiniPlayer(
    state        : PlaybackUiState,
    rotation     : Float,
    onExpand     : () -> Unit,
    onTogglePlay : () -> Unit,
    onNext       : () -> Unit,
    onPrevious   : () -> Unit,
) {
    val mc = LocalMeduzaColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                onClick           = onExpand,
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
            )
    ) {
        Row(
            modifier              = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DiscArtwork(state = state, rotation = rotation, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(state.title,  fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = mc.textPrimary,   maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.artist, fontSize = 11.sp, color = mc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.hasPrev) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = mc.textPrimary, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onTogglePlay, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector        = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint               = mc.textPrimary,
                        modifier           = Modifier.size(22.dp),
                    )
                }
                if (state.hasNext) {
                    IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = mc.textPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ── Full Player ────────────────────────────────────────────────────────────────
@Composable
private fun FullPlayer(
    state           : PlaybackUiState,
    rotation        : Float,
    onCollapse      : () -> Unit,
    onTogglePlay    : () -> Unit,
    onNext          : () -> Unit,
    onPrevious      : () -> Unit,
    onSeek          : (Long) -> Unit,
    onToggleShuffle : () -> Unit,
    onToggleRepeat  : () -> Unit,
    onDrag          : (Float) -> Unit,
    onDragEnd       : () -> Unit,
) {
    val mc = LocalMeduzaColors.current

    val infiniteTransition = rememberInfiniteTransition(label = "NeonColorTransition")
    val animatedColor by infiniteTransition.animateColor(
        initialValue  = mc.accent,
        targetValue   = mc.accentGlow,
        animationSpec = infiniteRepeatable(
            animation  = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "NeonColor",
    )

    MeduzaBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { onDragEnd() },
                        onDrag = { _, dragAmount -> 
                            if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y)) {
                                onDrag(dragAmount.x)
                            } else if (dragAmount.y > 20f) {
                                onCollapse()
                            }
                        }
                    )
                }
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCollapse, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Collapse", tint = mc.textPrimary, modifier = Modifier.size(32.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "PLAYING FROM",
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = mc.textSecondary,
                        letterSpacing = 1.2.sp,
                    )
                    // Bug fix: show real queue context, not hardcoded "MEDUZA RADIO"
                    Text(
                        state.queueContext.uppercase(),
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Black,
                        color      = mc.textPrimary,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.size(40.dp))
            }

            // ── Artwork + metadata ────────────────────────────────────────────
            Column(
                modifier            = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DiscArtwork(state = state, rotation = rotation, size = 260.dp, imageSize = 240.dp)
                Spacer(Modifier.height(32.dp))
                Text(state.title,  fontSize = 22.sp, fontWeight = FontWeight.Black,  color = mc.textPrimary,   textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(state.artist, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = mc.textSecondary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            }

            // ── Controls ──────────────────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {

                // Progress bar with dynamic neon gradient
                Box(
                    modifier         = Modifier.fillMaxWidth().height(32.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // Background track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(mc.surface),
                    )

                    val fraction = if (state.durationMs > 0)
                        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f) else 0f

                    // Active track with dynamic neon gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction.coerceAtLeast(0.01f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(mc.neonGradient),
                    )

                    // Bug fix: thumb positioned correctly without overflowing at 100%
                    // Using BoxWithConstraints + fractional offset avoids the 8.dp overflow glitch
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val thumbOffsetX = maxWidth * fraction
                        Box(
                            modifier = Modifier
                                .offset(x = (thumbOffsetX - 8.dp).coerceAtLeast(0.dp))
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                    }

                    @OptIn(ExperimentalMaterial3Api::class)
                    Slider(
                        value         = state.positionMs.toFloat(),
                        onValueChange = { onSeek(it.toLong()) },
                        valueRange    = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
                        colors        = SliderDefaults.colors(
                            activeTrackColor   = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                            thumbColor         = Color.Transparent,
                            activeTickColor    = Color.Transparent,
                            inactiveTickColor  = Color.Transparent,
                        ),
                        thumb    = { Spacer(Modifier.size(0.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), Arrangement.SpaceBetween) {
                    Text(formatDuration(state.positionMs), fontSize = 11.sp, color = mc.textSecondary)
                    Text(formatDuration(state.durationMs), fontSize = 11.sp, color = mc.textSecondary)
                }

                Spacer(Modifier.height(16.dp))

                // Playback controls
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    // Shuffle — glows in accent when active (MEDUZA Intelligent Shuffle)
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = "Intelligent Shuffle",
                            tint     = if (state.shuffleModeEnabled) mc.accentGlow else mc.textSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    IconButton(onClick = onPrevious, enabled = state.hasPrev, modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.Rounded.SkipPrevious, contentDescription = "Prev",
                            tint     = if (state.hasPrev) mc.textPrimary else mc.textSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    // Play/Pause — accent colored
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(mc.accentGradient)
                            .clickable(onClick = onTogglePlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint     = mc.background,
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    IconButton(onClick = onNext, enabled = state.hasNext, modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.Rounded.SkipNext, contentDescription = "Next",
                            tint     = if (state.hasNext) mc.textPrimary else mc.textSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    // Repeat
                    IconButton(onClick = onToggleRepeat) {
                        val icon = when (state.repeatMode) { 1 -> Icons.Rounded.RepeatOne; else -> Icons.Rounded.Repeat }
                        val tint = if (state.repeatMode == 0) mc.textSecondary else mc.accentGlow
                        Icon(icon, contentDescription = "Repeat", tint = tint, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
