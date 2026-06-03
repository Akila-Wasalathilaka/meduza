package com.example.meduza.core.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * SettingsManager — single source of truth for all user preferences.
 *
 * Persists settings to SharedPreferences under the key "meduza_settings".
 * All properties use Kotlin property delegates for clean get/set syntax.
 *
 * Made with ❤️ by Akyyra — https://github.com/akyyra/meduza
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("meduza_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_AUDIO_QUALITY        = "audio_quality"
        private const val KEY_LOW_NETWORK_MODE     = "low_network_mode"
        private const val KEY_CACHE_ENABLED        = "cache_enabled"
        private const val KEY_ACCENT_HUE           = "accent_hue"
        private const val KEY_RECENTLY_PLAYED_IDS  = "recently_played_ids"

        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SettingsManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    // ── Audio ──────────────────────────────────────────────────────────────────

    /** Audio quality preference: "low", "medium", or "high". Default: "medium". */
    var audioQuality: String
        get() = prefs.getString(KEY_AUDIO_QUALITY, "medium") ?: "medium"
        set(value) = prefs.edit().putString(KEY_AUDIO_QUALITY, value).apply()

    /** When true, prefers low-bitrate streams for reduced data usage. */
    var lowNetworkMode: Boolean
        get() = prefs.getBoolean(KEY_LOW_NETWORK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_LOW_NETWORK_MODE, value).apply()

    /** When true, stream URLs are cached in memory for faster re-play. */
    var cacheEnabled: Boolean
        get() = prefs.getBoolean(KEY_CACHE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CACHE_ENABLED, value).apply()

    // ── Theme ──────────────────────────────────────────────────────────────────

    /**
     * The accent hue (0–360°) for the dynamic theme engine.
     * The entire color system is derived from this single value via HSL harmonics.
     *
     * Default: 330° (Meduza pink/magenta — the original brand color).
     */
    var accentHue: Float
        get() = prefs.getFloat(KEY_ACCENT_HUE, 330f)
        set(value) = prefs.edit().putFloat(KEY_ACCENT_HUE, value.coerceIn(0f, 360f)).apply()

    // ── History & Intelligence ─────────────────────────────────────────────────

    /** Recently played song objects as JSON array (for UI display). */
    var recentlyPlayed: String
        get() = prefs.getString("recently_played", "[]") ?: "[]"
        set(value) = prefs.edit().putString("recently_played", value).apply()

    /** Recently played mediaIds as JSON array (for recency penalty in Intelligence Engine). */
    var recentlyPlayedIds: String
        get() = prefs.getString(KEY_RECENTLY_PLAYED_IDS, "[]") ?: "[]"
        set(value) = prefs.edit().putString(KEY_RECENTLY_PLAYED_IDS, value).apply()

    /** Cached home sections as JSON (stale-while-revalidate pattern). */
    var cachedHomeSections: String
        get() = prefs.getString("cached_home_sections", "[]") ?: "[]"
        set(value) = prefs.edit().putString("cached_home_sections", value).apply()

    /**
     * Persisted taste data: top-50 artists by play count as JSON array.
     * Used by MeduzaIntelligenceEngine for taste affinity scoring.
     */
    var savedArtistPlayCounts: String
        get() = prefs.getString("artist_play_counts", "[]") ?: "[]"
        set(value) = prefs.edit().putString("artist_play_counts", value).apply()

    /** Timestamp of the last successful home page network fetch. */
    var lastHomeRequestTime: Long
        get() = prefs.getLong("last_home_request_time", 0L)
        set(value) = prefs.edit().putLong("last_home_request_time", value).apply()

    /** Cached home chips as JSON. */
    var cachedHomeChips: String
        get() = prefs.getString("cached_home_chips", "[]") ?: "[]"
        set(value) = prefs.edit().putString("cached_home_chips", value).apply()
}
