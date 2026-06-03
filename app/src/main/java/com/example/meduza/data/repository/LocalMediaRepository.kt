package com.example.meduza.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.example.meduza.data.model.LocalSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMediaRepository {
    suspend fun loadSongs(context: Context): List<LocalSong> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val songs = mutableListOf<LocalSong>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1"
        resolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} ASC")
            ?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val title = cursor.getString(titleIndex) ?: "Unknown title"
                    val artist = cursor.getString(artistIndex) ?: "Unknown artist"
                    val album = cursor.getString(albumIndex) ?: "Unknown album"
                    val durationMs = cursor.getLong(durationIndex)
                    val path = if (dataIndex != -1) cursor.getString(dataIndex) ?: "" else ""
                    
                    // Intelligent filtering criteria to discard voice notes, records, ringtones, system files
                    val isIgnoredFolder = path.contains("/Recordings/", ignoreCase = true) ||
                            path.contains("/Recorder/", ignoreCase = true) ||
                            path.contains("/Call/", ignoreCase = true) ||
                            path.contains("/WhatsApp/", ignoreCase = true) ||
                            path.contains("/Telegram/", ignoreCase = true) ||
                            path.contains("/Notifications/", ignoreCase = true) ||
                            path.contains("/Ringtones/", ignoreCase = true) ||
                            path.contains("/Alarms/", ignoreCase = true) ||
                            path.contains("/System/", ignoreCase = true) ||
                            path.contains("/voice/", ignoreCase = true) ||
                            path.contains("/cache/", ignoreCase = true)
                    
                    // Music tracks are usually >= 30 seconds
                    val hasValidDuration = durationMs >= 30000

                    if (hasValidDuration && !isIgnoredFolder) {
                        val uri = ContentUris.withAppendedId(collection, id)
                        songs.add(
                            LocalSong(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = durationMs,
                                uri = uri,
                            )
                        )
                    }
                }
            }
        songs
    }
}
