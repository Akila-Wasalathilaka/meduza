# Contributing to Meduza

> **Made with ❤️ by Akyyra**
> We welcome every contributor! Whether you're fixing a bug, adding a feature, or improving the documentation — thank you.

---

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [Project Structure](#project-structure)
3. [Development Setup](#development-setup)
4. [Architecture Principles](#architecture-principles)
5. [How to Add a Feature](#how-to-add-a-feature)
6. [Contributing to the Intelligence Engine](#contributing-to-the-intelligence-engine)
7. [Code Style](#code-style)
8. [Pull Request Checklist](#pull-request-checklist)

---

## Code of Conduct

Be kind. Be constructive. We're all here to build something amazing together.

---

## Project Structure

```
app/src/main/java/com/example/meduza/
│
├── core/settings/
│   └── SettingsManager.kt        Single source of truth for all user prefs.
│                                  Add new prefs here as companion object constants.
│
├── data/
│   ├── model/                    Pure data classes (no Android dependencies)
│   └── repository/
│       ├── LocalMediaRepository  Reads local audio via MediaStore
│       └── OnlineMediaRepository YouTube Music API access via innertube
│
├── playback/
│   ├── MeduzaIntelligenceEngine  ★ Core algorithm — pure Kotlin, no Android deps
│   ├── PlaybackViewModel         Bridges the engine with ExoPlayer
│   └── MeduzaPlaybackService     Media3 ExoPlayer background service
│
└── ui/
    ├── theme/
    │   ├── MeduzaThemeEngine     ★ HSL color derivation from accent hue
    │   ├── Theme.kt              MeduzaTheme composable + LocalMeduzaColors
    │   └── MeduzaColors.kt       Static fallbacks + utility functions
    ├── components/               Reusable composables (MeduzaBackground, TrackCard…)
    └── screens/
        ├── home/                 HomeScreen — discovery + recently played
        ├── search/               SearchScreen
        ├── library/              LibraryScreen — local files
        ├── player/               BottomSheetPlayer — mini + full player
        └── settings/             SettingsDialog — theme picker + audio settings
```

---

## Development Setup

```bash
# 1. Clone the repo
git clone https://github.com/akyyra/meduza.git
cd meduza

# 2. Open in Android Studio (Hedgehog+)
# 3. Build debug APK
./gradlew assembleDebug

# 4. Run tests
./gradlew test
```

No secrets, API keys, or external accounts required.

---

## Architecture Principles

| Principle | How Meduza applies it |
|---|---|
| **Separation of concerns** | Intelligence engine is pure Kotlin — zero Android imports |
| **Single source of truth** | All settings go through `SettingsManager` |
| **Composition local** | Dynamic theme via `LocalMeduzaColors.current` — never pass Color through params |
| **Reactive state** | All UI state via `StateFlow` collected with `collectAsState()` |
| **Repository pattern** | ViewModels never touch `YouTube.*` directly — always via repository |

---

## How to Add a Feature

### Adding a new Setting
1. Add a constant + property to `SettingsManager.kt`
2. Add the UI control in `SettingsDialog.kt`
3. Read from `SettingsManager` in the relevant ViewModel

### Adding a new Screen
1. Create a `screens/yourfeature/` directory
2. Create `YourFeatureScreen.kt` as a `@Composable` function
3. Add to the `when (selectedSection)` in `MeduzaApp.kt`
4. Add a nav item in `MeduzaNavBar`

### Adding a new Color to the Theme
1. Add the name/hue pair to `MeduzaThemeEngine.PRESET_HUES`
2. Ensure the HSL derivation in `deriveColors()` looks good at that hue

---

## Contributing to the Intelligence Engine

`MeduzaIntelligenceEngine.kt` is intentionally pure Kotlin with no Android dependencies, making it fully unit-testable. Here's how each signal works and how to extend it:

| Signal | Where | How to improve |
|---|---|---|
| Taste Affinity | `scoreTrack()` — `artistPlayCounts` lookup | Add genre-level tracking, not just artist |
| Recency Penalty | `scoreTrack()` — `recentlyPlayedIds` check | Add time-decayed penalty (harder penalty for < 1 hour ago) |
| Energy Arc | `getEnergyArcTags()` | Make it user-configurable ("I work night shifts") |
| Mood Tags | `detectMoodTags()` | Add more keyword dictionaries per language |
| Diversity | `shuffleWithIntelligence()` — `recentArtists` window | Extend to genre-level diversity |

**To add a new signal:**
1. Add a new parameter to `scoreTrack()`
2. Compute your signal's score contribution
3. Add the weight with a comment explaining the rationale
4. Pass the new data from `PlaybackViewModel`

---

## Code Style

- **Kotlin** only — no Java
- **KDoc** on all `public` functions and classes
- **Trailing newline** on all files
- **Alignment formatting** — align similar lines for readability:
  ```kotlin
  val title   = "foo"
  val artist  = "bar"
  val mediaId = "abc"
  ```
- **Comments** — `// ── Section ──` headers for visual file structure

---

## Pull Request Checklist

- [ ] Code compiles with `./gradlew assembleDebug`
- [ ] New public functions have KDoc comments
- [ ] Any new `@Composable` functions use `LocalMeduzaColors.current` (not hardcoded colors)
- [ ] New settings go through `SettingsManager`
- [ ] Intelligence engine changes are pure Kotlin (no Android imports)
- [ ] PR description explains what changed and why

---

<div align="center">

**Made with ❤️ by Akyyra**

</div>
