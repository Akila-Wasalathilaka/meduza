package com.example.meduza.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.meduza.MainViewModel
import com.example.meduza.data.model.LocalSong
import com.example.meduza.data.model.OnlineSong
import com.example.meduza.core.settings.SettingsManager
import com.example.meduza.playback.PlaybackViewModel
import com.example.meduza.ui.screens.home.HomeScreen
import com.example.meduza.ui.screens.library.LibraryScreen
import com.example.meduza.ui.screens.player.BottomSheetPlayer
import com.example.meduza.ui.screens.search.SearchScreen
import com.example.meduza.ui.screens.settings.SettingsDialog
import com.example.meduza.ui.theme.*
import com.example.meduza.ui.components.MeduzaBackground

/**
 * MeduzaApp — root Composable composing all screens, the player, and navigation.
 *
 * Theme state is hoisted here: `accentHue` is read from SettingsManager and
 * changes propagate to MeduzaTheme so the entire UI re-renders with new colors.
 *
 * Made with ❤️ by Akyyra — https://github.com/akyyra/meduza
 */
@Composable
fun MeduzaApp(
    mainViewModel     : MainViewModel,
    playbackViewModel : PlaybackViewModel,
    accentHue         : Float,
    onHueChange       : (Float) -> Unit,
) {
    val mc = LocalMeduzaColors.current

    val context         = LocalContext.current
    val localSongs      by mainViewModel.localSongs.collectAsState()
    val localLoading    by mainViewModel.localLoading.collectAsState()
    val onlineResults   by mainViewModel.onlineResults.collectAsState()
    val onlineLoading   by mainViewModel.onlineLoading.collectAsState()
    val onlineError     by mainViewModel.onlineError.collectAsState()
    val homeSections    by mainViewModel.homeSections.collectAsState()
    val homeLoading     by mainViewModel.homeLoading.collectAsState()
    val homeRefreshing  by mainViewModel.homeRefreshing.collectAsState()
    val homeChips       by mainViewModel.homeChips.collectAsState()
    val selectedChip    by mainViewModel.selectedChip.collectAsState()
    val isLoadingMore   by mainViewModel.isLoadingMore.collectAsState()
    val playbackState   by playbackViewModel.state.collectAsState()

    var selectedSection  by remember { mutableIntStateOf(0) }
    var query            by remember { mutableStateOf("") }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isSettingsOpen   by remember { mutableStateOf(false) }
    var toastMsg         by remember { mutableStateOf<String?>(null) }
    
    var detailItem       by remember { mutableStateOf<OnlineSong?>(null) }
    val detailSections   by mainViewModel.detailSections.collectAsState()
    val playlistDetail   by mainViewModel.playlistDetail.collectAsState()
    val detailLoading    by mainViewModel.detailLoading.collectAsState()

    val isKeyboardOpen = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

    val settingsManager = remember { SettingsManager.getInstance(context) }
    var audioQuality    by remember { mutableStateOf(settingsManager.audioQuality) }
    var lowNetworkMode  by remember { mutableStateOf(settingsManager.lowNetworkMode) }
    var cacheEnabled    by remember { mutableStateOf(settingsManager.cacheEnabled) }

    // ── Recently Played — reactive to playback changes ────────────────────────
    // Use a key derived from playbackState.mediaId so list refreshes after each play
    val recentlyPlayedJson    = remember(playbackState.mediaId) { settingsManager.recentlyPlayed }
    val recentlyPlayedSongs   = remember(recentlyPlayedJson) {
        val list = mutableListOf<OnlineSong>()
        try {
            val arr = org.json.JSONArray(recentlyPlayedJson)
            for (i in 0 until arr.length()) {
                val o   = arr.getJSONObject(i)
                val vid = o.getString("videoId")
                if (vid.length == 11) {
                    list.add(OnlineSong(
                        videoId         = vid,
                        title           = o.getString("title"),
                        artist          = o.getString("artist"),
                        durationSeconds = if (o.has("durationSeconds")) o.getInt("durationSeconds") else null,
                        thumbnailUrl    = if (o.has("thumbnailUrl") && o.getString("thumbnailUrl").isNotBlank()) o.getString("thumbnailUrl") else thumbUrl(vid),
                        type            = o.optString("type", "song")
                    ))
                }
            }
        } catch (_: Exception) {}
        list
    }

    // Load home data once on launch
    LaunchedEffect(Unit) {
        mainViewModel.loadHomeData()
    }

    // ── Storage permission ────────────────────────────────────────────────────
    val permission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) mainViewModel.loadLocalSongs()
    }
    LaunchedEffect(hasPermission) { if (hasPermission) mainViewModel.loadLocalSongs() }

    // Toast auto-dismiss
    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            kotlinx.coroutines.delay(3000)
            toastMsg = null
        }
    }

    BackHandler(enabled = isSettingsOpen || isPlayerExpanded || detailItem != null || selectedSection != 0) {
        when {
            isSettingsOpen       -> isSettingsOpen = false
            isPlayerExpanded     -> isPlayerExpanded = false
            detailItem != null   -> { detailItem = null; mainViewModel.clearDetails() }
            selectedSection != 0 -> selectedSection = 0
        }
    }

    MeduzaBackground {
        Scaffold(containerColor = Color.Transparent) { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    when (selectedSection) {
                        0 -> {
                            if (detailItem != null) {
                                if (detailItem?.type == "playlist") {
                                    com.example.meduza.ui.screens.home.PlaylistDetailScreen(
                                        title            = detailItem!!.title,
                                        playlistDetail   = playlistDetail,
                                        fallbackSections = detailSections,
                                        isLoading        = detailLoading,
                                        playbackState    = playbackState,
                                        onBack           = { detailItem = null; mainViewModel.clearDetails() },
                                        onPlaySong       = { song, allSongs, idx ->
                                            if (playbackState.mediaId == song.videoId) {
                                                playbackViewModel.togglePlayPause()
                                            } else {
                                                val items = allSongs.map { it.toMediaItem() }
                                                playbackViewModel.playItems(
                                                    items       = items,
                                                    startIndex  = idx,
                                                    contextName = detailItem?.title ?: "Playlist",
                                                )
                                                saveRecentlyPlayed(context, song)
                                            }
                                        },
                                        onPlayAll        = { allSongs, shuffle ->
                                            if (allSongs.isNotEmpty()) {
                                                val items = if (shuffle) allSongs.shuffled().map { it.toMediaItem() } else allSongs.map { it.toMediaItem() }
                                                playbackViewModel.playItems(
                                                    items       = items,
                                                    startIndex  = 0,
                                                    contextName = detailItem?.title ?: "Playlist",
                                                )
                                                saveRecentlyPlayed(context, allSongs.first())
                                            }
                                        }
                                    )
                                } else {
                                    com.example.meduza.ui.screens.home.ContextScreen(
                                        title         = detailItem!!.title,
                                        sections      = detailSections,
                                        isLoading     = detailLoading,
                                        playbackState = playbackState,
                                        onBack        = { detailItem = null; mainViewModel.clearDetails() },
                                        onPlaySong    = { song, allSongs, idx ->
                                             if (playbackState.mediaId == song.videoId) {
                                                 playbackViewModel.togglePlayPause()
                                             } else {
                                                 val items = allSongs.map { it.toMediaItem() }
                                                 playbackViewModel.playItems(
                                                     items       = items,
                                                     startIndex  = idx,
                                                     contextName = detailItem?.title ?: "Context",
                                                 )
                                                 saveRecentlyPlayed(context, song)
                                             }
                                         }
                                    )
                                }
                            } else {
                                HomeScreen(
                                    playbackState  = playbackState,
                                    recentSongs    = recentlyPlayedSongs,
                                    homeSections   = homeSections,
                                    homeChips      = homeChips,
                                    selectedChip   = selectedChip,
                                    onChipSelected = { mainViewModel.toggleChip(it) },
                                    homeLoading    = homeLoading,
                                    isRefreshing   = homeRefreshing,
                                    isLoadingMore  = isLoadingMore,
                                    onRefresh      = { mainViewModel.refreshHomeData() },
                                    onLoadMore     = { mainViewModel.loadMoreHomeSections() },
                                    onPlay         = { items, idx -> playbackViewModel.playItems(items, idx) },
                                    onPlaySong     = { song, allSongs, idx ->
                                        if (song.type == "artist") {
                                            detailItem = song
                                            mainViewModel.loadArtistDetails(song.videoId)
                                        } else if (song.type == "playlist") {
                                            detailItem = song
                                            if (song.videoId.startsWith("MPREb_")) {
                                                mainViewModel.loadAlbumDetails(song.videoId)
                                            } else {
                                                mainViewModel.loadPlaylistDetails(song.videoId)
                                            }
                                        } else if (playbackState.mediaId == song.videoId) {
                                            playbackViewModel.togglePlayPause()
                                        } else {
                                            playbackViewModel.playSongWithRadio(
                                                song        = song.toMediaItem(),
                                                contextName = "Home",
                                            )
                                            saveRecentlyPlayed(context, song)
                                        }
                                    },
                                    onSettingsClick = { isSettingsOpen = true },
                                )
                            }
                        }
                        1 -> SearchScreen(
                            query         = query,
                            onQueryChange = { q ->
                                query = q
                                if (q.isNotBlank()) mainViewModel.searchOnline(q)
                                else mainViewModel.clearOnlineResults()
                            },
                            isLoading     = onlineLoading,
                            errorMessage  = onlineError,
                            results       = onlineResults,
                            playbackState = playbackState,
                            onPlay        = { song ->
                                if (song.type == "artist") {
                                    selectedSection = 0
                                    detailItem = song
                                    mainViewModel.loadArtistDetails(song.videoId)
                                } else if (song.type == "playlist") {
                                    selectedSection = 0
                                    detailItem = song
                                    if (song.videoId.startsWith("MPREb_")) {
                                        mainViewModel.loadAlbumDetails(song.videoId)
                                    } else {
                                        mainViewModel.loadPlaylistDetails(song.videoId)
                                    }
                                } else {
                                    playbackViewModel.playSongWithRadio(
                                        song        = song.toMediaItem(),
                                        contextName = "Search: $query",
                                    )
                                    saveRecentlyPlayed(context, song)
                                }
                            },
                            onCategorySelect = { q ->
                                query = q
                                mainViewModel.searchOnline(q)
                            }
                        )
                        2 -> LibraryScreen(
                            songs               = localSongs,
                            isLoading           = localLoading,
                            hasPermission       = hasPermission,
                            playbackState       = playbackState,
                            onRequestPermission = { permissionLauncher.launch(permission) },
                            onPlay              = { idx ->
                                val items = localSongs.map { it.toMediaItem() }
                                playbackViewModel.playItems(
                                    items       = items,
                                    startIndex  = idx,
                                    contextName = "Library",
                                )
                            },
                        )
                    }
                }

                // ── Bottom Overlays (Player + Navbar) ─────────────────────────
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()
                ) {
                    if (playbackState.title.isNotBlank() && playbackState.title != "Nothing playing") {
                        BottomSheetPlayer(
                            state           = playbackState,
                            isExpanded      = isPlayerExpanded,
                            onToggleExpand  = { isPlayerExpanded = !isPlayerExpanded },
                            onTogglePlay    = { playbackViewModel.togglePlayPause() },
                            onNext          = { playbackViewModel.next() },
                            onPrevious      = { playbackViewModel.previous() },
                            onSeek          = { playbackViewModel.seekTo(it) },
                            onToggleShuffle = { playbackViewModel.toggleShuffleMode() },
                            onToggleRepeat  = { playbackViewModel.toggleRepeatMode() },
                            modifier        = Modifier.fillMaxWidth(),
                        )
                    }

                    if (!isPlayerExpanded && !isKeyboardOpen) {
                        MeduzaNavBar(selected = selectedSection) { idx ->
                            if (selectedSection == idx && idx == 0) {
                                detailItem = null
                                mainViewModel.clearDetails()
                            } else if (idx != 0) {
                                detailItem = null
                                mainViewModel.clearDetails()
                            }
                            selectedSection  = idx
                            isPlayerExpanded = false
                        }
                    }
                }

                // ── Toast ─────────────────────────────────────────────────────
                if (toastMsg != null) {
                    Snackbar(
                        modifier       = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        containerColor = mc.surfaceHigh,
                        contentColor   = mc.textPrimary,
                    ) { Text(toastMsg ?: "", fontSize = 13.sp) }
                }

                // ── Settings Dialog ───────────────────────────────────────────
                if (isSettingsOpen) {
                    SettingsDialog(
                        audioQuality    = audioQuality,
                        lowNetworkMode  = lowNetworkMode,
                        cacheEnabled    = cacheEnabled,
                        accentHue       = accentHue,
                        onQualityChange = { q ->
                            audioQuality = q
                            settingsManager.audioQuality = q
                            toastMsg = "Audio quality set to ${q.replaceFirstChar { it.uppercase() }} (takes effect on next song)"
                        },
                        onLowNetChange  = {
                            lowNetworkMode = it
                            settingsManager.lowNetworkMode = it
                            toastMsg = if (it) "Low Data Mode enabled" else "Low Data Mode disabled"
                        },
                        onCacheChange   = {
                            cacheEnabled = it
                            settingsManager.cacheEnabled = it
                            toastMsg = if (it) "Playback caching enabled" else "Playback caching disabled"
                        },
                        onHueChange     = { hue ->
                            onHueChange(hue)
                            settingsManager.accentHue = hue
                        },
                        onDismiss = { isSettingsOpen = false },
                    )
                }
            }
        }
    }
}

// ── Nav Bar ────────────────────────────────────────────────────────────────────
@Composable
private fun MeduzaNavBar(selected: Int, onSelect: (Int) -> Unit) {
    val mc = LocalMeduzaColors.current
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(mc.surface.copy(alpha = 0.85f))
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(32.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            listOf(
                Triple(Icons.Default.Home,         "Home",    0),
                Triple(Icons.Default.Search,       "Search",  1),
                Triple(Icons.Default.LibraryMusic, "Library", 2),
            ).forEach { (icon, label, idx) ->
                val isSelected   = selected == idx
                val bgColor      = if (isSelected) mc.accent.copy(alpha = 0.15f) else Color.Transparent
                val contentColor = if (isSelected) mc.accent else mc.textSecondary

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onSelect(idx) }
                        .background(bgColor)
                        .padding(
                            horizontal = if (isSelected) 16.dp else 12.dp,
                            vertical   = 10.dp,
                        ),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector        = icon,
                        contentDescription = label,
                        tint               = contentColor,
                        modifier           = Modifier.size(24.dp),
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text       = label,
                            color      = contentColor,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

// ── MediaItem Extensions ───────────────────────────────────────────────────────

private fun LocalSong.toMediaItem(): MediaItem {
    val meta = MediaMetadata.Builder()
        .setTitle(title).setArtist(artist).setAlbumTitle(album).build()
    return MediaItem.Builder()
        .setMediaId(id.toString()).setUri(uri).setMediaMetadata(meta).build()
}

private fun OnlineSong.toMediaItem(): MediaItem {
    val meta = MediaMetadata.Builder()
        .setTitle(title).setArtist(artist)
        .setArtworkUri(android.net.Uri.parse(thumbnailUrl ?: thumbUrl(videoId))).build()
    return MediaItem.Builder()
        .setMediaId(videoId)
        .setUri("youtube://$videoId")
        .setMediaMetadata(meta).build()
}

// ── Utilities ──────────────────────────────────────────────────────────────────

private fun saveRecentlyPlayed(context: android.content.Context, song: OnlineSong) {
    if (song.videoId.length != 11) return
    try {
        val mgr  = SettingsManager.getInstance(context)
        val arr  = org.json.JSONArray(mgr.recentlyPlayed)
        val list = mutableListOf<org.json.JSONObject>()
        for (i in 0 until arr.length()) {
            val o   = arr.getJSONObject(i)
            val vid = o.optString("videoId")
            if (vid.length == 11 && vid != song.videoId) list.add(o)
        }
        list.add(0, org.json.JSONObject().apply {
            put("videoId", song.videoId)
            put("title",   song.title)
            put("artist",  song.artist)
            put("thumbnailUrl", song.thumbnailUrl ?: thumbUrl(song.videoId))
            song.durationSeconds?.let { put("durationSeconds", it) }
        })
        val out = org.json.JSONArray()
        list.take(12).forEach { out.put(it) }
        mgr.recentlyPlayed = out.toString()
    } catch (_: Exception) {}
}
