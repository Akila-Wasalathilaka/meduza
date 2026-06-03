package com.example.meduza

import kotlinx.coroutines.runBlocking
import moe.koiverse.archivetune.innertube.YouTube

fun main() = runBlocking {
    val result = YouTube.home().getOrNull()
    if (result == null) {
        println("Result is null")
        return@runBlocking
    }
    println("Sections count: ${result.sections.size}")
    result.sections.forEach { section ->
        println("Section: ${section.title}")
        val songs = section.items.filterIsInstance<moe.koiverse.archivetune.innertube.models.SongItem>()
        println("  Songs: ${songs.size}")
    }
}
