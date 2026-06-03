package com.example.meduza.playback

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.meduza.data.repository.OnlineMediaRepository
import com.example.meduza.core.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * PlaybackViewModel — bridges the MEDUZA Intelligence Engine with ExoPlayer.
 *
 * Key responsibilities:
 *  - Manages MediaController lifecycle and listener attachment
 *  - Feeds artist taste data to MeduzaIntelligenceEngine for smart queuing
 *  - Runs intelligent shuffle AND intelligent normal-play ordering
 *  - Auto-queues the next radio track using the best seed from taste data
 *  - Pre-resolves stream URLs for gapless playback
 *
 * Made with ❤️ by Akyyra — https://github.com/akyyra/meduza
 */
class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private val _controller = MutableStateFlow<MediaController?>(null)
    private val _state      = MutableStateFlow(PlaybackUiState())
    private var progressJob: Job? = null

    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    // Singleton repo — no new instances per operation
    private val repo     = OnlineMediaRepository.getInstance()
    private val settings get() = SettingsManager.getInstance(getApplication())

    // ── Taste Tracking ─────────────────────────────────────────────────────────
    // Persisted across sessions via SettingsManager. Top-50 artists by play count.
    private val artistPlayCount = mutableMapOf<String, Int>().also { map ->
        try {
            val saved = SettingsManager.getInstance(application).savedArtistPlayCounts
            val arr   = org.json.JSONArray(saved)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                map[obj.getString("artist")] = obj.getInt("count")
            }
        } catch (_: Exception) {}
    }

    // ── Recency Tracking ──────────────────────────────────────────────────────
    // Sliding set of last 20 played mediaIds — used by the intelligence engine
    // to penalise recently heard songs and keep the queue fresh.
    private val recentlyPlayedIds = LinkedHashSet<String>().also { set ->
        try {
            val saved = SettingsManager.getInstance(application).recentlyPlayedIds
            val arr   = org.json.JSONArray(saved)
            for (i in 0 until arr.length()) set.add(arr.getString(i))
        } catch (_: Exception) {}
    }

    // ── Playback Mode State ────────────────────────────────────────────────────
    private var intelligentShuffleEnabled  = false
    private var intelligentNormalPlayActive = false  // normal play also uses intelligence
    private var errorSkipJob: Job? = null

    // ── Playback Queue State ───────────────────────────────────────────────────
    private var currentQueue: MeduzaQueue = StaticListQueue()
    private var originalQueueItems: List<MediaItem> = emptyList()

    // ── Queue Context ──────────────────────────────────────────────────────────
    // Human-readable name of what's currently playing (playlist, section, search)
    private val _queueContext = MutableStateFlow("Meduza")
    val queueContext: StateFlow<String> = _queueContext.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────

    init {
        val context = application.applicationContext
        context.startService(Intent(context, MeduzaPlaybackService::class.java))

        viewModelScope.launch {
            val token      = SessionToken(context, ComponentName(context, MeduzaPlaybackService::class.java))
            val controller = MediaController.Builder(context, token).buildAsync().await()
            _controller.value = controller
            attachListener(controller)
            refreshState(controller)
            startProgressUpdates(controller)
        }
    }

    // ── Listener ───────────────────────────────────────────────────────────────

    private fun attachListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean)             = refreshState(controller)
            override fun onMediaMetadataChanged(metadata: MediaMetadata)   = refreshState(controller)
            override fun onTimelineChanged(timeline: Timeline, reason: Int) = refreshState(controller)
            override fun onRepeatModeChanged(repeatMode: Int)               = refreshState(controller)
            override fun onShuffleModeEnabledChanged(enabled: Boolean)      = refreshState(controller)

            // Auto-skip on error: prevents stuck/frozen player when a stream fails
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.w("PlaybackViewModel", "Playback error, auto-skipping: ${error.message}")
                errorSkipJob?.cancel()
                errorSkipJob = viewModelScope.launch {
                    delay(1_200L)
                    val c = _controller.value ?: return@launch
                    if (c.hasNextMediaItem()) {
                        c.seekToNextMediaItem()
                        c.play()
                    } else {
                        delay(2_000L)
                        c.prepare()
                        c.play()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                refreshState(controller)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshState(controller)
                if (mediaItem == null || mediaItem.mediaId.isBlank()) return

                // ── Taste tracking — persist immediately ──────────────────────
                val artist = mediaItem.mediaMetadata.artist?.toString() ?: ""
                if (artist.isNotBlank()) {
                    val key = artist.lowercase().trim()
                    artistPlayCount[key] = (artistPlayCount[key] ?: 0) + 1
                    persistArtistPlayCounts()
                }

                // ── Recency tracking ─────────────────────────────────────────
                val mediaId = mediaItem.mediaId
                if (mediaId.isNotBlank()) {
                    recentlyPlayedIds.add(mediaId)
                    // Keep only the last 20
                    if (recentlyPlayedIds.size > 20) {
                        recentlyPlayedIds.remove(recentlyPlayedIds.iterator().next())
                    }
                    persistRecentlyPlayedIds()
                }

                val currentIndex = controller.currentMediaItemIndex
                val totalItems   = controller.mediaItemCount

                loadMoreQueueItems(controller, mediaId)

                // Pre-resolve next song stream in background for gapless playback
                val nextIndex = currentIndex + 1
                if (nextIndex < totalItems) {
                    val nextItem = controller.getMediaItemAt(nextIndex)
                    if (nextItem.mediaId.length == 11) {
                        viewModelScope.launch(Dispatchers.IO) {
                            runCatching {
                                repo.resolveStreamUrl(nextItem.mediaId, settings.audioQuality, settings.lowNetworkMode)
                            }
                        }
                    }
                }
            }
        })
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    private fun persistArtistPlayCounts() {
        try {
            val arr = org.json.JSONArray()
            artistPlayCount.entries.sortedByDescending { it.value }.take(50).forEach { (artist, count) ->
                arr.put(org.json.JSONObject().put("artist", artist).put("count", count))
            }
            settings.savedArtistPlayCounts = arr.toString()
        } catch (_: Exception) {}
    }

    private fun persistRecentlyPlayedIds() {
        try {
            val arr = org.json.JSONArray()
            recentlyPlayedIds.toList().takeLast(20).forEach { arr.put(it) }
            settings.recentlyPlayedIds = arr.toString()
        } catch (_: Exception) {}
    }

    // ── Intelligent Auto-Queue ─────────────────────────────────────────────────

    private var isQueueLoading = false

    /**
     * Loads the next batch of recommended songs or paginated results when approaching the end of the queue.
     * Uses MeduzaQueue and falls back to a watch-next YouTubeRadioQueue seeded by the current track's ID.
     */
    private fun loadMoreQueueItems(controller: MediaController, currentMediaId: String) {
        if (isQueueLoading) return
        val totalItems = controller.mediaItemCount
        val currentIndex = controller.currentMediaItemIndex
        val itemsRemaining = totalItems - currentIndex

        viewModelScope.launch {
            if (currentQueue.hasNextPage() && itemsRemaining <= 5) {
                isQueueLoading = true
                try {
                    val nextItems = withContext(Dispatchers.IO) { currentQueue.nextPage() }
                    val existingIds = (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId }.toSet()
                    val filtered = nextItems.filter { it.mediaId !in existingIds }
                    if (filtered.isNotEmpty()) {
                        controller.addMediaItems(filtered)
                        refreshState(controller)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackViewModel", "Failed to load next page of queue", e)
                } finally {
                    isQueueLoading = false
                }
            } else if (currentQueue !is StaticListQueue && !currentQueue.hasNextPage() && itemsRemaining <= 3 && currentMediaId.length == 11) {
                isQueueLoading = true
                try {
                    // Use the Intelligence Engine to select the best radio seed based on user taste
                    val queueItems = (0 until controller.mediaItemCount).map { i ->
                        val item = controller.getMediaItemAt(i)
                        Triple(
                            item.mediaMetadata.title?.toString() ?: "",
                            item.mediaMetadata.artist?.toString() ?: "",
                            item.mediaId
                        )
                    }
                    val seedId = MeduzaIntelligenceEngine.selectBestRadioSeed(
                        queueItems = queueItems,
                        artistPlayCounts = artistPlayCount
                    ) ?: currentMediaId

                    // Bootstrap a new YouTubeRadioQueue
                    val radioQueue = YouTubeRadioQueue(
                        videoId = seedId,
                        repo = repo
                    )
                    val nextItems = withContext(Dispatchers.IO) { radioQueue.nextPage() }
                    val existingIds = (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId }.toSet()
                    val filtered = nextItems.filter { it.mediaId !in existingIds }
                    if (filtered.isNotEmpty()) {
                        controller.addMediaItems(filtered)
                        currentQueue = radioQueue
                        refreshState(controller)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackViewModel", "Failed to bootstrap YouTube radio queue", e)
                } finally {
                    isQueueLoading = false
                }
            }
        }
    }

    // ── State ──────────────────────────────────────────────────────────────────

    private fun refreshState(controller: MediaController) {
        val metadata    = controller.mediaMetadata
        val currentItem = controller.currentMediaItem
        _state.update { prev ->
            prev.copy(
                title              = metadata.title?.toString() ?: "Nothing playing",
                artist             = metadata.artist?.toString() ?: "",
                mediaId            = currentItem?.mediaId ?: "",
                artworkUrl         = metadata.artworkUri?.toString(),
                isPlaying          = controller.isPlaying,
                durationMs         = controller.duration.coerceAtLeast(0L),
                positionMs         = controller.currentPosition.coerceAtLeast(0L),
                hasNext            = controller.hasNextMediaItem(),
                hasPrev            = controller.hasPreviousMediaItem(),
                repeatMode         = controller.repeatMode,
                shuffleModeEnabled = intelligentShuffleEnabled,
                queueContext       = _queueContext.value,
            )
        }
    }

    private fun startProgressUpdates(controller: MediaController) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                _state.update {
                    it.copy(
                        positionMs = controller.currentPosition.coerceAtLeast(0L),
                        durationMs = controller.duration.coerceAtLeast(0L),
                    )
                }
                delay(500)
            }
        }
    }

    // ── Playback Commands ──────────────────────────────────────────────────────

    /**
     * Plays a list of items starting at [startIndex].
     *
     * Behaviour:
     *  - Pre-resolves the target item's stream URL before handing to ExoPlayer
     *    → instant start with no buffering delay.
     *  - If intelligent normal-play is enabled (default), the remaining queue
     *    is reordered by the MEDUZA engine for a smart listening experience.
     *  - Pre-fetches the next item's URL in background for gapless transitions.
     *
     * @param items      MediaItems to play
     * @param startIndex Index of the item to start from
     * @param contextName Human-readable context label (e.g. "Top Hits", "Search Results")
     */
    fun playItems(items: List<MediaItem>, startIndex: Int = 0, contextName: String = "Meduza") {
        val controller = _controller.value ?: return
        if (items.isEmpty()) return
        val safeIndex  = startIndex.coerceIn(0, items.size - 1)
        val targetItem = items[safeIndex]

        _queueContext.value       = contextName
        intelligentShuffleEnabled = false  // Reset shuffle when explicit play triggered

        viewModelScope.launch {
            // Reset queue for new playback session
            currentQueue = StaticListQueue()
            originalQueueItems = items

            var resolvedItems = items.toMutableList()

            // ── Pre-resolve target stream URL ────────────────────────────────
            if (targetItem.mediaId.length == 11) {
                val resolved = withContext(Dispatchers.IO) {
                    runCatching {
                        repo.resolveStreamUrl(
                            videoId        = targetItem.mediaId,
                            quality        = settings.audioQuality,
                            lowNetworkMode = settings.lowNetworkMode,
                        ).getOrNull()
                    }.getOrNull()
                }
                if (resolved != null) {
                    resolvedItems[safeIndex] = MediaItem.Builder()
                        .setMediaId(targetItem.mediaId)
                        .setUri(android.net.Uri.parse(resolved))
                        .setMediaMetadata(targetItem.mediaMetadata)
                        .build()
                }
            }

            // Reorder the remaining queue (after the start item) using the
            // MEDUZA engine for a smart, non-linear listening experience.
            // (Removed per user request: normal play should remain linear)
            if (items.size > 2) {
                // We keep the original order for linear play.
            }

            try {
                controller.setMediaItems(resolvedItems, safeIndex, 0L)
                controller.prepare()
                controller.play()
                refreshState(controller)
            } catch (e: Exception) {
                // Fallback: let ResolvingDataSource handle it lazily
                runCatching {
                    controller.setMediaItems(items, safeIndex, 0L)
                    controller.prepare()
                    controller.play()
                    refreshState(controller)
                }
                android.util.Log.e("PlaybackViewModel", "playItems fallback used", e)
            }

            // ── Prefetch next item ───────────────────────────────────────────
            val nextIdx = 1  // After reorder, next is always at index 1
            if (nextIdx < resolvedItems.size && resolvedItems[nextIdx].mediaId.length == 11) {
                launch(Dispatchers.IO) {
                    runCatching {
                        repo.resolveStreamUrl(resolvedItems[nextIdx].mediaId, settings.audioQuality, settings.lowNetworkMode)
                    }
                }
            }
        }
    }

    /**
     * Plays a single song immediately (with pre-resolution for speed) and
     * builds a YouTube radio queue around it, fetching context recommendations
     * immediately to populate the player's upcoming queue.
     */
    fun playSongWithRadio(song: MediaItem, contextName: String = "Meduza") {
        val controller = _controller.value ?: return
        _queueContext.value = contextName
        intelligentShuffleEnabled = false

        viewModelScope.launch {
            val items = listOf(song)
            var resolvedItems = items.toMutableList()

            // Pre-resolve the target stream URL for instant start
            if (song.mediaId.length == 11) {
                val resolved = withContext(Dispatchers.IO) {
                    runCatching {
                        repo.resolveStreamUrl(
                            videoId = song.mediaId,
                            quality = settings.audioQuality,
                            lowNetworkMode = settings.lowNetworkMode
                        ).getOrNull()
                    }.getOrNull()
                }
                if (resolved != null) {
                    resolvedItems[0] = MediaItem.Builder()
                        .setMediaId(song.mediaId)
                        .setUri(android.net.Uri.parse(resolved))
                        .setMediaMetadata(song.mediaMetadata)
                        .build()
                }
            }

            try {
                controller.setMediaItems(resolvedItems, 0, 0L)
                controller.prepare()
                controller.play()
                refreshState(controller)
            } catch (e: Exception) {
                runCatching {
                    controller.setMediaItems(items, 0, 0L)
                    controller.prepare()
                    controller.play()
                    refreshState(controller)
                }
                android.util.Log.e("PlaybackViewModel", "playSongWithRadio fallback used", e)
            }

            // Immediately bootstrap the radio queue and load 20 recommended tracks
            val radioQueue = YouTubeRadioQueue(videoId = song.mediaId, repo = repo)
            currentQueue = radioQueue
            try {
                val nextItems = withContext(Dispatchers.IO) { radioQueue.nextPage() }
                val existingIds = (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId }.toSet()
                val filtered = nextItems.filter { it.mediaId !in existingIds }
                if (filtered.isNotEmpty()) {
                    controller.addMediaItems(filtered)
                    refreshState(controller)
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaybackViewModel", "Failed to seed immediate radio queue", e)
            }
        }
    }

    fun togglePlayPause() {
        val controller = _controller.value ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun toggleRepeatMode() {
        val controller = _controller.value ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else                   -> Player.REPEAT_MODE_OFF
        }
        refreshState(controller)
    }

    /**
     * Toggles MEDUZA Intelligent Shuffle.
     *
     * When enabled:
     *  - The upcoming portion of the queue is reordered using the full
     *    MEDUZA multi-signal algorithm (energy arc, taste, recency, diversity).
     *  - ExoPlayer's built-in shuffle is NOT used — we manage order ourselves
     *    so we have full control over the intelligence logic.
     */
    fun toggleShuffleMode() {
        val controller = _controller.value ?: return
        intelligentShuffleEnabled = !intelligentShuffleEnabled

        if (intelligentShuffleEnabled) {
            val total        = controller.mediaItemCount
            val currentIndex = controller.currentMediaItemIndex
            if (currentIndex < total - 1) {
                val upcoming = (currentIndex + 1 until total).map { controller.getMediaItemAt(it) }
                controller.removeMediaItems(currentIndex + 1, total)

                // Run the MEDUZA intelligence engine shuffle
                val triples = upcoming.map { item ->
                    Triple(
                        item.mediaMetadata.title?.toString() ?: "",
                        item.mediaMetadata.artist?.toString() ?: "",
                        item.mediaId
                    )
                }
                val orderedIndices = MeduzaIntelligenceEngine.shuffleWithIntelligence(
                    items             = triples,
                    artistPlayCounts  = artistPlayCount,
                    recentlyPlayedIds = recentlyPlayedIds,
                )
                val shuffled = orderedIndices.map { upcoming[it] }
                controller.addMediaItems(shuffled)
            }
        } else {
            // Restore original linear order for the upcoming queue
            val total        = controller.mediaItemCount
            val currentIndex = controller.currentMediaItemIndex
            val currentItem  = controller.currentMediaItem
            if (currentItem != null && originalQueueItems.isNotEmpty()) {
                val origIndex = originalQueueItems.indexOfFirst { it.mediaId == currentItem.mediaId }
                if (origIndex != -1) {
                    val originalUpcoming = originalQueueItems.drop(origIndex + 1)
                    if (originalUpcoming.isNotEmpty()) {
                        if (currentIndex < total - 1) {
                            controller.removeMediaItems(currentIndex + 1, total)
                        }
                        controller.addMediaItems(originalUpcoming)
                    }
                }
            }
        }
        refreshState(controller)
    }

    fun next() {
        val controller = _controller.value ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            controller.play()
        }
    }

    fun previous() {
        val controller = _controller.value ?: return
        if (controller.currentPosition > 3000) controller.seekTo(0)
        else controller.seekToPreviousMediaItem()
        controller.play()
    }

    fun seekTo(positionMs: Long) {
        _controller.value?.seekTo(positionMs.coerceAtLeast(0L))
    }

    /** Expose taste data for display/debug purposes. */
    fun getTopArtists(limit: Int = 5): List<Pair<String, Int>> =
        artistPlayCount.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        _controller.value?.release()
    }
}

// ── UI State ───────────────────────────────────────────────────────────────────

/**
 * Immutable snapshot of playback UI state.
 * Collected by Composables via [PlaybackViewModel.state].
 */
data class PlaybackUiState(
    val title              : String  = "Nothing playing",
    val artist             : String  = "",
    val mediaId            : String  = "",
    val artworkUrl         : String? = null,
    val isPlaying          : Boolean = false,
    val positionMs         : Long    = 0L,
    val durationMs         : Long    = 0L,
    val hasNext            : Boolean = false,
    val hasPrev            : Boolean = false,
    val repeatMode         : Int     = 0,
    val shuffleModeEnabled : Boolean = false,
    val queueContext       : String  = "Meduza",
)
