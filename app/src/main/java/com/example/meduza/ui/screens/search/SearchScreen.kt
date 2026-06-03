package com.example.meduza.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meduza.data.model.OnlineSong
import com.example.meduza.playback.PlaybackUiState
import com.example.meduza.ui.components.SongRow
import com.example.meduza.ui.theme.*

/**
 * SearchScreen — search YouTube Music songs + browse genre categories.
 *
 * Made with ❤️ by Akyyra — https://github.com/akyyra/meduza
 */
@Composable
fun SearchScreen(
    query         : String,
    onQueryChange : (String) -> Unit,
    isLoading     : Boolean,
    errorMessage  : String?,
    results       : List<OnlineSong>,
    playbackState : PlaybackUiState,
    onPlay        : (OnlineSong) -> Unit,
    onCategorySelect : (String) -> Unit = {},
) {
    val mc = LocalMeduzaColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Text("Search", fontSize = 22.sp, fontWeight = FontWeight.Black, color = mc.textPrimary)
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value         = query,
            onValueChange = onQueryChange,
            modifier      = Modifier.fillMaxWidth(),
            placeholder   = { Text("Songs, artists, playlists…", color = mc.textSecondary) },
            singleLine    = true,
            leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary) },
            trailingIcon  = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = mc.textSecondary)
                    }
                }
            },
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = mc.accent,
                unfocusedBorderColor    = mc.border,
                focusedContainerColor   = mc.surface,
                unfocusedContainerColor = mc.surface,
                focusedTextColor        = mc.textPrimary,
                unfocusedTextColor      = mc.textPrimary,
                cursorColor             = mc.accent,
            ),
            shape = androidx.compose.foundation.shape.CircleShape,
        )
        Spacer(Modifier.height(16.dp))

        if (query.isBlank()) {
            Text("Browse", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = mc.textSecondary)
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns               = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement   = Arrangement.spacedBy(12.dp),
                contentPadding        = PaddingValues(bottom = 160.dp),
                modifier              = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(SearchCategories) { cat ->
                    val gradient = Brush.linearGradient(
                        colors = listOf(cat.color, cat.color.copy(alpha = 0.5f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(gradient)
                            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .clickable { onCategorySelect(cat.query) }
                            .padding(16.dp),
                    ) {
                        Text(
                            cat.name,
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White,
                            modifier   = Modifier.align(Alignment.TopStart),
                        )
                    }
                }
            }
        } else {
            when {
                isLoading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color       = mc.accent,
                        strokeWidth = 2.dp,
                        modifier    = Modifier.size(28.dp),
                    )
                }
                !errorMessage.isNullOrBlank() -> Text(
                    text     = errorMessage,
                    color    = Color(0xFFCF6679),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                else -> LazyColumn(
                    modifier            = Modifier.fillMaxWidth().weight(1f),
                    contentPadding      = PaddingValues(bottom = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(results) { _, song ->
                        SongRow(
                            title     = song.title,
                            artist    = song.artist,
                            meta      = song.durationSeconds?.let { formatDuration(it * 1000L) } ?: "",
                            videoId   = song.videoId,
                            thumbUrl  = song.thumbnailUrl ?: thumbUrl(song.videoId),
                            isPlaying = playbackState.isPlaying && playbackState.mediaId == song.videoId,
                            onClick   = { onPlay(song) },
                        )
                    }
                }
            }
        }
    }
}
