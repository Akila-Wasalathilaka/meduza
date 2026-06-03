package com.example.meduza.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import com.example.meduza.data.model.HomeSection
import com.example.meduza.data.model.OnlineSong
import com.example.meduza.playback.PlaybackUiState
import com.example.meduza.ui.components.ShimmerSection
import com.example.meduza.ui.components.SongRow
import com.example.meduza.ui.theme.LocalMeduzaColors

@Composable
fun ContextScreen(
    title: String,
    sections: List<HomeSection>,
    isLoading: Boolean,
    playbackState: PlaybackUiState,
    onBack: () -> Unit,
    onPlaySong: (OnlineSong, List<OnlineSong>, Int) -> Unit,
) {
    val mc = LocalMeduzaColors.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 160.dp, top = 0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(mc.surface.copy(alpha = 0.5f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = mc.textPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (isLoading) {
            items(4) { sIdx ->
                Spacer(Modifier.height(16.dp))
                ShimmerSection(cardSize = 160.dp)
                Spacer(Modifier.height(24.dp))
            }
        } else {
            sections.forEachIndexed { sIdx, section ->
                item {
                    Spacer(Modifier.height(16.dp))
                    HomeSectionHeader(title = section.title ?: "Songs")
                    Spacer(Modifier.height(12.dp))
                }
                if (sIdx == 0 && section.songs.size > 5) {
                    itemsIndexed(
                        items = section.songs,
                        key = { index, song -> "${section.title}_${song.videoId}_$index" }
                    ) { itemIdx, song ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            SongRow(
                                title     = song.title,
                                artist    = song.artist,
                                meta      = "",
                                videoId   = song.videoId,
                                thumbUrl  = song.thumbnailUrl,
                                isPlaying = playbackState.mediaId == song.videoId,
                                onClick   = { onPlaySong(song, section.songs, itemIdx) },
                            )
                        }
                    }
                } else {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            itemsIndexed(section.songs) { idx, song ->
                                ImmersiveTrackCard(
                                    song = song,
                                    isPlaying = playbackState.mediaId == song.videoId,
                                    size = 160.dp,
                                    onClick = { onPlaySong(song, section.songs, idx) },
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}
