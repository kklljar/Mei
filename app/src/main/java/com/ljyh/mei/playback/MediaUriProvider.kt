package com.ljyh.mei.playback

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.ljyh.mei.constants.AlternativeSourceMode
import com.ljyh.mei.constants.AlternativeSourceModeKey
import com.ljyh.mei.constants.LxFallbackEnabledKey
import com.ljyh.mei.data.model.api.GetSongDetails
import com.ljyh.mei.data.model.api.GetSongUrlV1
import com.ljyh.mei.data.network.api.ApiService
import com.ljyh.mei.di.repository.SongRepository
import com.ljyh.mei.playback.lx.LxFallbackResolver
import com.ljyh.mei.utils.dataStore
import com.ljyh.unblockneteasemusic.model.MusicAlbum
import com.ljyh.unblockneteasemusic.model.MusicArtist
import com.ljyh.unblockneteasemusic.model.MusicTrack
import com.ljyh.unblockneteasemusic.model.TrackId
import com.ljyh.unblockneteasemusic.unblock.UnblockResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

class SourceNotFoundException(message: String) : IOException(message)

@Singleton
class MediaUriProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val songRepository: SongRepository,
    private val lxFallbackResolver: LxFallbackResolver,
    private val unblockResolver: UnblockResolver,
) {
    private val urlCache = ConcurrentHashMap<String, String>()

    suspend fun resolveMediaUri(mediaId: String, quality: String): Uri {
        val localPath = songRepository.getSong(mediaId).firstOrNull()?.path
            ?: songRepository.getSong("local_$mediaId").firstOrNull()?.path
        if (localPath != null) {
            if (localPath.startsWith("content://")) return Uri.parse(localPath)
            File(localPath).takeIf(File::exists)?.let { return Uri.fromFile(it) }
        }

        val preferences = context.dataStore.data.first()
        val storedMode = preferences[AlternativeSourceModeKey]
        val mode = runCatching { storedMode?.let(AlternativeSourceMode::valueOf) }.getOrNull()
            ?: if (preferences[LxFallbackEnabledKey] == true) AlternativeSourceMode.Lx else AlternativeSourceMode.Off
        val cacheKey = "$mediaId|$quality|${mode.name}"
        urlCache[cacheKey]?.let { return it.toUri() }

        try {
            val response = apiService.getSongUrlV1(GetSongUrlV1(ids = "[$mediaId]", level = quality))
            val url = response.data.firstOrNull()?.url
            if (!url.isNullOrBlank() && (mode != AlternativeSourceMode.Lx || lxFallbackResolver.isPlayable(url))) {
                urlCache[cacheKey] = url
                return url.toUri()
            }

            val fallback = fallback(mode, mediaId, quality)
            if (fallback != null) {
                urlCache[cacheKey] = fallback.toString()
                return fallback
            }
            throw SourceNotFoundException("未找到可播放音源：$mediaId")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: SourceNotFoundException) {
            throw error
        } catch (error: Exception) {
            val fallback = fallback(mode, mediaId, quality)
            if (fallback != null) {
                urlCache[cacheKey] = fallback.toString()
                return fallback
            }
            throw IOException("解析歌曲链接失败：$mediaId", error)
        }
    }

    private suspend fun fallback(mode: AlternativeSourceMode, mediaId: String, quality: String): Uri? =
        when (mode) {
            AlternativeSourceMode.Off -> null
            AlternativeSourceMode.Lx -> lxFallbackResolver.resolve(mediaId, quality)
            AlternativeSourceMode.Unblock -> resolveUnblock(mediaId)
        }

    private suspend fun resolveUnblock(mediaId: String): Uri? {
        val song = apiService.getSongDetail(GetSongDetails(mediaId)).songs.firstOrNull() ?: return null
        val target = MusicTrack(
            id = TrackId("netease", mediaId),
            title = song.name,
            artists = song.ar.mapNotNull { artist ->
                artist.name?.takeIf(String::isNotBlank)?.let { MusicArtist(it, artist.Id.toString()) }
            },
            album = MusicAlbum(song.al.name.orEmpty(), song.al.Id.toString(), song.al.picUrl),
            durationMs = song.dt,
        )
        val resolved = withTimeoutOrNull(20_000) { unblockResolver.resolve(target) }
        return resolved?.takeIf { lxFallbackResolver.isPlayable(it.url) }?.url?.toUri().also {
            Timber.tag("Unblock").d("resolved id=%s success=%s", mediaId, it != null)
        }
    }
}
