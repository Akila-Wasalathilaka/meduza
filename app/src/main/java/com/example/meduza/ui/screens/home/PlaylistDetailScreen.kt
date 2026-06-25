package com.example.meduza.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.meduza.data.model.HomeSection
import com.example.meduza.data.model.OnlineSong
import com.example.meduza.data.model.PlaylistDetail
import com.example.meduza.playback.PlaybackUiState
import com.example.meduza.ui.components.ShimmerSection
import com.example.meduza.ui.components.SongRow
import com.example.meduza.ui.theme.LocalMeduzaColors

@Composable
fun PlaylistDetailScreen(
    title: String,
    playlistDetail: PlaylistDetail?,
    fallbackSections: List<HomeSection>,
    isLoading: Boolean,
    playbackState: PlaybackUiState,
    onBack: () -> Unit,
    onPlaySong: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onPlayAll: (List<OnlineSong>, Boolean) -> Unit
) {
    val mc = LocalMeduzaColors.current
    val tracks = playlistDetail?.tracks ?: fallbackSections.flatMap { it.songs }
    val displayTitle = playlistDetail?.title ?: title
    val thumbUrl = playlistDetail?.thumbnailUrl

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Top Bar
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
                        .background(mc.surface.copy(alpha = 0.6f))
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
                    text = displayTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (isLoading) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ShimmerSection(cardSize = 200.dp)
                }
            }
        } else {
            // Hero Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (thumbUrl != null) {
                        AsyncImage(
                            model = thumbUrl,
                            contentDescription = displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(220.dp)
                                .shadow(24.dp, RoundedCornerShape(16.dp), spotColor = mc.accent)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                    Text(
                        text = displayTitle,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = mc.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    playlistDetail?.description?.takeIf { it.isNotBlank() }?.let { desc ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = desc,
                            fontSize = 13.sp,
                            color = mc.textSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${playlistDetail?.authorName ?: "Playlist"} • ${playlistDetail?.trackCountText ?: "${tracks.size} songs"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = mc.accent
                    )
                    Spacer(Modifier.height(20.dp))

                    // Action Controls
                    if (tracks.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Play Button
                            Button(
                                onClick = { onPlayAll(tracks, false) },
                                colors = ButtonDefaults.buttonColors(containerColor = mc.accent),
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                                modifier = Modifier.shadow(12.dp, CircleShape, spotColor = mc.accent)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.Black)
                                Spacer(Modifier.width(6.dp))
                                Text("Play", fontWeight = FontWeight.Black, color = Color.Black, fontSize = 16.sp)
                            }

                            // Shuffle Button
                            FilledIconButton(
                                onClick = { onPlayAll(tracks, true) },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = mc.surface),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = mc.textPrimary)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
            }

            // Tracklist
            itemsIndexed(
                items = tracks,
                key = { idx, song -> "track_${song.videoId}_$idx" }
            ) { itemIdx, song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaySong(song, tracks, itemIdx) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${itemIdx + 1}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (playbackState.mediaId == song.videoId) mc.accent else mc.textSecondary,
                        modifier = Modifier.width(32.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        SongRow(
                            title     = song.title,
                            artist    = song.artist,
                            meta      = "",
                            videoId   = song.videoId,
                            thumbUrl  = song.thumbnailUrl,
                            isPlaying = playbackState.mediaId == song.videoId,
                            onClick   = { onPlaySong(song, tracks, itemIdx) }
                        )
                    }
                }
            }
        }
    }
}
