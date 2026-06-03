# Meduza — Architecture

> **Made with ❤️ by Akyyra**

---

## Overview

Meduza is a single-module Android app (with a library module `innertube`) following MVVM + Repository pattern with Jetpack Compose UI.

---

## Module Structure

```
meduza/
├── app/                         Main application module
└── innertube/                   YouTube Music API client
                                  (forked from ArchiveTune, maintained by Akyyra)
```

---

## Data Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                           UI Layer (Jetpack Compose)                 │
│                                                                      │
│  MainActivity ──theme hue──▶ MeduzaTheme ──▶ LocalMeduzaColors      │
│       │                                                              │
│       ├──▶ MainViewModel ────▶ HomeScreen, SearchScreen, Library    │
│       └──▶ PlaybackViewModel ▶ BottomSheetPlayer                    │
└──────────────────────────────────┬──────────────────────────────────┘
                                   │ State (StateFlow)
┌──────────────────────────────────▼──────────────────────────────────┐
│                        ViewModel Layer                               │
│                                                                      │
│  MainViewModel                   PlaybackViewModel                  │
│  ├── loadHomeData()               ├── playItems()                   │
│  ├── searchOnline()               ├── toggleShuffleMode()           │
│  └── loadLocalSongs()             ├── autoQueueIntelligentTrack()   │
│                                   └── MeduzaIntelligenceEngine ◀── │
│                                        (pure Kotlin, testable)       │
└──────────────────────────────────┬──────────────────────────────────┘
                                   │ suspend functions
┌──────────────────────────────────▼──────────────────────────────────┐
│                       Repository Layer                               │
│                                                                      │
│  OnlineMediaRepository                LocalMediaRepository          │
│  ├── searchSongs()                    └── loadSongs()               │
│  ├── resolveStreamUrl()                   (MediaStore query)        │
│  ├── getRadioQueue()                                                 │
│  └── getHomeData()                                                  │
└──────────────────────────────────┬──────────────────────────────────┘
                                   │ HTTP (Ktor)
┌──────────────────────────────────▼──────────────────────────────────┐
│                      Innertube Module                                │
│                                                                      │
│  YouTube.kt (API client)                                             │
│  ├── search(), home(), next(), player()                              │
│  └── NewPipeUtils (JS deobfuscation for stream URLs)                 │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Theme System

```
User picks hue (0–360°)
        │
        ▼
MeduzaThemeEngine.deriveColors(hue)
        │
        ▼
MeduzaDynamicColors
├── accent, accentGlow, complement, triad
├── background, surface, surfaceHigh, border
├── textPrimary, textSecondary
├── neonGradient (Brush)
└── accentGradient (Brush)
        │
        ▼
CompositionLocalProvider(LocalMeduzaColors provides colors)
        │
        ▼
Any @Composable: LocalMeduzaColors.current.accent etc.
```

---

## Intelligence Engine

```
MeduzaIntelligenceEngine.scoreTrack(title, artist, mediaId, …)
│
├── Signal 1: Taste Affinity
│   artistPlayCounts[artist] / max → sqrt → ×2.0 bonus
│
├── Signal 2: Recency Penalty
│   mediaId in recentlyPlayedIds → ×0.2 (80% reduction)
│
├── Signal 3: Energy Arc
│   hourOfDay → preferredTags (e.g. CHILL, AMBIENT at night)
│   overlap with detectMoodTags(title, artist) → +0.5 per match
│
├── Signal 4: Diversity Window
│   artist in last-5-queue → exp(-appearances × 0.9) multiplier
│
└── Signal 5: Randomness Jitter
    +0–0.3 random noise (prevents deterministic ordering)

Final score → weighted random tournament selection → ordered queue
```

---

## Key Dependencies

| Dependency | Purpose |
|---|---|
| Jetpack Compose + Material 3 | UI framework |
| Media3 (ExoPlayer) + MediaSession | Audio playback + background service |
| Coil | Image loading (thumbnails) |
| Ktor | HTTP client (innertube) |
| Kotlinx Coroutines | Async + Flow |
| SettingsManager (custom) | SharedPreferences wrapper |

---

## State Management

All state flows **one direction**:

```
User action → ViewModel function → StateFlow update → Composable recomposition
```

No global mutable state. No direct SharedPreferences in Composables (always via SettingsManager through ViewModel).

---

<div align="center">

**Made with ❤️ by Akyyra**

</div>
