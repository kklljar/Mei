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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

class SourceNotFoundException(message: String) : IOException(message)
data class PlaybackSourceInfo(val name: String, val quality: String?)

@Singleton
class MediaUriProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val songRepository: SongRepository,
    private val lxFallbackResolver: LxFallbackResolver,
    private val unblockResolver: UnblockResolver,
) {
    private data class CachedUrl(
        val url: String,
        val identity: String,
        val source: PlaybackSourceInfo,
    )
    private data class FallbackResult(
        val uri: Uri,
        val identity: String,
        val source: PlaybackSourceInfo,
    )

    private val urlCache = ConcurrentHashMap<String, CachedUrl>()
    private val lastResolved = ConcurrentHashMap<String, CachedUrl>()
    private val failedIdentities = ConcurrentHashMap<String, MutableSet<String>>()
    private val _sourceInfo = MutableStateFlow<Map<String, PlaybackSourceInfo>>(emptyMap())
    val sourceInfo = _sourceInfo.asStateFlow()

    private fun selected(mediaId: String, cached: CachedUrl): Uri {
        lastResolved[mediaId] = cached
        _sourceInfo.update { it + (mediaId to cached.source) }
        return cached.url.toUri()
    }

    suspend fun resolveMediaUri(mediaId: String, quality: String): Uri {
        val localPath = songRepository.getSong(mediaId).firstOrNull()?.path
            ?: songRepository.getSong("local_$mediaId").firstOrNull()?.path
        if (localPath != null) {
            val localUri = if (localPath.startsWith("content://")) Uri.parse(localPath)
            else File(localPath).takeIf(File::exists)?.let(Uri::fromFile)
            if (localUri != null) {
                _sourceInfo.update { it + (mediaId to PlaybackSourceInfo("本地", null)) }
                return localUri
            }
        }

        val preferences = context.dataStore.data.first()
        val storedMode = preferences[AlternativeSourceModeKey]
        val mode = runCatching { storedMode?.let(AlternativeSourceMode::valueOf) }.getOrNull()
            ?: if (preferences[LxFallbackEnabledKey] == true) AlternativeSourceMode.Lx else AlternativeSourceMode.Off
        val cacheKey = "$mediaId|$quality|${mode.name}"
        urlCache[cacheKey]?.let {
            return selected(mediaId, it)
        }
        val excluded = failedIdentities[mediaId].orEmpty()

        try {
            val response = apiService.getSongUrlV1(GetSongUrlV1(ids = "[$mediaId]", level = quality))
            val url = response.data.firstOrNull()?.url
            if ("netease" !in excluded && !url.isNullOrBlank() &&
                (mode != AlternativeSourceMode.Lx || lxFallbackResolver.isPlayable(url))) {
                val cached = CachedUrl(url, "netease", PlaybackSourceInfo("网易云", quality))
                urlCache[cacheKey] = cached
                return selected(mediaId, cached)
            }

            val fallback = fallback(mode, mediaId, quality, excluded)
            if (fallback != null) {
                val cached = CachedUrl(fallback.uri.toString(), fallback.identity, fallback.source)
                urlCache[cacheKey] = cached
                return selected(mediaId, cached)
            }
            throw SourceNotFoundException("未找到可播放音源：$mediaId")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: SourceNotFoundException) {
            throw error
        } catch (error: Exception) {
            val fallback = fallback(mode, mediaId, quality, excluded)
            if (fallback != null) {
                val cached = CachedUrl(fallback.uri.toString(), fallback.identity, fallback.source)
                urlCache[cacheKey] = cached
                return selected(mediaId, cached)
            }
            throw IOException("解析歌曲链接失败：$mediaId", error)
        }
    }

    private suspend fun fallback(
        mode: AlternativeSourceMode,
        mediaId: String,
        quality: String,
        excluded: Set<String>,
    ): FallbackResult? =
        when (mode) {
            AlternativeSourceMode.Off -> null
            AlternativeSourceMode.Lx -> lxFallbackResolver.resolve(mediaId, quality)?.let {
                FallbackResult(it, "lx", PlaybackSourceInfo("LX 音源", quality))
            }
            AlternativeSourceMode.Unblock -> resolveUnblock(mediaId, excluded)
        }

    private suspend fun resolveUnblock(mediaId: String, excluded: Set<String>): FallbackResult? {
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
        val resolved = withTimeoutOrNull(45_000) {
            unblockResolver.resolve(target) { candidate ->
                val identity = "${candidate.track.id.source}:${candidate.track.id.value}"
                // Each provider already probes its signed URL before returning it. Probing a
                // second time can invalidate or reject short-lived Migu URLs on some devices.
                identity !in excluded
            }
        } ?: return null
        val identity = "${resolved.track.id.source}:${resolved.track.id.value}"
        Timber.tag("Unblock").i("resolved id=%s source=%s", mediaId, resolved.track.id.source)
        return FallbackResult(
            resolved.url.toUri(),
            identity,
            PlaybackSourceInfo("解灰 · ${sourceDisplayName(resolved.track.id.source)}", null),
        )
    }

    private fun sourceDisplayName(source: String): String = when (source.lowercase()) {
        "qq" -> "QQ 音乐"
        "kuwo" -> "酷我音乐"
        "migu" -> "咪咕音乐"
        else -> source
    }

    fun recoverAfterError(mediaId: String): Boolean {
        val previous = lastResolved.remove(mediaId) ?: return false
        if (previous.identity == "lx") return false
        failedIdentities.getOrPut(mediaId) { ConcurrentHashMap.newKeySet() }.add(previous.identity)
        urlCache.keys.removeIf { it.startsWith("$mediaId|") }
        Timber.tag("Unblock").w("excluding failed source id=%s identity=%s", mediaId, previous.identity)
        return true
    }
}
