package com.example.meduza

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.meduza.data.model.HomeChip
import com.example.meduza.data.model.HomePageResult
import com.example.meduza.data.model.HomeSection
import com.example.meduza.data.repository.LocalMediaRepository
import com.example.meduza.data.model.LocalSong
import com.example.meduza.data.repository.OnlineMediaRepository
import com.example.meduza.data.model.OnlineSong
import com.example.meduza.core.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val localRepository  = LocalMediaRepository()
    private val onlineRepository = OnlineMediaRepository.getInstance()
    private val settingsManager  = SettingsManager.getInstance(application)

    // ── Local ──────────────────────────────────────────────────────────────────
    private val _localSongs    = MutableStateFlow<List<LocalSong>>(emptyList())
    private val _localLoading  = MutableStateFlow(false)
    val localSongs:  StateFlow<List<LocalSong>> = _localSongs.asStateFlow()
    val localLoading: StateFlow<Boolean>         = _localLoading.asStateFlow()

    // ── Search ─────────────────────────────────────────────────────────────────
    private val _onlineResults = MutableStateFlow<List<OnlineSong>>(emptyList())
    private val _onlineLoading = MutableStateFlow(false)
    private val _onlineError   = MutableStateFlow<String?>(null)
    val onlineResults: StateFlow<List<OnlineSong>> = _onlineResults.asStateFlow()
    val onlineLoading: StateFlow<Boolean>           = _onlineLoading.asStateFlow()
    val onlineError:   StateFlow<String?>           = _onlineError.asStateFlow()

    // ── Home ───────────────────────────────────────────────────────────────────
    private val _homeSections    = MutableStateFlow<List<HomeSection>>(emptyList())
    private val _homeLoading     = MutableStateFlow(
        settingsManager.cachedHomeSections.isBlank() || settingsManager.cachedHomeSections == "[]"
    )
    private val _homeRefreshing  = MutableStateFlow(false)
    private val _homeChips       = MutableStateFlow<List<HomeChip>>(emptyList())
    private val _selectedChip    = MutableStateFlow<HomeChip?>(null)
    private val _homeContinuation = MutableStateFlow<String?>(null)
    private val _isLoadingMore   = MutableStateFlow(false)

    // Snapshot of home page before a chip was selected (to restore on back)
    private var previousSections: List<HomeSection> = emptyList()
    private var previousContinuation: String? = null

    val homeSections:     StateFlow<List<HomeSection>> = _homeSections.asStateFlow()
    val homeLoading:      StateFlow<Boolean>           = _homeLoading.asStateFlow()
    val homeRefreshing:   StateFlow<Boolean>           = _homeRefreshing.asStateFlow()
    val homeChips:        StateFlow<List<HomeChip>>    = _homeChips.asStateFlow()
    val selectedChip:     StateFlow<HomeChip?>         = _selectedChip.asStateFlow()
    val homeContinuation: StateFlow<String?>           = _homeContinuation.asStateFlow()
    val isLoadingMore:    StateFlow<Boolean>           = _isLoadingMore.asStateFlow()

    // Context Details (Artist/Playlist)
    private val _detailSections = MutableStateFlow<List<HomeSection>>(emptyList())
    val detailSections: StateFlow<List<HomeSection>> = _detailSections.asStateFlow()

    private val _playlistDetail = MutableStateFlow<com.example.meduza.data.model.PlaylistDetail?>(null)
    val playlistDetail: StateFlow<com.example.meduza.data.model.PlaylistDetail?> = _playlistDetail.asStateFlow()

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    fun loadArtistDetails(browseId: String) {
        viewModelScope.launch {
            _detailLoading.value = true
            _detailSections.value = emptyList()
            _playlistDetail.value = null
            try {
                _detailSections.value = onlineRepository.getArtistData(browseId)
            } finally {
                _detailLoading.value = false
            }
        }
    }

    fun loadPlaylistDetails(playlistId: String) {
        viewModelScope.launch {
            _detailLoading.value = true
            _detailSections.value = emptyList()
            _playlistDetail.value = null
            try {
                val detail = onlineRepository.getPlaylistDetails(playlistId)
                _playlistDetail.value = detail
                if (detail != null && detail.tracks.isNotEmpty()) {
                    _detailSections.value = listOf(HomeSection(title = detail.title, songs = detail.tracks))
                }
            } finally {
                _detailLoading.value = false
            }
        }
    }

    fun loadAlbumDetails(browseIdOrPlaylistId: String) {
        viewModelScope.launch {
            _detailLoading.value = true
            _detailSections.value = emptyList()
            _playlistDetail.value = null
            try {
                val detail = onlineRepository.getAlbumDetails(browseIdOrPlaylistId)
                _playlistDetail.value = detail
                if (detail != null && detail.tracks.isNotEmpty()) {
                    _detailSections.value = listOf(HomeSection(title = detail.title, songs = detail.tracks))
                }
            } finally {
                _detailLoading.value = false
            }
        }
    }

    fun clearDetails() {
        _detailSections.value = emptyList()
        _playlistDetail.value = null
    }

    // ── Favourite video IDs (used by loadHomeData for personalized mixes) ──────
    private var _favVideoIds: List<String> = emptyList()

    init {
        // Stale-While-Revalidate: show cache instantly, refresh in background via LaunchedEffect
        _homeSections.value = jsonToSections(settingsManager.cachedHomeSections)
        _homeChips.value = jsonToChips(settingsManager.cachedHomeChips)
    }

    // ── Home Data ──────────────────────────────────────────────────────────────
    /**
     * Main home load — mirrors ArchiveTune's load().
     * Parallel: personalised mixes (from listen history) + YTM home API.
     */
    fun loadHomeData() {
        if (_homeLoading.value && _homeSections.value.isNotEmpty()) return
        val cache = _homeSections.value
        val lastLoad = settingsManager.lastHomeRequestTime
        val now = System.currentTimeMillis()
        val hasSongSection = cache.any { it.songs.firstOrNull()?.type == "song" }
        if (cache.isNotEmpty() && hasSongSection && (now - lastLoad) < 5 * 60 * 1000L) {
            _homeChips.value = jsonToChips(settingsManager.cachedHomeChips)
            return
        }
        viewModelScope.launch {
            _homeLoading.value = true

            val ytResult = runCatching { onlineRepository.getHomeData() }
                .getOrDefault(HomePageResult(emptyList(), emptyList(), null))

            val networkSections = ytResult.sections
            val allChips = ytResult.chips

            if (networkSections.isNotEmpty()) {
                _homeContinuation.value = ytResult.continuation
                _homeSections.value = networkSections
                if (allChips.isNotEmpty()) {
                    _homeChips.value = allChips
                    settingsManager.cachedHomeChips = chipsToJson(allChips)
                }
                settingsManager.cachedHomeSections = sectionsToJson(networkSections)
                settingsManager.lastHomeRequestTime = System.currentTimeMillis()
            } else {
                // Network returned empty. Only load fallbacks if the local cache is also empty.
                if (_homeSections.value.isEmpty()) {
                    val fallbacks = listOf("Top Hits", "Trending Music", "Viral 50", "New Releases")
                    val fallbackDefs = fallbacks.map { term ->
                        async { runCatching { onlineRepository.searchSongs(term) }.getOrDefault(emptyList()) }
                    }
                    val fallbackSections = mutableListOf<HomeSection>()
                    fallbacks.zip(fallbackDefs).forEach { (term, def) ->
                        val songs = def.await()
                        if (songs.isNotEmpty()) fallbackSections.add(HomeSection(title = term, songs = songs))
                    }
                    if (fallbackSections.isNotEmpty()) {
                        _homeSections.value = fallbackSections
                        settingsManager.cachedHomeSections = sectionsToJson(fallbackSections)
                    }
                }
            }
            _homeLoading.value = false
            
            // Aggressive Pre-Fetching: instantly pre-load next rows like Spotify to ensure 10+ rows
            if (networkSections.isNotEmpty() && _homeContinuation.value != null) {
                loadMoreHomeSections()
            }
        }
    }

    fun refreshHomeData() {
        if (_homeRefreshing.value) return
        viewModelScope.launch {
            _homeRefreshing.value = true
            _homeLoading.value = true
            _selectedChip.value = null // Reset chip selection on refresh to return to main feed

            val ytResult = runCatching { onlineRepository.getHomeData() }
                .getOrDefault(HomePageResult(emptyList(), emptyList(), null))

            val networkSections = ytResult.sections
            val allChips = ytResult.chips

            if (networkSections.isNotEmpty()) {
                _homeContinuation.value = ytResult.continuation
                _homeSections.value = networkSections
                if (allChips.isNotEmpty()) {
                    _homeChips.value = allChips
                    settingsManager.cachedHomeChips = chipsToJson(allChips)
                }
                settingsManager.cachedHomeSections = sectionsToJson(networkSections)
                settingsManager.lastHomeRequestTime = System.currentTimeMillis()
            } else {
                // Network returned empty. Only load fallbacks if the local cache is also empty.
                if (_homeSections.value.isEmpty()) {
                    val fallbacks = listOf("Top Hits", "Trending Music", "Viral 50", "New Releases")
                    val fallbackDefs = fallbacks.map { term ->
                        async { runCatching { onlineRepository.searchSongs(term) }.getOrDefault(emptyList()) }
                    }
                    val fallbackSections = mutableListOf<HomeSection>()
                    fallbacks.zip(fallbackDefs).forEach { (term, def) ->
                        val songs = def.await()
                        if (songs.isNotEmpty()) fallbackSections.add(HomeSection(title = term, songs = songs))
                    }
                    if (fallbackSections.isNotEmpty()) {
                        _homeSections.value = fallbackSections
                        settingsManager.cachedHomeSections = sectionsToJson(fallbackSections)
                    }
                }
            }
            _homeLoading.value = false
            _homeRefreshing.value = false
            
            // Aggressive Pre-Fetching: instantly pre-load next rows like Spotify to ensure 10+ rows
            if (networkSections.isNotEmpty() && _homeContinuation.value != null) {
                loadMoreHomeSections()
            }
        }
    }

    /**
     * Infinite scroll — loads next page using continuation token.
     * Mirrors ArchiveTune's loadMoreYouTubeItems().
     */
    fun loadMoreHomeSections() {
        val continuation = _homeContinuation.value ?: return
        if (_isLoadingMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            val result = runCatching {
                onlineRepository.loadMoreHomeSections(continuation)
            }.getOrDefault(HomePageResult(emptyList(), emptyList(), null))

            if (result.sections.isNotEmpty()) {
                _homeSections.value = _homeSections.value + result.sections
                settingsManager.cachedHomeSections = sectionsToJson(_homeSections.value)
            }
            _homeContinuation.value = result.continuation
            _isLoadingMore.value = false
        }
    }

    /**
     * Toggle a chip filter — mirrors ArchiveTune's toggleChip().
     * Selecting: saves current home → fetches chip-filtered page.
     * Deselecting (same chip or null): restores saved home.
     */
    fun toggleChip(chip: HomeChip?) {
        if (chip == null || chip == _selectedChip.value) {
            // Deselect — restore previous home page
            _homeSections.value = previousSections
            _homeContinuation.value = previousContinuation
            _selectedChip.value = null
            return
        }
        // Save current state before fetching chip page
        if (_selectedChip.value == null) {
            previousSections = _homeSections.value
            previousContinuation = _homeContinuation.value
        }
        viewModelScope.launch {
            _homeLoading.value = true
            val result = runCatching {
                onlineRepository.getHomeDataForChip(chip)
            }.getOrDefault(HomePageResult(emptyList(), emptyList(), null))

            if (result.sections.isNotEmpty()) {
                _homeSections.value = result.sections
            }
            _homeContinuation.value = result.continuation
            _selectedChip.value = chip
            _homeLoading.value = false
        }
    }

    fun loadDiscoveredSongs(favArtist: String) {
        // No-op: kept for backward compat. Central loadHomeData() handles discovery.
    }

    // ── Local & Search ─────────────────────────────────────────────────────────
    fun loadLocalSongs() {
        viewModelScope.launch {
            _localLoading.value = true
            _localSongs.value   = localRepository.loadSongs(getApplication())
            _localLoading.value = false
        }
    }

    fun searchOnline(query: String) {
        viewModelScope.launch {
            _onlineLoading.value = true
            _onlineError.value   = null
            val results = runCatching { onlineRepository.searchSongs(query) }
                .onFailure { _onlineError.value = it.message ?: "Search failed" }
                .getOrDefault(emptyList())
            _onlineResults.value = results
            _onlineLoading.value = false
        }
    }

    fun clearOnlineResults() {
        _onlineResults.update { emptyList() }
        _onlineError.value = null
    }

    suspend fun resolveOnlineStreamUrl(videoId: String): Result<String> {
        val settings = SettingsManager.getInstance(getApplication())
        return onlineRepository.resolveStreamUrl(videoId, settings.audioQuality, settings.lowNetworkMode)
    }

    // ── JSON helpers ───────────────────────────────────────────────────────────
    private fun sectionsToJson(sections: List<HomeSection>): String {
        val array = org.json.JSONArray()
        for (section in sections) {
            val sectionObj = org.json.JSONObject()
            sectionObj.put("title", section.title)
            val songsArray = org.json.JSONArray()
            for (song in section.songs) {
                val obj = org.json.JSONObject().apply {
                    put("videoId", song.videoId)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("durationSeconds", song.durationSeconds ?: -1)
                    put("thumbnailUrl", song.thumbnailUrl ?: "")
                    put("type", song.type)
                }
                songsArray.put(obj)
            }
            sectionObj.put("songs", songsArray)
            array.put(sectionObj)
        }
        return array.toString()
    }

    private fun jsonToSections(json: String): List<HomeSection> {
        if (json.isBlank() || json == "[]") return emptyList()
        val list = mutableListOf<HomeSection>()
        try {
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val sectionObj = array.getJSONObject(i)
                val title = sectionObj.getString("title")
                val songsArray = sectionObj.getJSONArray("songs")
                val songs = mutableListOf<OnlineSong>()
                for (j in 0 until songsArray.length()) {
                    val obj = songsArray.getJSONObject(j)
                    val duration = obj.optInt("durationSeconds", -1)
                    songs.add(OnlineSong(
                        videoId         = obj.getString("videoId"),
                        title           = obj.getString("title"),
                        artist          = obj.getString("artist"),
                        durationSeconds = if (duration != -1) duration else null,
                        thumbnailUrl    = obj.optString("thumbnailUrl", "").takeIf { it.isNotEmpty() },
                        type            = obj.optString("type", "song")
                    ))
                }
                list.add(HomeSection(title, songs))
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Error parsing cached sections", e)
        }
        return list
    }

    private fun chipsToJson(chips: List<HomeChip>): String {
        val array = org.json.JSONArray()
        for (chip in chips) {
            val obj = org.json.JSONObject().apply {
                put("title", chip.title)
                put("params", chip.params)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun jsonToChips(json: String): List<HomeChip> {
        if (json.isBlank() || json == "[]") return emptyList()
        val list = mutableListOf<HomeChip>()
        try {
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(HomeChip(
                    title = obj.getString("title"),
                    params = obj.getString("params")
                ))
            }
        } catch (_: Exception) {}
        return list
    }
}
