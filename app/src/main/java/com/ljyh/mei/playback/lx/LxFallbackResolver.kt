package com.ljyh.mei.playback.lx

import android.content.Context
import androidx.core.net.toUri
import com.ljyh.mei.constants.LxFallbackEnabledKey
import com.ljyh.mei.constants.LxSourceUrlsKey
import com.ljyh.mei.data.model.api.GetSongDetails
import com.ljyh.mei.data.network.api.ApiService
import com.ljyh.mei.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LxFallbackResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
) {
    private val scriptCache = ConcurrentHashMap<String, String>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()
    private val preload by lazy {
        context.assets.open("lx/preload.js").bufferedReader().use { it.readText() }
    }

    suspend fun isEnabled(): Boolean =
        context.dataStore.data.first()[LxFallbackEnabledKey] == true

    suspend fun resolve(mediaId: String, quality: String): android.net.Uri? {
        val preferences = context.dataStore.data.first()
        if (preferences[LxFallbackEnabledKey] != true) return null
        val urls = preferences[LxSourceUrlsKey].orEmpty().lineSequence()
            .map(String::trim)
            .filter { it.startsWith("https://") || it.startsWith("http://") }
            .distinct()
            .take(5)
            .toList()
        if (urls.isEmpty()) return null

        val track = runCatching {
            apiService.getSongDetail(GetSongDetails(c = mediaId)).songs.firstOrNull()
        }.getOrNull() ?: return null
        val musicInfo = buildJsonObject {
            put("source", "wy")
            put("name", track.name)
            put("singer", track.ar.joinToString("、") { it.name.orEmpty() })
            put("albumName", track.al.name.orEmpty())
            put("albumId", track.al.Id)
            put("songmid", track.id.toString())
            put("songId", track.id)
            put("interval", "%02d:%02d".format(track.dt / 60_000, track.dt / 1000 % 60))
            put("_interval", track.dt / 1000)
            put("img", track.al.picUrl)
            put("types", buildJsonArray {
                listOf("128k", "320k", "flac", "flac24bit").forEach { type ->
                    add(buildJsonObject { put("type", type) })
                }
            })
        }

        return coroutineScope {
            urls.mapIndexed { index, url ->
                async {
                    kotlinx.coroutines.delay(index * 150L)
                    withTimeoutOrNull(18_000) {
                        val code = scriptCache[url] ?: downloadScript(url)?.also { scriptCache[url] = it }
                            ?: return@withTimeoutOrNull null
                        val audioUrl = runCatching { LxHost(preload).resolve(code, musicInfo, quality) }
                            .onFailure { Timber.tag("LxFallback").w(it, "LX source failed") }
                            .getOrNull()
                        audioUrl?.takeIf { isPlayable(it) }?.toUri()
                    }
                }
            }.awaitAll().firstOrNull { it != null }
        }
    }

    private fun downloadScript(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).header("Accept", "application/javascript,text/plain,*/*").build())
            .execute().use { response ->
                if (!response.isSuccessful || response.body.contentLength() > 1024 * 1024) return null
                val bytes = response.body.byteStream().readNBytes(1024 * 1024 + 1)
                if (bytes.isEmpty() || bytes.size > 1024 * 1024) return null
                bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            }
    }.getOrNull()

    fun isPlayable(url: String): Boolean = runCatching {
        client.newCall(
            Request.Builder().url(url)
                .header("Range", "bytes=0-4095")
                .header("Accept-Encoding", "identity")
                .build()
        ).execute().use { response ->
            val type = response.header("Content-Type").orEmpty().lowercase()
            response.isSuccessful && !type.contains("json") && !type.contains("html") &&
                !type.startsWith("text/") && response.body.byteStream().readNBytes(16).isNotEmpty()
        }
    }.getOrDefault(false)
}
