package com.example.meduza

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.meduza.core.settings.SettingsManager
import com.example.meduza.playback.PlaybackViewModel
import com.example.meduza.ui.MeduzaApp
import com.example.meduza.ui.theme.MeduzaTheme

/**
 * MainActivity — entry point for Meduza.
 *
 * Theme state ([accentHue]) is hoisted here so it persists across
 * configuration changes and SettingsDialog re-opens without re-reading
 * SharedPreferences on every recomposition.
 *
 * Made with ❤️ by Akyyra — https://github.com/akyyra/meduza
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // High-efficiency Coil ImageLoader — thumbnail caching for fast image loads
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .respectCacheHeaders(false)   // YouTube images have strict headers — force our cache
            .build()
        Coil.setImageLoader(imageLoader)

        enableEdgeToEdge()

        setContent {
            // Read accent hue from SettingsManager — persisted across sessions
            val settingsManager = remember { SettingsManager.getInstance(this) }
            var accentHue by remember { mutableFloatStateOf(settingsManager.accentHue) }

            MeduzaTheme(accentHue = accentHue) {
                val mainViewModel: MainViewModel = viewModel()
                val playbackViewModel: PlaybackViewModel = viewModel()
                MeduzaApp(
                    mainViewModel      = mainViewModel,
                    playbackViewModel  = playbackViewModel,
                    accentHue          = accentHue,
                    onHueChange        = { hue ->
                        accentHue = hue
                        settingsManager.accentHue = hue
                    },
                )
            }
        }
    }
}