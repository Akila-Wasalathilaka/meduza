package com.example.meduza.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import com.example.meduza.data.model.HomeSection
import com.example.meduza.data.model.HomeChip
import com.example.meduza.data.model.OnlineSong
import com.example.meduza.playback.PlaybackUiState
import com.example.meduza.ui.components.*
import com.example.meduza.ui.theme.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.util.Calendar

// ── Section title truncation ───────────────────────────────────────────────────
private fun truncateTitle(title: String, maxLength: Int = 35): String {
    if (title.length <= maxLength) return title
    val trimmed = title.take(maxLength)
    val lastSpace = trimmed.lastIndexOf(' ')
    return if (lastSpace > 15) trimmed.substring(0, lastSpace) + "…" else trimmed + "…"
}

// ── HomeScreen ────────────────────────────────────────────────────────────────
@Composable
fun HomeScreen(
    playbackState:    PlaybackUiState,
    recentSongs:      List<OnlineSong>,
    homeSections:     List<HomeSection>,
    homeChips:        List<HomeChip>,
    selectedChip:     HomeChip?,
    onChipSelected:   (HomeChip) -> Unit,
    homeLoading:      Boolean,
    isRefreshing:     Boolean = false,
    isLoadingMore:    Boolean = false,
    onRefresh:        () -> Unit = {},
    onLoadMore:       () -> Unit = {},
    onPlay:           (List<MediaItem>, Int) -> Unit,
    onPlaySong:       (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onSettingsClick:  () -> Unit,
) {
    val hour     = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when { hour < 12 -> "Good morning"; hour < 18 -> "Good afternoon"; else -> "Good evening" }

    // ── Stable dedup in remember() ────────────────────────────────────────────
    val displayRecent = remember(recentSongs) {
        recentSongs.distinctBy { it.title.lowercase() }
    }
    val dedupedSections = homeSections

    // ── Infinite scroll detection ─────────────────────────────────────────────
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .filter { idx -> idx != null && idx >= listState.layoutInfo.totalItemsCount - 3 }
            .collect { onLoadMore() }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh    = onRefresh,
        modifier     = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state               = listState,
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(bottom = 160.dp, top = 0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // ── Header ───────────────────────────────────────────────────────
            item {
                HomeHeader(greeting = greeting, onSettingsClick = onSettingsClick)
            }

            item {
                if (homeChips.isNotEmpty()) {
                    HomeChipRow(
                        chips          = homeChips,
                        selectedChip   = selectedChip,
                        onChipSelected = onChipSelected,
                    )
                    Spacer(Modifier.height(8.dp))
                } else {
                    Spacer(Modifier.height(16.dp))
                }
            }

            // ── Jump Back In ──────────────────────────────────────────────────
            if (displayRecent.isNotEmpty()) {
                item {
                    HomeSectionHeader(title = "Jump Back In")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding        = PaddingValues(horizontal = 16.dp),
                    ) {
                        itemsIndexed(displayRecent.take(10), key = { idx, song -> "recent_${song.videoId}_$idx" }) { idx, song ->
                            val isActive = playbackState.mediaId == song.videoId
                            ImmersiveTrackCard(
                                song      = song,
                                isPlaying = isActive,
                                size      = 160.dp,
                                onClick   = { onPlaySong(song, displayRecent, idx) },
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            // ── Home Sections ─────────────────────────────────────────────────
            dedupedSections.forEachIndexed { sIdx, section ->
                item(key = "header_$sIdx") {
                    HomeSectionHeader(title = truncateTitle(section.title))
                    Spacer(Modifier.height(10.dp))
                }
                item(key = "content_$sIdx") {
                    if (sIdx % 3 == 2) {
                        DeckRow(section.songs, playbackState, onPlaySong)
                    } else {
                        StandardRow(section.songs, playbackState, onPlaySong, 170.dp)
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }

            // ── Shimmer while loading (initial load or chip switch) ───────────
            if (homeLoading && homeSections.isEmpty()) {
                items(6) { sIdx ->
                    val size = if (sIdx % 2 == 0) 210.dp else 175.dp
                    ShimmerSection(cardSize = size)
                    Spacer(Modifier.height(24.dp))
                }
            }

            // ── Load-more shimmer at bottom ───────────────────────────────────
            if (isLoadingMore) {
                item {
                    ShimmerSection(cardSize = 175.dp)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────────────
@Composable
private fun HomeHeader(greeting: String, onSettingsClick: () -> Unit) {
    val mc = LocalMeduzaColors.current
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text       = greeting,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Black,
                color      = mc.textPrimary,
            )
            Text(
                text     = "What's on your mind?",
                fontSize = 13.sp,
                color    = mc.textSecondary,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(mc.surface)
                .border(1.dp, mc.border, CircleShape)
                .clickable { onSettingsClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Default.Settings,
                contentDescription = "Settings",
                tint               = mc.textSecondary,
                modifier           = Modifier.size(20.dp),
            )
        }
    }
}

// ── Section Header ─────────────────────────────────────────────────────────────
@Composable
fun HomeSectionHeader(title: String, label: String? = null) {
    val mc = LocalMeduzaColors.current
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(mc.accent)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text       = title,
                fontSize   = 19.sp,
                fontWeight = FontWeight.Black,
                color      = mc.textPrimary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            if (label != null) {
                Text(
                    text     = label,
                    fontSize = 12.sp,
                    color    = mc.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

// ── Layout 1: 3D Deck (overlapping tilt) ──────────────────────────────────────
@Composable
private fun DeckRow(
    songs: List<OnlineSong>,
    state: PlaybackUiState,
    onPlay: (OnlineSong, List<OnlineSong>, Int) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy((-28).dp),
        contentPadding        = PaddingValues(start = 16.dp, top = 0.dp, end = 40.dp, bottom = 0.dp),
    ) {
        itemsIndexed(songs, key = { idx, song -> "deck_${song.videoId}_$idx" }) { idx, song ->
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ    = if (idx % 2 == 0) -2.5f else 2.5f
                        translationY = if (idx % 2 == 0) 14f else -14f
                    }
                    .zIndex((songs.size - idx).toFloat())
            ) {
                ImmersiveTrackCard(
                    song      = song,
                    isPlaying = state.mediaId == song.videoId,
                    size      = 210.dp,
                    onClick   = { onPlay(song, songs, idx) },
                )
            }
        }
    }
}

// ── Layout 2: Standard Square Cards ───────────────────────────────────────────
@Composable
private fun StandardRow(
    songs: List<OnlineSong>,
    state: PlaybackUiState,
    onPlay: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    size: Dp,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding        = PaddingValues(horizontal = 16.dp),
    ) {
        itemsIndexed(songs, key = { idx, song -> "std_${song.videoId}_$idx" }) { idx, song ->
            ImmersiveTrackCard(
                song      = song,
                isPlaying = state.mediaId == song.videoId,
                size      = size,
                onClick   = { onPlay(song, songs, idx) },
            )
        }
    }
}

// ── Layout 3: List Columns (chunked rows) ─────────────────────────────────────
@Composable
private fun ListColumnsRow(
    songs: List<OnlineSong>,
    state: PlaybackUiState,
    onPlay: (OnlineSong, List<OnlineSong>, Int) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding        = PaddingValues(horizontal = 16.dp),
    ) {
        val chunks = songs.chunked(4)
        items(chunks.size, key = { chunkIdx -> "col_$chunkIdx" }) { chunkIdx ->
            val chunk = chunks[chunkIdx]
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier            = Modifier.width(300.dp),
            ) {
                chunk.forEachIndexed { itemIdx, song ->
                    SongRow(
                        title     = song.title,
                        artist    = song.artist,
                        meta      = "",
                        videoId   = song.videoId,
                        thumbUrl  = song.thumbnailUrl,
                        isPlaying = state.mediaId == song.videoId,
                        onClick   = { onPlay(song, songs, chunkIdx * 4 + itemIdx) },
                    )
                }
            }
        }
    }
}

// ── Layout 4: Compact Small Tiles ─────────────────────────────────────────────
@Composable
private fun CompactRow(
    songs: List<OnlineSong>,
    state: PlaybackUiState,
    onPlay: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    size: Dp,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding        = PaddingValues(horizontal = 16.dp),
    ) {
        itemsIndexed(songs, key = { idx, song -> "cmp_${song.videoId}_$idx" }) { idx, song ->
            ImmersiveTrackCard(
                song      = song,
                isPlaying = state.mediaId == song.videoId,
                size      = size,
                onClick   = { onPlay(song, songs, idx) },
            )
        }
    }
}

// ── Immersive Track Card ───────────────────────────────────────────────────────
/**
 * Clean, angelic track card without heavy boundaries.
 * Text is placed cleanly below the image for a modern, breathable look.
 * Artists use circular shapes.
 */
@Composable
fun ImmersiveTrackCard(
    song      : OnlineSong,
    isPlaying : Boolean,
    size      : Dp,
    onClick   : () -> Unit,
) {
    val mc      = LocalMeduzaColors.current
    val videoId = song.videoId
    val isArtist = song.type == "artist"
    val shape   = if (isArtist) CircleShape else RoundedCornerShape(12.dp)

    val context = androidx.compose.ui.platform.LocalContext.current
    val isLowEnd = remember(context) { com.example.meduza.core.utils.DevicePerformanceManager.isLowEndDevice(context) }
    val urls    = remember(videoId, song.thumbnailUrl, isLowEnd) {
        listOfNotNull(
            com.example.meduza.core.utils.DevicePerformanceManager.getAdaptiveThumbnailUrl(song.thumbnailUrl, isLowEnd),
            song.thumbnailUrl,
            "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            "https://i.ytimg.com/vi/$videoId/sddefault.jpg",
        ).distinct()
    }
    var urlIndex by remember(videoId) { mutableIntStateOf(0) }

    val cardScale by animateFloatAsState(
        targetValue   = if (isPlaying) 1.03f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "cardScale",
    )

    val borderBrush = Brush.linearGradient(listOf(mc.accent, mc.complement, mc.accent))

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.width(size),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .scale(cardScale)
                .clip(shape)
                .background(mc.surface.copy(alpha = 0.5f))
                .then(
                    if (isPlaying) Modifier.border(2.dp, borderBrush, shape)
                    else Modifier
                )
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
                    modifier         = Modifier
                        .fillMaxSize()
                        .background(getDeterministicGradient(song.title, song.artist)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = song.title.take(1).uppercase(),
                        fontSize   = (size.value * 0.22f).sp,
                        fontWeight = FontWeight.Black,
                        color      = Color.White.copy(alpha = 0.85f),
                    )
                }
            }

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(mc.accent)
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint               = mc.background,
                        modifier           = Modifier.size(18.dp),
                    )
                }
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            text       = song.title,
            fontSize   = (if (size >= 160.dp) 14f else 12f).sp,
            fontWeight = FontWeight.SemiBold,
            color      = mc.textPrimary,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            textAlign  = if (isArtist) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
            modifier   = Modifier.fillMaxWidth()
        )
        if (!isArtist) {
            Spacer(Modifier.height(2.dp))
            Text(
                text     = song.artist,
                fontSize = (if (size >= 160.dp) 12f else 10f).sp,
                color    = mc.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign  = androidx.compose.ui.text.style.TextAlign.Start,
                modifier   = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun QuickPicksGrid(
    songs: List<OnlineSong>,
    state: PlaybackUiState,
    onPlay: (OnlineSong, List<OnlineSong>, Int) -> Unit,
) {
    val mc = LocalMeduzaColors.current
    val displaySongs = songs.take(8)
    val chunks = displaySongs.chunked(2)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chunks.forEachIndexed { rowIdx, rowSongs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowSongs.forEachIndexed { colIdx, song ->
                    val itemIdx = rowIdx * 2 + colIdx
                    val isPlaying = state.mediaId == song.videoId
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPlaying) mc.accent.copy(alpha = 0.25f) else mc.surface.copy(alpha = 0.6f))
                            .border(0.5.dp, if (isPlaying) mc.accent else mc.border, RoundedCornerShape(8.dp))
                            .clickable { onPlay(song, songs, itemIdx) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            coil.compose.AsyncImage(
                                model = song.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = song.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPlaying) mc.accent else mc.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                        }
                    }
                }
                if (rowSongs.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}


