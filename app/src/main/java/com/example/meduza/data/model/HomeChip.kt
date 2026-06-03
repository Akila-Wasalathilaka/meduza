package com.example.meduza.data.model

/** Mirrors ArchiveTune's HomePage.Chip — a category filter from the YTM home API */
data class HomeChip(
    val title: String,
    val params: String,   // YouTube API endpoint params for this chip filter
)
