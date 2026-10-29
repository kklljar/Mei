package com.ljyh.mei.playback.lx

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.evaluate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class LxCapability(
    val name: String = "",
    val type: String = "music",
    val actions: List<String> = emptyList(),
    val qualitys: List<String> = emptyList(),
)

internal val lxJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal class LxHost(private val preload: String) {
    suspend fun resolve(code: String, musicInfo: JsonObject, requestedQuality: String): String? =
        withContext(Dispatchers.Default) {
            withTimeout(15_000) {
                val http = LxHttpTransport()
                val js = QuickJs.create(Dispatchers.IO)
                val timers = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
                try {
                    js.memoryLimit = 32L * 1024 * 1024
                    js.maxStackSize = 512L * 1024
                    js.evaluationTimeoutMillis = 2_000
                    js.asyncFunction("__meiHttp") { args -> http.request(args[0] as String) }
                    js.function("__meiCancel") { args -> http.cancel(args[0] as String); null }
                    js.function("__meiCrypto") { args -> LxCrypto.invoke(args[0] as String) }
                    js.asyncFunction("__meiDelay") { args ->
                        val id = args[0] as String
                        val timer = timers.getOrPut(id) { CompletableDeferred() }
                        try {
                            withTimeoutOrNull((args[1] as Number).toLong().coerceIn(1, 15_000)) { timer.await() }
                            null
                        } finally {
                            timers.remove(id)
                        }
                    }
                    js.function("__meiClearTimer") { args ->
                        timers.getOrPut(args[0] as String) { CompletableDeferred() }.complete(Unit)
                        null
                    }
                    js.evaluate<Any?>(preload, filename = "lx-host.js")
                    js.evaluate<Any?>("__meiHost.setup({name:'Mei LX fallback',version:'1'})")
                    js.evaluate<Any?>(code, filename = "user-source.js")
                    val capabilities = lxJson.decodeFromString<Map<String, LxCapability>>(
                        js.evaluate<String>("__meiHost.capabilities()")
                    )
                    val capability = capabilities["wy"]
                        ?.takeIf { it.type == "music" && "musicUrl" in it.actions }
                        ?: return@withTimeout null
                    val qualities = qualityCandidates(requestedQuality, capability.qualitys)
                    for (quality in qualities) {
                        val key = "result_" + UUID.randomUUID().toString().replace("-", "")
                        val request = buildJsonObject {
                            put("source", "wy")
                            put("action", "musicUrl")
                            put("info", buildJsonObject {
                                put("type", quality)
                                put("musicInfo", musicInfo)
                            })
                        }
                        try {
                            js.evaluate<Any?>("__meiHost.request(${JsonPrimitive(key)}, $request)")
                            val url = js.evaluate<String>("__meiHost.result(${JsonPrimitive(key)})")
                            if (url.startsWith("http://") || url.startsWith("https://")) return@withTimeout url
                        } catch (_: Exception) {
                            // Try the next quality.
                        }
                    }
                    null
                } finally {
                    http.close()
                    js.close()
                }
            }
        }

    private fun qualityCandidates(requested: String, available: List<String>): List<String> {
        val ordered = listOf("128k", "320k", "flac", "flac24bit")
        val target = when (requested.lowercase()) {
            "standard", "128k" -> 0
            "higher", "exhigh", "320k" -> 1
            "lossless", "flac" -> 2
            else -> 3
        }
        val supported = available.ifEmpty { ordered }
        return ordered.take(target + 1).asReversed().filter { it in supported }
    }
}
