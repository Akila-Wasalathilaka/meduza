package com.example.meduza.data.model

/** Full result from the YTM home API — sections + category chips + pagination */
data class HomePageResult(
    val sections: List<HomeSection>,
    val chips: List<HomeChip>,
    val continuation: String?,
)
