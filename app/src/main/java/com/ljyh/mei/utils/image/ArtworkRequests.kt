package com.ljyh.mei.utils.image

import coil3.Extras
import coil3.getExtra
import coil3.intercept.Interceptor
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.size.Dimension
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class ArtworkSize(val pixels: Int) {
    Small(192),
    Card(512),
    Detail(1024);

    companion object {
        fun forBounds(width: Int?, height: Int?): ArtworkSize {
            val pixels = listOfNotNull(width, height)
                .filter { it > 0 }
                .maxOrNull() ?: Card.pixels
            return entries.firstOrNull { pixels <= it.pixels } ?: Detail
        }
    }
}

private val originalArtwork = Extras.Key(false)
private val processingParameters = setOf(
    "param", "imageView", "thumbnail", "type", "quality", "enlarge"
)
private val processingChainSeparator = Regex("\\||%7c", RegexOption.IGNORE_CASE)

private fun String.neteaseImageUrl(): HttpUrl? = toHttpUrlOrNull()?.takeIf {
    it.host == "music.126.net" || it.host.endsWith(".music.126.net")
}

private fun HttpUrl.hasCompositeProcessing(): Boolean =
    processingChainSeparator.containsMatchIn(encodedQuery.orEmpty()) ||
        "watermark" in queryParameterNames

fun String.artworkImage(size: ArtworkSize): String {
    val url = neteaseImageUrl() ?: return this
    if (url.hasCompositeProcessing()) return this

    val builder = url.newBuilder()
    processingParameters.forEach(builder::removeAllQueryParameters)
    return builder
        .addQueryParameter("imageView", "1")
        .addQueryParameter("quality", "80")
        .addQueryParameter("thumbnail", "${size.pixels}z${size.pixels}")
        .addQueryParameter("type", "webp")
        .setQueryParameter("tostatic", "0")
        .build()
        .toString()
}

fun String.originalArtworkUrl(): String {
    val url = neteaseImageUrl() ?: return this
    if (url.hasCompositeProcessing()) return this

    val builder = url.newBuilder()
    (processingParameters + "tostatic").forEach(builder::removeAllQueryParameters)
    return builder.build().toString()
}

fun ImageRequest.Builder.originalArtwork(url: String?): ImageRequest.Builder = apply {
    data(url?.originalArtworkUrl())
    extras[originalArtwork] = true
}

/** Normalizes NetEase artwork before Coil performs memory and disk cache lookup. */
class ArtworkRequestInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val source = when (val data = request.data) {
            is String -> data
            is android.net.Uri -> data.toString()
            else -> return chain.proceed()
        }
        if (source.neteaseImageUrl() == null) return chain.proceed()

        val normalized = if (request.getExtra(originalArtwork)) {
            source.originalArtworkUrl()
        } else {
            source.artworkImage(
                ArtworkSize.forBounds(
                    (chain.size.width as? Dimension.Pixels)?.px,
                    (chain.size.height as? Dimension.Pixels)?.px,
                )
            )
        }
        return chain.withRequest(request.newBuilder().data(normalized).build()).proceed()
    }
}
