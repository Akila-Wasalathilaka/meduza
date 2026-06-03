package com.example.meduza.playback

import androidx.media3.common.MediaItem
import com.example.meduza.data.repository.OnlineMediaRepository
import com.example.meduza.core.settings.SettingsManager
import moe.koiverse.archivetune.innertube.models.WatchEndpoint

interface MeduzaQueue {
    fun hasNextPage(): Boolean
    suspend fun nextPage(): List<MediaItem>
}

class StaticListQueue : MeduzaQueue {
    override fun hasNextPage(): Boolean = false
    override suspend fun nextPage(): List<MediaItem> = emptyList()
}

class YouTubeRadioQueue(
    private var videoId: String,
    private val repo: OnlineMediaRepository,
    private var continuation: String? = null,
    private var endpoint: WatchEndpoint? = null
) : MeduzaQueue {
    private var hasMore = true

    override fun hasNextPage(): Boolean = hasMore

    override suspend fun nextPage(): List<MediaItem> {
        if (!hasMore) return emptyList()
        val result = repo.getRadioQueue(
            videoId = videoId,
            continuation = continuation,
            endpoint = endpoint
        )
        if (result == null || result.songs.isEmpty()) {
            hasMore = false
            return emptyList()
        }
        continuation = result.nextContinuation
        endpoint = result.nextEndpoint
        if (continuation == null && endpoint == null) {
            hasMore = false
        }
        
        return result.songs.map { song ->
            val metadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setArtworkUri(android.net.Uri.parse(
                    song.thumbnailUrl ?: "https://i.ytimg.com/vi/${song.videoId}/hqdefault.jpg"
                ))
                .build()
            MediaItem.Builder()
                .setMediaId(song.videoId)
                .setUri("youtube://${song.videoId}")
                .setMediaMetadata(metadata)
                .build()
        }
    }
}
