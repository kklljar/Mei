package com.ljyh.mei.di.repository

import com.ljyh.mei.data.model.room.Playlist
import com.ljyh.mei.data.model.room.PlaylistType
import com.ljyh.mei.data.model.UserPlaylist
import com.ljyh.mei.data.model.room.PlaylistSongCrossRef
import com.ljyh.mei.di.dao.PlaylistDao
import com.ljyh.mei.di.dao.PlaylistSongCrossRefDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class LocalPlaylistRepository @Inject constructor(private val playlistDao: PlaylistDao) {
    suspend fun getPlaylist(id: String): Playlist? = playlistDao.getPlaylist(id)
    suspend fun getPlaylistByAuthor(author: String): List<Playlist> = playlistDao.getPlaylistByAuthor(author)
    fun getAllPlaylist(): Flow<List<Playlist>> = playlistDao.getAllPlaylist()
    suspend fun insertPlaylist(playlist: Playlist) = playlistDao.insertPlaylist(playlist)
    suspend fun insertPlaylists(playlists: List<Playlist>) = playlistDao.insertPlaylists(playlists)
    suspend fun reconcileUserPlaylists(remote: List<UserPlaylist.Playlist>) {
        val existing = playlistDao.getAllPlaylist().first()
        val existingMap = existing.associateBy { it.id }
        val playlistsToInsert = remote.map {
            val cached = existingMap[it.id.toString()]
            Playlist(
                id = it.id.toString(),
                title = it.name,
                cover = it.coverImgUrl,
                author = it.creator.userId.toString(),
                authorName = it.creator.nickname,
                authorAvatar = it.creator.avatarUrl,
                count = it.trackCount,
                playCount = it.playCount,
                lastPlayTime = cached?.lastPlayTime ?: 0L,
                localPlayCount = cached?.localPlayCount ?: 0
            )
        }
        insertPlaylists(playlistsToInsert)
        val remoteIds = remote.mapTo(hashSetOf()) { it.id.toString() }
        existing.asSequence()
            .filter { it.type == PlaylistType.NETEAST && it.id !in remoteIds }
            .forEach { playlistDao.deletePlaylistById(it.id) }
    }
    suspend fun deletePlaylistById(id: String) = playlistDao.deletePlaylistById(id)
    suspend fun touchPlaylist(id: String, timestamp: Long) = playlistDao.touchPlaylist(id, timestamp)
}

class PlaylistSongCrossRefRepository @Inject constructor(private val dao: PlaylistSongCrossRefDao) {
    fun getSongIdsByPlaylist(playlistId: String): Flow<List<String>> = dao.getSongIdsByPlaylist(playlistId)
    suspend fun insert(crossRef: PlaylistSongCrossRef) = dao.insert(crossRef)
    suspend fun insertAll(crossRefs: List<PlaylistSongCrossRef>) = dao.insertAll(crossRefs)
    suspend fun deleteByPlaylist(playlistId: String) = dao.deleteByPlaylist(playlistId)
    suspend fun delete(playlistId: String, songId: String) = dao.delete(playlistId, songId)
}
