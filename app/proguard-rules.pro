# ── Meduza ProGuard Rules ──────────────────────────────────────────────────────

# Keep line numbers in stack traces for debugging
-keepattributes SourceFile,LineNumberTable

# ── Mozilla Rhino (PoToken JS engine in innertube) ─────────────────────────────
# Rhino references desktop-only java.beans.* and javax.script.* APIs that don't
# exist on Android. These are never called at runtime on Android — suppress them.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.mozilla.javascript.**
-keep class org.mozilla.javascript.** { *; }

# ── Kotlin ─────────────────────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**

# ── Coroutines ─────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ── Media3 / ExoPlayer ─────────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── Compose ────────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Coil ───────────────────────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ── Innertube / NewPipe ────────────────────────────────────────────────────────
-keep class moe.koiverse.archivetune.** { *; }
-dontwarn moe.koiverse.**
-keep class org.schabi.newpipe.** { *; }
-dontwarn org.schabi.**

# ── OkHttp ─────────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ── JSON / Gson ────────────────────────────────────────────────────────────────
-keep class org.json.** { *; }
-dontwarn org.json.**

# ── App Models ─────────────────────────────────────────────────────────────────
-keep class com.example.meduza.data.model.** { *; }
-keep class com.example.meduza.playback.PlaybackUiState { *; }

# ── ViewModels ─────────────────────────────────────────────────────────────────
-keep class com.example.meduza.MainViewModel { *; }
-keep class com.example.meduza.playback.PlaybackViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(...); }

# ── Enums ──────────────────────────────────────────────────────────────────────
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# ── SharedPreferences (safe) ────────────────────────────────────────────────────
-keep class com.example.meduza.core.settings.SettingsManager { *; }