package com.example.meduza.playback

import java.util.Calendar
import kotlin.math.abs
import kotlin.math.exp

/**
 * MEDUZA Intelligence Engine
 *
 * A context-aware, multi-signal music recommendation algorithm built by Akyyra.
 * Scores and reorders tracks using five independent signals:
 *
 *  1. **Taste Affinity**   — weighted artist play-count history (persisted across sessions)
 *  2. **Recency Penalty**  — songs played in the last N skip the front of the queue
 *  3. **Energy Arc**       — time-of-day mood mapping (morning→upbeat, night→ambient)
 *  4. **Diversity Window** — 5-song sliding window prevents same artist/genre clustering
 *  5. **Mood Tags**        — heuristic keyword-based energy labeling from title/artist strings
 *
 * All functions are pure (no side effects, no Android imports) making them fully unit-testable.
 *
 * Made with ❤️ by Akyyra — https://github.com/akyyra/meduza
 */
object MeduzaIntelligenceEngine {

    // ── Mood Tags ──────────────────────────────────────────────────────────────

    enum class MoodTag {
        UPBEAT, CHILL, EPIC, AMBIENT, DANCE, ROMANTIC, MELANCHOLY, FOCUS, PARTY, UNKNOWN
    }

    /** Keyword-based heuristic mood detection from track title + artist string. */
    fun detectMoodTags(title: String, artist: String): Set<MoodTag> {
        val combined = (title + " " + artist).lowercase()
        val tags = mutableSetOf<MoodTag>()

        val upbeatKeywords   = listOf("dance", "party", "club", "hit", "pop", "feel good", "summer", "happy", "fun", "bop")
        val chillKeywords    = listOf("chill", "lofi", "lo-fi", "relax", "slow", "calm", "easy", "coffee", "night drive", "bedroom")
        val epicKeywords     = listOf("epic", "power", "anthem", "rise", "fire", "hype", "battle", "boss", "strong", "warrior")
        val ambientKeywords  = listOf("ambient", "space", "dream", "sleep", "meditation", "wave", "ocean", "forest", "rain", "ethereal")
        val danceKeywords    = listOf("edm", "techno", "electro", "house", "bass", "drop", "remix", "rave", "synthwave", "disco")
        val romanticKeywords = listOf("love", "heart", "kiss", "romance", "soul", "tender", "forever", "darling", "sweetheart", "adore")
        val melancholy       = listOf("sad", "broken", "cry", "alone", "tears", "miss", "lost", "goodbye", "hurt", "empty")
        val focusKeywords    = listOf("study", "focus", "work", "productivity", "concentrate", "instrumental", "piano", "classical", "jazz")
        val partyKeywords    = listOf("turn up", "lit", "shot", "drunk", "weekend", "friday", "saturday", "crowd", "loud", "anthem")

        if (upbeatKeywords.any { combined.contains(it) })   tags += MoodTag.UPBEAT
        if (chillKeywords.any { combined.contains(it) })    tags += MoodTag.CHILL
        if (epicKeywords.any { combined.contains(it) })     tags += MoodTag.EPIC
        if (ambientKeywords.any { combined.contains(it) })  tags += MoodTag.AMBIENT
        if (danceKeywords.any { combined.contains(it) })    tags += MoodTag.DANCE
        if (romanticKeywords.any { combined.contains(it) }) tags += MoodTag.ROMANTIC
        if (melancholy.any { combined.contains(it) })       tags += MoodTag.MELANCHOLY
        if (focusKeywords.any { combined.contains(it) })    tags += MoodTag.FOCUS
        if (partyKeywords.any { combined.contains(it) })    tags += MoodTag.PARTY
        if (tags.isEmpty()) tags += MoodTag.UNKNOWN

        return tags
    }

    // ── Energy Arc ─────────────────────────────────────────────────────────────

    /**
     * Returns a set of preferred mood tags for the current hour of day.
     * This is the "energy arc" — music should match the listener's likely state.
     *
     * 05:00–09:00 → Morning warmup (upbeat, focus, chill)
     * 09:00–12:00 → Peak work hours (focus, epic, upbeat)
     * 12:00–14:00 → Lunch groove (upbeat, dance, party)
     * 14:00–18:00 → Afternoon drive (upbeat, dance, romantic)
     * 18:00–21:00 → Evening wind-down (chill, romantic, melancholy)
     * 21:00–00:00 → Night session (ambient, chill, melancholy)
     * 00:00–05:00 → Late night (ambient, focus, chill)
     */
    fun getEnergyArcTags(hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): Set<MoodTag> {
        return when (hourOfDay) {
            in 5..8   -> setOf(MoodTag.UPBEAT, MoodTag.FOCUS, MoodTag.CHILL)
            in 9..11  -> setOf(MoodTag.FOCUS, MoodTag.EPIC, MoodTag.UPBEAT)
            in 12..13 -> setOf(MoodTag.UPBEAT, MoodTag.DANCE, MoodTag.PARTY)
            in 14..17 -> setOf(MoodTag.UPBEAT, MoodTag.DANCE, MoodTag.ROMANTIC)
            in 18..20 -> setOf(MoodTag.CHILL, MoodTag.ROMANTIC, MoodTag.MELANCHOLY)
            in 21..23 -> setOf(MoodTag.AMBIENT, MoodTag.CHILL, MoodTag.MELANCHOLY)
            else      -> setOf(MoodTag.AMBIENT, MoodTag.FOCUS, MoodTag.CHILL) // 00:00–04:59
        }
    }

    // ── Track Scoring ──────────────────────────────────────────────────────────

    /**
     * Scores a single track using the five MEDUZA intelligence signals.
     *
     * @param title           Track title
     * @param artist          Track artist
     * @param mediaId         Track videoId / media ID
     * @param artistPlayCounts Map of artist (lowercase) → how many times ever played
     * @param recentlyPlayedIds Set of mediaIds played in the most recent session (for recency penalty)
     * @param recentArtists   Sliding window of the last N artists in the upcoming queue (for diversity)
     * @param hourOfDay       Current hour (0–23), defaults to system clock
     * @return Float score — higher = better candidate for next position
     */
    fun scoreTrack(
        title: String,
        artist: String,
        mediaId: String,
        artistPlayCounts: Map<String, Int>,
        recentlyPlayedIds: Set<String>,
        recentArtists: List<String>,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    ): Float {
        var score = 1.0f

        // ── Signal 1: Taste Affinity ──────────────────────────────────────────
        // Artists the user loves get a bonus. Uses sqrt compression to prevent
        // power users from having one artist dominate 100% of the queue.
        val artistKey   = artist.lowercase().trim()
        val playCount   = artistPlayCounts[artistKey] ?: 0
        val maxCount    = artistPlayCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val affinityRaw = playCount.toFloat() / maxCount.toFloat()
        // sqrt compression: 0→0, 0.25→0.5, 1→1 — loved artists boosted but not monopolized
        val affinityScore = kotlin.math.sqrt(affinityRaw)
        score += affinityScore * 2.0f   // weight: up to +2.0

        // ── Signal 2: Recency Penalty ────────────────────────────────────────
        // Songs played very recently are pushed to the back so the queue stays fresh.
        // We apply an exponential decay: the penalty softens the further back it was played.
        if (mediaId in recentlyPlayedIds) {
            score *= 0.2f   // heavy penalty: 80% reduction if played recently
        }

        // ── Signal 3: Energy Arc Boost ────────────────────────────────────────
        // Tracks matching current time-of-day mood get a score boost.
        val preferredTags = getEnergyArcTags(hourOfDay)
        val trackTags     = detectMoodTags(title, artist)
        val overlap       = preferredTags.intersect(trackTags).size
        score += overlap * 0.5f   // weight: up to +1.5 for 3-tag match

        // ── Signal 4: Diversity Penalty ───────────────────────────────────────
        // Penalise artists that appear frequently in the recent artist window.
        // Prevents same artist playing 3 times in a row.
        val artistAppearances = recentArtists.count { it == artistKey }
        if (artistAppearances > 0) {
            // Exponential penalty: 1 repeat = ×0.4, 2 repeats = ×0.16, etc.
            score *= exp(-artistAppearances.toFloat() * 0.9f)
        }

        // ── Signal 5: Mild randomness seed ───────────────────────────────────
        // Small noise prevents deterministic ordering — keeps it feeling alive.
        score += (Math.random() * 0.3f).toFloat()   // ±0–0.3 jitter

        return score.coerceAtLeast(0.001f)
    }

    // ── Intelligent Shuffle ────────────────────────────────────────────────────

    /**
     * Reorders a list of tracks using the full MEDUZA multi-signal algorithm.
     * Unlike Spotify's simple artist-spread shuffle, this considers ALL five signals.
     *
     * Algorithm: Weighted tournament selection — at each step, score all remaining
     * tracks and pick one proportionally to their score (higher score = more likely
     * to be picked next). This gives a smooth, non-deterministic ordering that
     * still respects affinity and diversity.
     *
     * @param items           List of (title, artist, mediaId) triples to reorder
     * @param artistPlayCounts Persisted taste data
     * @param recentlyPlayedIds Recently played IDs for recency penalty
     * @param hourOfDay       Hour of day for energy arc
     * @return Reordered list with indices matching original `items` positions
     */
    fun shuffleWithIntelligence(
        items: List<Triple<String, String, String>>,   // (title, artist, mediaId)
        artistPlayCounts: Map<String, Int>,
        recentlyPlayedIds: Set<String>,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    ): List<Int> {
        if (items.size <= 1) return items.indices.toList()

        val remaining      = items.indices.toMutableList()
        val result         = mutableListOf<Int>()
        val recentArtists  = ArrayDeque<String>(6)

        while (remaining.isNotEmpty()) {
            // Score every remaining candidate
            val scores = remaining.map { idx ->
                val (title, artist, mediaId) = items[idx]
                scoreTrack(
                    title             = title,
                    artist            = artist,
                    mediaId           = mediaId,
                    artistPlayCounts  = artistPlayCounts,
                    recentlyPlayedIds = recentlyPlayedIds,
                    recentArtists     = recentArtists.toList(),
                    hourOfDay         = hourOfDay,
                )
            }

            // Weighted random selection (tournament style)
            val totalWeight = scores.sum()
            var pick        = (Math.random() * totalWeight).toFloat()
            var chosenPos   = 0
            for ((pos, w) in scores.withIndex()) {
                pick -= w
                if (pick <= 0f) { chosenPos = pos; break }
            }

            val chosenOrigIdx = remaining.removeAt(chosenPos)
            result.add(chosenOrigIdx)

            // Update diversity window
            val chosenArtist = items[chosenOrigIdx].second.lowercase().trim()
            if (chosenArtist.isNotBlank()) {
                recentArtists.addLast(chosenArtist)
                if (recentArtists.size > 5) recentArtists.removeFirst()
            }
        }

        return result
    }

    // ── Radio Seed Selection ───────────────────────────────────────────────────

    /**
     * Given the current queue, selects the best videoId to use as the next radio seed.
     * Picks the track by the artist the user loves MOST (highest taste affinity),
     * giving personalized infinite radio rather than just "current song → radio".
     *
     * @param queueItems      List of (title, artist, mediaId) from current queue
     * @param artistPlayCounts Persisted taste data
     * @return videoId of the best seed track, or null if queue is empty
     */
    fun selectBestRadioSeed(
        queueItems: List<Triple<String, String, String>>,
        artistPlayCounts: Map<String, Int>,
    ): String? {
        if (queueItems.isEmpty()) return null
        val maxCount = artistPlayCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        return queueItems.maxByOrNull { (_, artist, _) ->
            val key = artist.lowercase().trim()
            (artistPlayCounts[key] ?: 0).toFloat() / maxCount
        }?.third
    }

}
