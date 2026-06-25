package com.example.meduza.data.model

data class PlaylistDetail(
    val id: String,
    val title: String,
    val description: String?,
    val authorName: String?,
    val authorAvatarUrl: String?,
    val thumbnailUrl: String?,
    val trackCountText: String?,
    val totalDurationText: String?,
    val isEditable: Boolean = false,
    val tracks: List<OnlineSong>
)
