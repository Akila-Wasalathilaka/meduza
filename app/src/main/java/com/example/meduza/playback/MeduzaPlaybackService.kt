package com.example.meduza.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.meduza.core.settings.SettingsManager
import java.io.File

@OptIn(UnstableApi::class)
object DownloadCache {
    private var cache: SimpleCache? = null

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (cache == null) {
            val cacheDir = File(context.cacheDir, "media_playback_cache")
            val limit = com.example.meduza.core.utils.DevicePerformanceManager.getAdaptiveCacheLimit(context)
            val evictor = LeastRecentlyUsedCacheEvictor(limit)
            val databaseProvider = StandaloneDatabaseProvider(context)
            cache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return cache!!
    }
}

@OptIn(UnstableApi::class)
class DynamicDataSourceFactory(
    private val context: Context,
    private val standardDataSourceFactory: DataSource.Factory
) : DataSource.Factory {
    private val cacheDataSourceFactory by lazy {
        val cache = DownloadCache.getCache(context)
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(standardDataSourceFactory)
    }

    override fun createDataSource(): DataSource {
        val cacheEnabled = SettingsManager.getInstance(context).cacheEnabled
        return if (cacheEnabled) {
            cacheDataSourceFactory.createDataSource()
        } else {
            standardDataSourceFactory.createDataSource()
        }
    }
}

class MeduzaPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        // YouTube stream URLs require proper headers to avoid 403 Forbidden.
        // Without these headers, Google CDN rejects the stream request.
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("com.google.android.youtube/1.9 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002) gzip")
            .setDefaultRequestProperties(
                mapOf(
                    "Origin" to "https://www.youtube.com",
                    "Referer" to "https://www.youtube.com/",
                    "X-YouTube-Client-Name" to "30",
                    "X-YouTube-Client-Version" to "1.9",
                )
            )
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
                val standardDataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
            
        val dynamicDataSourceFactory = DynamicDataSourceFactory(
            context = this,
            standardDataSourceFactory = standardDataSourceFactory
        )
        
        val resolvingDataSourceFactory = androidx.media3.datasource.ResolvingDataSource.Factory(dynamicDataSourceFactory) { dataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme == "youtube") {
                var videoId = uri.host ?: uri.schemeSpecificPart ?: ""
                if (videoId.startsWith("//")) {
                    videoId = videoId.substring(2)
                }
                if (videoId.isBlank()) return@Factory dataSpec
                try {
                    // Hard 8-second timeout prevents ANR — if resolution hangs, ExoPlayer
                    // gets an IOException and can skip/retry instead of freezing the app.
                    val resolvedUrl = kotlinx.coroutines.runBlocking {
                        kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val settings = com.example.meduza.core.settings.SettingsManager.getInstance(this@MeduzaPlaybackService)
                                com.example.meduza.data.repository.OnlineMediaRepository.getInstance().resolveStreamUrl(
                                    videoId        = videoId,
                                    quality        = settings.audioQuality,
                                    lowNetworkMode = settings.lowNetworkMode,
                                ).getOrThrow()
                            }
                        }
                    } ?: throw java.io.IOException("Stream resolution timed out for $videoId")
                    dataSpec.buildUpon().setUri(android.net.Uri.parse(resolvedUrl)).build()
                } catch (e: Exception) {
                    android.util.Log.e("MeduzaPlaybackService", "Failed to resolve stream for $videoId: ${e.message}")
                    throw java.io.IOException("Stream unavailable: ${e.message}", e)
                }
            } else {
                dataSpec
            }
        }

        
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(resolvingDataSourceFactory)

        val isLowEnd = com.example.meduza.core.utils.DevicePerformanceManager.isLowEndDevice(this)
        val maxBuffer = if (isLowEnd) 15_000 else 50_000
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs               */ 1_500,
                /* maxBufferMs               */ maxBuffer,
                /* bufferForPlaybackMs       */ 500,
                /* bufferForPlaybackAfterRebufferMs */ 1_000,
            )
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            
        mediaSession = MediaSession.Builder(this, player!!).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.playbackState == androidx.media3.common.Player.STATE_IDLE || player.playbackState == androidx.media3.common.Player.STATE_ENDED) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.release()
        player?.release()
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
