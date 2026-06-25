package com.example.meduza.data.repository

import android.util.Log
import com.example.meduza.data.model.HomeChip
import com.example.meduza.data.model.HomePageResult
import com.example.meduza.data.model.HomeSection
import com.example.meduza.data.model.OnlineSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.koiverse.archivetune.innertube.NewPipeUtils
import moe.koiverse.archivetune.innertube.YouTube
import moe.koiverse.archivetune.innertube.models.SongItem
import moe.koiverse.archivetune.innertube.models.YouTubeClient
import moe.koiverse.archivetune.innertube.models.response.PlayerResponse

private const val TAG = "OnlineMediaRepository"

class OnlineMediaRepository private constructor() {

    companion object {
        private data class CacheEntry(val url: String, val expiresAt: Long)
        // 5 min TTL — YouTube signed URLs expire; don't serve stale ones
        private val streamUrlCache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()
        private const val CACHE_TTL_MS = 5 * 60 * 1000L

        @Volatile private var INSTANCE: OnlineMediaRepository? = null
        fun getInstance(): OnlineMediaRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: OnlineMediaRepository().also { INSTANCE = it }
            }
    }

    /**
     * Ensures visitor data is populated.
     * ArchiveTune pattern: uses sw.js_data endpoint which is more reliable than the API.
     */
    suspend fun ensureVisitorData() = withContext(Dispatchers.IO) {
        if (YouTube.visitorData.isNullOrBlank()) {
            val result = runCatching { YouTube.visitorData().getOrNull() }
            result.getOrNull()?.let { vd ->
                if (vd.isNotBlank()) {
                    YouTube.visitorData = vd
                    Log.d(TAG, "Visitor data initialized successfully")
                }
            }
        }
    }

    suspend fun searchSongs(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        ensureVisitorData()
        val result = runCatching {
            YouTube.searchSummary(query).getOrNull()
        }
        result.getOrNull()?.summaries?.flatMap { it.items }
            ?.distinctBy { it.id }
            ?.mapNotNull { item ->
                when (item) {
                    is moe.koiverse.archivetune.innertube.models.SongItem -> OnlineSong(
                        videoId         = item.id,
                        title           = item.title,
                        artist          = item.artists.firstOrNull()?.name ?: "Unknown artist",
                        durationSeconds = item.duration,
                        thumbnailUrl    = item.thumbnail,
                        type            = "song"
                    )
                    is moe.koiverse.archivetune.innertube.models.PlaylistItem -> OnlineSong(
                        videoId         = item.id,
                        title           = item.title,
                        artist          = item.author?.name ?: "Playlist",
                        durationSeconds = null,
                        thumbnailUrl    = item.thumbnail,
                        type            = "playlist"
                    )
                    is moe.koiverse.archivetune.innertube.models.ArtistItem -> OnlineSong(
                        videoId         = item.id,
                        title           = item.title,
                        artist          = item.subscriberCountText ?: "Artist",
                        durationSeconds = null,
                        thumbnailUrl    = item.thumbnail,
                        type            = "artist"
                    )
                    is moe.koiverse.archivetune.innertube.models.AlbumItem -> OnlineSong(
                        videoId         = item.playlistId,
                        title           = item.title,
                        artist          = item.artists?.firstOrNull()?.name ?: "Album",
                        durationSeconds = null,
                        thumbnailUrl    = item.thumbnail,
                        type            = "playlist"
                    )
                    else -> null
                }
            } ?: emptyList()
    }

    /**
     * Resolves a playable stream URL for the given videoId.
     *
     * Strategy (learned from ArchiveTune/YTPlayerUtils):
     * 1. ANDROID_TESTSUITE is the BEST first attempt — no signature timestamp needed,
     *    no login needed, returns direct URLs (not ciphered), very high success rate.
     * 2. ANDROID_VR_NO_AUTH is second best — also no signature, no login, direct URLs.
     * 3. IOS is third — no signature needed, direct URLs.
     * 4. ANDROID_VR variants as additional fallbacks.
     * 5. WEB_REMIX last — needs signature timestamp and is most likely to 400.
     *
     * Key fix: Clients with useSignatureTimestamp=false get signatureTimestamp=null,
     * which is what they expect. Passing null to a timestamp-requiring client = 400 error.
     */
    suspend fun resolveStreamUrl(
        videoId: String,
        quality: String = "medium",
        lowNetworkMode: Boolean = false,
    ): Result<String> = withContext(Dispatchers.IO) {
        val cacheKey = "${videoId}_${quality}_${lowNetworkMode}"
        val cached = streamUrlCache[cacheKey]
        if (cached != null && System.currentTimeMillis() < cached.expiresAt) {
            Log.d(TAG, "Serving cached stream URL for videoId=$videoId")
            return@withContext Result.success(cached.url)
        }

        runCatching {
            ensureVisitorData()

            if (YouTube.poToken.isNullOrBlank()) {
                val visitor = YouTube.visitorData ?: "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3"
                YouTube.webClientPoTokenEnabled = true
                YouTube.poToken = moe.koiverse.archivetune.innertube.utils.PoTokenGenerator.generateColdStartToken(visitor, "player")
                Log.d(TAG, "Dynamically generated PO Token: ${YouTube.poToken}")
            }

            // We will fetch the signature timestamp lazily ONLY if we hit a client that requires it (e.g. MOBILE/WEB_REMIX).
            // This completely avoids a 2-second blocking HTTP embed request for high-speed clients like ANDROID_TESTSUITE!
            var signatureTimestamp: Int? = null
            var fetchedSignature = false

            Log.d(TAG, "Resolving stream for videoId=$videoId")

            // Ordered client list — prioritize clients that work WITHOUT signature timestamps
            // to avoid 400 errors. These are the most reliable for anonymous playback.
            val clientList = listOf(
                YouTubeClient.ANDROID_TESTSUITE,   // Best: no sig, no login, direct urls
                YouTubeClient.ANDROID_VR_NO_AUTH,  // Good: no sig, no login, direct urls
                YouTubeClient.IOS,                 // Good: no sig needed, direct urls
                YouTubeClient.ANDROID_VR_1_61_48,  // Fallback VR variant
                YouTubeClient.ANDROID_VR_1_43_32,  // Fallback VR variant
                YouTubeClient.MOBILE,              // Needs sig timestamp
                YouTubeClient.WEB_REMIX,           // Last resort: needs sig + login
            )

            var selectedFormat: PlayerResponse.StreamingData.Format? = null
            var chosenClient: YouTubeClient = YouTubeClient.ANDROID_TESTSUITE
            var lastError: Throwable? = null

            for (client in clientList) {
                try {
                    // Only pass signatureTimestamp to clients that actually use it and fetch it on-demand
                    val sigTs = if (client.useSignatureTimestamp) {
                        if (!fetchedSignature) {
                            signatureTimestamp = runCatching {
                                NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
                            }.getOrNull()
                            fetchedSignature = true
                            Log.d(TAG, "Fetched signature timestamp lazily on-demand: $signatureTimestamp")
                        }
                        signatureTimestamp
                    } else {
                        null
                    }

                    Log.d(TAG, "Trying client: ${client.clientName} v${client.clientVersion}")

                    val response = YouTube.player(
                        videoId = videoId,
                        client = client,
                        signatureTimestamp = sigTs,
                        setLogin = false,
                    ).getOrThrow()

                    // Check playability status first
                    if (response.playabilityStatus.status != "OK") {
                        Log.w(TAG, "Client ${client.clientName} returned status: ${response.playabilityStatus.status}, reason: ${response.playabilityStatus.reason}")
                        continue
                    }
                     val formats = response.streamingData?.adaptiveFormats.orEmpty()
                        .filter { it.isAudio && (it.mimeType.contains("audio/mp4") || it.mimeType.contains("audio/webm")) }

                     if (formats.isEmpty()) {
                         Log.w(TAG, "Client ${client.clientName} returned no audio formats")
                         continue
                     }

                     val selected = when {
                         lowNetworkMode || quality == "low" -> {
                             // Target stable 48kbps-64kbps modern streams (AAC 48kbps / Opus 50kbps)
                             // for fast buffering and instant playback, instead of legacy throttled streams.
                             formats.minByOrNull { kotlin.math.abs(it.bitrate - 48000) }
                         }
                         quality == "high" -> {
                             formats.maxByOrNull { it.bitrate }
                         }
                         else -> {
                             // Medium: find closest to 128kbps
                             formats.minByOrNull { kotlin.math.abs(it.bitrate - 128000) }
                         }
                     } ?: formats.firstOrNull()

                    if (selected != null) {
                         selectedFormat = selected
                         chosenClient = client
                         Log.d(TAG, "Selected format from ${client.clientName}: itag=${selected.itag}, bitrate=${selected.bitrate}, mimeType=${selected.mimeType}")
                         break
                     }
                } catch (e: Exception) {
                    Log.w(TAG, "Client ${client.clientName} failed: ${e.message}")
                    lastError = e
                }
            }

            val finalFormat = selectedFormat
                ?: throw (lastError ?: IllegalStateException("No audio stream available for $videoId"))

            // Resolve the actual playable URL (handles ciphered URLs via NewPipe JS deobfuscation)
            val resolvedUrl = NewPipeUtils.getStreamUrl(finalFormat, videoId, chosenClient).getOrThrow()
            val cacheKey = "${videoId}_${quality}_${lowNetworkMode}"
            streamUrlCache[cacheKey] = CacheEntry(resolvedUrl, System.currentTimeMillis() + CACHE_TTL_MS)
            resolvedUrl
        }
    }

    /**
     * Resolves related/recommended songs matching a videoId vibe using YouTube Music's ML watch-next radio API.
     * This delivers diverse, genre-matched related recommendations exactly like Spotify!
     */
    suspend fun getRelatedSongs(videoId: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        ensureVisitorData()
        val endpoint = moe.koiverse.archivetune.innertube.models.WatchEndpoint(videoId = videoId)
        val result = YouTube.next(endpoint)
        result.getOrNull()?.items?.map { song ->
            OnlineSong(
                videoId = song.id,
                title = song.title,
                artist = song.artists.firstOrNull()?.name ?: "Unknown artist",
                durationSeconds = song.duration,
                thumbnailUrl = song.thumbnail
            )
        } ?: emptyList()
    }

    data class RadioQueueResult(
        val songs: List<OnlineSong>,
        val nextContinuation: String?,
        val nextEndpoint: moe.koiverse.archivetune.innertube.models.WatchEndpoint?
    )

    /**
     * Fetches the official infinite radio queue from YouTube Music.
     * Uses followAutomixPreview = true to get a continuous stream of songs.
     */
    suspend fun getRadioQueue(
        videoId: String,
        continuation: String? = null,
        endpoint: moe.koiverse.archivetune.innertube.models.WatchEndpoint? = null
    ): RadioQueueResult? = withContext(Dispatchers.IO) {
        ensureVisitorData()
        val watchEndpoint = endpoint ?: moe.koiverse.archivetune.innertube.models.WatchEndpoint(videoId = videoId)
        val result = runCatching {
            YouTube.next(
                endpoint = watchEndpoint,
                continuation = continuation,
                followAutomixPreview = true
            ).getOrNull()
        }.getOrNull() ?: return@withContext null

        val songs = result.items.map { song ->
            OnlineSong(
                videoId = song.id,
                title = song.title,
                artist = song.artists.firstOrNull()?.name ?: "Unknown artist",
                durationSeconds = song.duration,
                thumbnailUrl = song.thumbnail
            )
        }

        RadioQueueResult(
            songs = songs,
            nextContinuation = result.continuation,
            nextEndpoint = result.endpoint
        )
    }

    /**
     * Fetches the personalized YouTube Music home page.
     * Returns HomePageResult with sections, category chips, and a continuation token.
     * Mirrors ArchiveTune's YouTube.home() → HomePage pattern.
     */
    suspend fun getHomeData(): HomePageResult = withContext(Dispatchers.IO) {
        ensureVisitorData()
        val result = runCatching { YouTube.home().getOrNull() }
        val homePage = result.getOrNull() ?: return@withContext HomePageResult(emptyList(), emptyList(), null)

        val chips = homePage.chips
            ?.filterNot { it.title.contains("podcasts", ignoreCase = true) }
            ?.map { HomeChip(title = it.title, params = it.endpoint?.params ?: "") }
            .orEmpty()

        val rawSections = homePage.sections.map { section ->
            val songs = section.items.mapNotNull { item ->
                when (item) {
                    is moe.koiverse.archivetune.innertube.models.SongItem -> OnlineSong(
                        videoId         = item.id,
                        title           = item.title,
                        artist          = item.artists.firstOrNull()?.name ?: "Unknown artist",
                        durationSeconds = item.duration,
                        thumbnailUrl    = item.thumbnail,
                        type            = "song"
                    )
                    is moe.koiverse.archivetune.innertube.models.PlaylistItem -> OnlineSong(
                        videoId         = item.id,
                        title           = item.title,
                        artist          = item.author?.name ?: "Playlist",
                        durationSeconds = null,
                        thumbnailUrl    = item.thumbnail,
                        type            = "playlist"
                    )
                    is moe.koiverse.archivetune.innertube.models.ArtistItem -> OnlineSong(
                        videoId         = item.id,
                        title           = item.title,
                        artist          = item.subscriberCountText ?: "Artist",
                        durationSeconds = null,
                        thumbnailUrl    = item.thumbnail,
                        type            = "artist"
                    )
                    is moe.koiverse.archivetune.innertube.models.AlbumItem -> OnlineSong(
                        videoId         = item.playlistId,
                        title           = item.title,
                        artist          = item.artists?.firstOrNull()?.name ?: "Album",
                        durationSeconds = null,
                        thumbnailUrl    = item.thumbnail,
                        type            = "playlist"
                    )
                    else -> null
                }
            }
            HomeSection(title = section.title ?: "Recommended", songs = songs)
        }.filter { it.songs.isNotEmpty() }

        val (songSections, otherSections) = rawSections.partition { sec ->
            sec.title.contains("quick picks", ignoreCase = true) ||
            sec.title.contains("start radio", ignoreCase = true) ||
            sec.title.contains("forgotten favorites", ignoreCase = true) ||
            sec.title.contains("listen again", ignoreCase = true) ||
            (sec.songs.isNotEmpty() && sec.songs.all { it.type == "song" })
        }
        val sections = songSections + otherSections

        HomePageResult(
            sections     = sections,
            chips        = chips,
            continuation = homePage.continuation,
        )
    }

    /**
     * Fetches a chip-filtered home page (e.g. "Relax", "Workout").
     * Uses the chip's params to call YouTube.home(params=...).
     */
    suspend fun getHomeDataForChip(chip: HomeChip): HomePageResult = withContext(Dispatchers.IO) {
        ensureVisitorData()
        val result = runCatching {
            YouTube.home(params = chip.params).getOrNull()
        }
        val homePage = result.getOrNull() ?: return@withContext HomePageResult(emptyList(), emptyList(), null)

        val sections = homePage.sections.map { section ->
            val songs = section.items.mapNotNull { item ->
                when (item) {
                    is moe.koiverse.archivetune.innertube.models.SongItem -> OnlineSong(
                        videoId         = item.id,
                        title           = item.title,
                        artist          = item.artists.firstOrNull()?.name ?: "Unknown artist",
                        durationSeconds = item.duration,
                        thumbnailUrl    = item.thumbnail,
                        type            = "song"
                    )
                    is moe.koiverse.archivetune.innertube.models.PlaylistItem -> OnlineSong(
                        videoId         = item.id,
                        title           = item.title,
                        artist          = item.author?.name ?: "Playlist",
                        durationSeconds = null,
                        thumbnailUrl    = item.thumbnail,
                        type            = "playlist"
                    )
                    is moe.koiverse.archivetune.innertube.models.ArtistItem -> OnlineSong(
                        videoId         = item.id,
                        title           = item.title,
                        artist          = item.subscriberCountText ?: "Artist",
                        durationSeconds = null,
                        thumbnailUrl    = item.thumbnail,
                        type            = "artist"
                    )
                    is moe.koiverse.archivetune.innertube.models.AlbumItem -> OnlineSong(
                        videoId         = item.playlistId,
                        title           = item.title,
                        artist          = item.artists?.firstOrNull()?.name ?: "Album",
                        durationSeconds = null,
                        thumbnailUrl    = item.thumbnail,
                        type            = "playlist"
                    )
                    else -> null
                }
            }
            HomeSection(title = section.title ?: chip.title, songs = songs)
        }.filter { it.songs.isNotEmpty() }

        HomePageResult(
            sections     = sections,
            chips        = emptyList(), // chips don't change on filter
            continuation = homePage.continuation,
        )
    }

    /**
     * Loads the next page of home sections using a continuation token.
     * Mirrors ArchiveTune's loadMoreYouTubeItems(continuation).
     */
    suspend fun loadMoreHomeSections(continuation: String): HomePageResult = withContext(Dispatchers.IO) {
        ensureVisitorData()
        val result = runCatching { YouTube.home(continuation = continuation).getOrNull() }
        val homePage = result.getOrNull() ?: return@withContext HomePageResult(emptyList(), emptyList(), null)

        val sections = homePage.sections.map { section ->
            val songs = section.items.mapNotNull { item ->
                when (item) {
                    is moe.koiverse.archivetune.innertube.models.SongItem -> OnlineSong(
                        videoId         = item.id,
                        title           = item.title,
                        artist          = item.artists.firstOrNull()?.name ?: "Unknown artist",
                        durationSeconds = item.duration,
                        thumbnailUrl    = item.thumbnail,
                        type            = "song"
                    )
                    is moe.koiverse.archivetune.innertube.models.PlaylistItem -> OnlineSong(
                        videoId         = item.id,
                        title           = item.title,
                        artist          = item.author?.name ?: "Playlist",
                        durationSeconds = null,
                        thumbnailUrl    = item.thumbnail,
                        type            = "playlist"
                    )
                    is moe.koiverse.archivetune.innertube.models.ArtistItem -> OnlineSong(
                        videoId         = item.id,
                        title           = item.title,
                        artist          = item.subscriberCountText ?: "Artist",
                        durationSeconds = null,
                        thumbnailUrl    = item.thumbnail,
                        type            = "artist"
                    )
                    is moe.koiverse.archivetune.innertube.models.AlbumItem -> OnlineSong(
                        videoId         = item.playlistId,
                        title           = item.title,
                        artist          = item.artists?.firstOrNull()?.name ?: "Album",
                        durationSeconds = null,
                        thumbnailUrl    = item.thumbnail,
                        type            = "playlist"
                    )
                    else -> null
                }
            }
            HomeSection(title = section.title ?: "More for you", songs = songs)
        }.filter { it.songs.isNotEmpty() }

        HomePageResult(
            sections     = sections,
            chips        = emptyList(),
            continuation = homePage.continuation,
        )
    }

    suspend fun getArtistData(browseId: String): List<HomeSection> = withContext(Dispatchers.IO) {
        val result = YouTube.artist(browseId)
        val artistPage = result.getOrNull() ?: return@withContext emptyList()
        
        artistPage.sections.map { section ->
            val songs = section.items.mapNotNull { item ->
                when (item) {
                    is moe.koiverse.archivetune.innertube.models.SongItem -> OnlineSong(
                        videoId         = item.id,
                        title           = item.title,
                        artist          = item.artists.firstOrNull()?.name ?: "Unknown artist",
                        durationSeconds = item.duration,
                        thumbnailUrl    = item.thumbnail,
                        type            = "song"
                    )
                    is moe.koiverse.archivetune.innertube.models.AlbumItem -> OnlineSong(
                        videoId         = item.playlistId, // Use playlistId for album details instead of browseId
                        title           = item.title,
                        artist          = item.artists?.firstOrNull()?.name ?: "Album",
                        durationSeconds = null,
                        thumbnailUrl    = item.thumbnail,
                        type            = "playlist" // Map album to playlist type for simplicity
                    )
                    else -> null
                }
            }
            HomeSection(title = section.title, songs = songs)
        }.filter { it.songs.isNotEmpty() }
    }

    suspend fun getPlaylistData(playlistId: String): List<HomeSection> = withContext(Dispatchers.IO) {
        val result = YouTube.playlist(playlistId)
        val playlistPage = result.getOrNull() ?: return@withContext emptyList()
        
        val songs = playlistPage.songs.map { song ->
            OnlineSong(
                videoId         = song.id,
                title           = song.title,
                artist          = song.artists.firstOrNull()?.name ?: "Unknown artist",
                durationSeconds = song.duration,
                thumbnailUrl    = song.thumbnail,
                type            = "song"
            )
        }
        
        if (songs.isNotEmpty()) {
            listOf(HomeSection(title = playlistPage.playlist.title, songs = songs))
        } else {
            emptyList()
        }
    }

    suspend fun getPlaylistDetails(playlistId: String): com.example.meduza.data.model.PlaylistDetail? = withContext(Dispatchers.IO) {
        val cleanId = playlistId.removePrefix("VL")
        val result = YouTube.playlist(cleanId)
        val page = result.getOrNull() ?: return@withContext null
        val songs = page.songs.map { song ->
            OnlineSong(
                videoId         = song.id,
                title           = song.title,
                artist          = song.artists.firstOrNull()?.name ?: "Unknown artist",
                durationSeconds = song.duration,
                thumbnailUrl    = song.thumbnail,
                type            = "song"
            )
        }
        com.example.meduza.data.model.PlaylistDetail(
            id              = page.playlist.id,
            title           = page.playlist.title,
            description     = page.playlist.description,
            authorName      = page.playlist.author?.name ?: "Playlist",
            authorAvatarUrl = null,
            thumbnailUrl    = page.playlist.thumbnail,
            trackCountText  = page.playlist.songCountText ?: "${songs.size} songs",
            totalDurationText = null,
            isEditable      = page.playlist.isEditable,
            tracks          = songs
        )
    }

    suspend fun getAlbumDetails(browseIdOrPlaylistId: String): com.example.meduza.data.model.PlaylistDetail? = withContext(Dispatchers.IO) {
        val cleanId = browseIdOrPlaylistId.removePrefix("VL")
        if (cleanId.startsWith("MPREb_")) {
            val result = YouTube.album(cleanId)
            val page = result.getOrNull() ?: return@withContext null
            val songs = page.songs.map { song ->
                OnlineSong(
                    videoId         = song.id,
                    title           = song.title,
                    artist          = song.artists.firstOrNull()?.name ?: page.album.artists?.firstOrNull()?.name ?: "Unknown artist",
                    durationSeconds = song.duration,
                    thumbnailUrl    = song.thumbnail ?: page.album.thumbnail,
                    type            = "song"
                )
            }
            com.example.meduza.data.model.PlaylistDetail(
                id              = page.album.playlistId,
                title           = page.album.title,
                description     = "${page.album.year ?: ""} • ${page.album.artists?.joinToString { it.name } ?: "Album"}",
                authorName      = page.album.artists?.firstOrNull()?.name ?: "Album",
                authorAvatarUrl = null,
                thumbnailUrl    = page.album.thumbnail,
                trackCountText  = "${songs.size} songs",
                totalDurationText = null,
                isEditable      = false,
                tracks          = songs
            )
        } else {
            getPlaylistDetails(cleanId)
        }
    }
}
