package com.example.meduza.data.model

data class OnlineSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    val type: String = "song",
)
