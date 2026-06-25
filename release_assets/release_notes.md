# Meduza v1.1.0 (Adaptive Architecture Update) ⚡️

We are incredibly excited to launch **v1.1.0**, featuring a massive architectural overhaul that brings Spotify-level feed density, intelligent performance scaling, and massive network optimizations!

## ✨ What's New

- **🚀 Adaptive Hardware Engine**: Meduza now acts as an intelligent app. It detects your phone's capabilities (RAM and CPU cores) on startup. Low-end devices get an ultra-optimized, battery-friendly experience with compressed thumbnails and throttled caching, while high-end devices unlock maximum caching and aggressive pre-fetching.
- **📱 Spotify-Style Feed Density**: Fixed an issue where YouTube's API natively limited the Home feed to just 3 rows. Meduza now instantly fetches multiple data blocks in the background to guarantee 10+ rows the second you open the app.
- **🎨 UI Standardization**: Ripped out the chaotic vertical grids! The entire Home feed is now beautifully standardized into clean, horizontally scrolling carousels of premium square cards.
- **📶 Massive Network Optimizations**: Injected a 50MB background API cache directly into the OkHttp networking layer. Playlists and searches load instantly from disk instead of draining your battery on the network. ExoPlayer buffering has also been dynamically optimized for 3G cellular connections.
- **🔒 Security Lockdown**: Completely blocked cleartext (HTTP) traffic at the AndroidManifest level for MitM protection and secured caches.

---

# Meduza v1.0.0 🎵

We are thrilled to announce the first stable release of **Meduza**! 

Meduza is a premium, open-source Android music player powered by YouTube Music, designed to offer an intelligent, deeply personalized listening experience without the need for subscriptions or API keys.

## ✨ Highlights

- **🧠 MEDUZA Intelligence Engine**: A custom multi-signal recommendation algorithm that curates your music using Taste Affinity Scoring, Energy Arc Scheduling, and Recency Decay to ensure a perfect flow of tracks.
- **🎨 Dynamic Theme Engine**: 13 vibrant accent colors that auto-derive background surfaces, borders, and neon glows.
- **🎧 Seamless Playback**: Powered by Media3 ExoPlayer with true gapless playback, intelligent radio queues, and auto-skip on stream errors.
- **📚 Local Library Support**: Full MediaStore integration for local audio files alongside online streaming.

## 📦 Installation

Download the `app-release.apk` below and install it directly on your Android device (Android 8.0+ required).

---
*Note: This APK is fully signed and optimized via R8 shrinking, keeping the app lightweight and fast.*
