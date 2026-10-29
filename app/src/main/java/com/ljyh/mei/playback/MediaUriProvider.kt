package com.ljyh.mei.playback

import android.net.Uri
import androidx.core.net.toUri
import com.ljyh.mei.data.model.api.GetSongUrlV1
import com.ljyh.mei.data.network.api.ApiService
import com.ljyh.mei.di.repository.SongRepository
import com.ljyh.mei.playback.lx.LxFallbackResolver
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

class SourceNotFoundException(message: String) : IOException(message)

@Singleton
class MediaUriProvider @Inject constructor(
    private val apiService: ApiService,
    private val songRepository: SongRepository,
    private val lxFallbackResolver: LxFallbackResolver,
) {
    private val urlCache = ConcurrentHashMap<String, String>()

    suspend fun resolveMediaUri(mediaId: String, quality: String): Uri {
        val localPath = songRepository.getSong(mediaId).firstOrNull()?.path
            ?: songRepository.getSong("local_$mediaId").firstOrNull()?.path
        if (localPath != null) {
            if (localPath.startsWith("content://")) {
                return Uri.parse(localPath)
            }
            val file = File(localPath)
            if (file.exists()) {
                return Uri.fromFile(file)
            }
        }

        urlCache[mediaId]?.let { return it.toUri() }
        return try {
            val response = apiService.getSongUrlV1(
                GetSongUrlV1(ids = "[$mediaId]", level = quality)
            )
            val url = response.data.getOrNull(0)?.url

            if (url.isNullOrBlank() ||
                (lxFallbackResolver.isEnabled() && !lxFallbackResolver.isPlayable(url))) {
                Timber.tag("MediaUriProvider").d(response.toString())
                val fallback = lxFallbackResolver.resolve(mediaId, quality)
                if (fallback != null) {
                    urlCache[mediaId] = fallback.toString()
                    return fallback
                }
                throw SourceNotFoundException("API and LX sources returned no URL for $mediaId")
            }

            urlCache[mediaId] = url
            url.toUri()
        } catch (e: Exception) {
            if (e is SourceNotFoundException) throw e
            lxFallbackResolver.resolve(mediaId, quality)?.let { fallback ->
                urlCache[mediaId] = fallback.toString()
                return fallback
            }
            throw IOException("Network error resolving URL for $mediaId", e)
        }
    }
}
