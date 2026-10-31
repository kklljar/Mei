package com.ljyh.mei.playback.queue

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ljyh.mei.data.model.MediaMetadata

/** Serializable mirror of the playing song; MediaMetadata itself is not serializable. */
@Serializable
internal data class SnapshotSong(
    val id: Long,
    val title: String,
    val coverUrl: String = "",
    val artists: List<SnapshotArtist> = emptyList(),
    val duration: Long = 0,
    val album: SnapshotAlbum? = null,
) {
    fun toMediaMetadata(): MediaMetadata = MediaMetadata(
        id = id,
        title = title,
        coverUrl = coverUrl,
        artists = artists.map { MediaMetadata.Artist(it.id, it.name) },
        duration = duration,
        album = MediaMetadata.Album(album?.id ?: 0, album?.title ?: ""),
    )
}

@Serializable
internal data class SnapshotArtist(val id: Long = 0, val name: String = "")

@Serializable
internal data class SnapshotAlbum(val id: Long = 0, val title: String = "")

internal fun MediaMetadata.toSnapshotSong(): SnapshotSong = SnapshotSong(
    id = id,
    title = title,
    coverUrl = coverUrl,
    artists = artists.map { SnapshotArtist(it.id, it.name) },
    duration = duration,
    album = SnapshotAlbum(album.id, album.title),
)

internal data class PlaybackQueueSnapshot(
    val ids: List<String>,
    val currentIndex: Int = 0,
    val positionMs: Long = 0,
    val currentSong: SnapshotSong? = null,
    val isFmMode: Boolean = false,
    /** Null in snapshots written before per-song sources were introduced. */
    val entries: List<QueueEntrySnapshot>? = null,
)

@Serializable
internal data class QueueEntrySnapshot(val entryId: String, val source: QueueSource? = null)

internal object PlaybackQueueSnapshotCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(snapshot: PlaybackQueueSnapshot): String = json.encodeToString(snapshot)

    fun decode(value: String?): PlaybackQueueSnapshot? = runCatching {
        value?.let { json.decodeFromString<PlaybackQueueSnapshot>(it) }
            ?.takeIf { snapshot ->
                snapshot.ids.isNotEmpty() &&
                    snapshot.ids.all { it.isNotBlank() } &&
                    snapshot.currentIndex in snapshot.ids.indices &&
                    snapshot.positionMs >= 0L &&
                    (snapshot.entries == null || (snapshot.entries.size == snapshot.ids.size &&
                        snapshot.entries.all { it.entryId.isNotBlank() })) &&
                    (snapshot.currentSong == null ||
                        snapshot.currentSong.id.toString() == snapshot.ids[snapshot.currentIndex])
            }
    }.getOrNull()
}
