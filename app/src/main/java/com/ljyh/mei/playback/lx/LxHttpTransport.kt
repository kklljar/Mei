package com.ljyh.mei.playback.lx

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class LxHttpTransport : AutoCloseable {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val calls = ConcurrentHashMap<String, Call>()

    suspend fun request(raw: String): String {
        require(raw.length <= 2 * 1024 * 1024)
        val input = lxJson.parseToJsonElement(raw).jsonObject
        val id = input.string("id")
        val options = input["options"] as? JsonObject ?: JsonObject(emptyMap())
        val method = options.string("method", "GET").uppercase()
        val builder = Request.Builder().url(input.string("url")).header("User-Agent", "Mozilla/5.0")
        (options["headers"] as? JsonObject)?.forEach { (key, value) ->
            builder.header(key, value.jsonPrimitive.content)
        }
        val contentType = (options["headers"] as? JsonObject)?.entries
            ?.firstOrNull { it.key.equals("content-type", true) }?.value?.jsonPrimitive?.content
        val form = options["form"] as? JsonObject
        val formData = options["formData"] as? JsonObject
        val body = when {
            form != null -> FormBody.Builder().apply {
                form.forEach { (key, value) -> add(key, value.jsonPrimitive.content) }
            }.build()
            formData != null -> MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                formData.forEach { (key, value) ->
                    addFormDataPart(key, (value as? JsonPrimitive)?.content ?: value.toString())
                }
            }.build()
            options["body"] != null && options["body"] != JsonNull -> {
                val value = options.getValue("body")
                val text = if (value is JsonPrimitive) value.content else value.toString()
                text.toRequestBody((contentType ?: "application/json").toMediaTypeOrNull())
            }
            method !in setOf("GET", "HEAD") -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        val call = client.newCall(builder.method(method, body).build())
        calls[id] = call
        return try {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val result = response.use {
                                val bytes = it.body.byteStream().readNBytes(2 * 1024 * 1024 + 1)
                                require(bytes.size <= 2 * 1024 * 1024) { "Response too large" }
                                val text = bytes.toString(it.body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                                val parsed = if (it.header("Content-Type").orEmpty().contains("json", true)) {
                                    runCatching { lxJson.parseToJsonElement(text) }.getOrElse { JsonPrimitive(text) }
                                } else JsonPrimitive(text)
                                buildJsonObject {
                                    put("statusCode", it.code)
                                    put("statusMessage", it.message)
                                    put("headers", buildJsonObject {
                                        it.headers.names().forEach { name -> put(name.lowercase(), it.header(name)) }
                                    })
                                    put("body", parsed)
                                }.toString()
                            }
                            if (continuation.isActive) continuation.resume(result)
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                })
            }
        } finally {
            calls.remove(id)
        }
    }

    fun cancel(id: String) = calls.remove(id)?.cancel() ?: Unit
    override fun close() {
        calls.values.forEach(Call::cancel)
        calls.clear()
    }
}

private fun JsonObject.string(key: String, default: String = ""): String =
    (get(key) as? JsonPrimitive)?.content ?: default
