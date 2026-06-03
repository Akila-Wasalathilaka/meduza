package com.example.meduza.ui.screens.library

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meduza.data.model.LocalSong
import com.example.meduza.playback.PlaybackUiState
import com.example.meduza.ui.components.PermissionCard
import com.example.meduza.ui.components.SongRow
import com.example.meduza.ui.theme.*

/**
 * LibraryScreen — displays local audio files from the device.
 *
 * Made with ❤️ by Akyyra — https://github.com/akyyra/meduza
 */
@Composable
fun LibraryScreen(
    songs               : List<LocalSong>,
    isLoading           : Boolean,
    hasPermission       : Boolean,
    playbackState       : PlaybackUiState,
    onRequestPermission : () -> Unit,
    onPlay              : (Int) -> Unit,
) {
    val mc = LocalMeduzaColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Text("Your Library", fontSize = 22.sp, fontWeight = FontWeight.Black, color = mc.textPrimary)
        Spacer(Modifier.height(14.dp))

        when {
            !hasPermission -> PermissionCard(onRequestPermission)

            isLoading -> {
                val infiniteTransition = rememberInfiniteTransition(label = "LibraryLoading")
                val alpha by infiniteTransition.animateFloat(
                    initialValue  = 0.2f,
                    targetValue   = 1f,
                    animationSpec = infiniteRepeatable(
                        animation  = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "LoadingAlpha",
                )
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .border(2.dp, mc.accent.copy(alpha = alpha), CircleShape)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector        = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint               = mc.accent.copy(alpha = alpha),
                                modifier           = Modifier.size(36.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Scanning local files…",
                            fontSize   = 14.sp,
                            color      = mc.textPrimary.copy(alpha = alpha),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            songs.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("No local tracks found.", fontSize = 13.sp, color = mc.textSecondary)
            }

            else -> LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(songs) { idx, song ->
                    SongRow(
                        title     = song.title,
                        artist    = song.artist,
                        meta      = formatDuration(song.durationMs),
                        videoId   = null,
                        thumbUrl  = null,
                        isPlaying = playbackState.isPlaying && playbackState.title == song.title,
                        onClick   = { onPlay(idx) },
                    )
                }
            }
        }
    }
}
