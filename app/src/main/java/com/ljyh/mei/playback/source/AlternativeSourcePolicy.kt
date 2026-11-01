package com.ljyh.mei.playback.source

import com.ljyh.mei.utils.preferences.AudioSourceMode
import kotlinx.coroutines.CancellationException

/** Tries both backup resolvers in the user's selected order. */
internal suspend fun <T> AudioSourceMode.resolveAlternative(
    lx: suspend () -> T?,
    unblock: suspend () -> T?,
    onNoResult: (String) -> Unit = {},
    onFailure: (String, Exception) -> Unit = { _, _ -> },
): T? = when (this) {
    AudioSourceMode.Off -> null
    AudioSourceMode.Lx -> resolveInOrder(
        listOf("LX" to lx, "解灰" to unblock), onNoResult, onFailure,
    )
    AudioSourceMode.Unblock -> resolveInOrder(
        listOf("解灰" to unblock, "LX" to lx), onNoResult, onFailure,
    )
}

private suspend fun <T> resolveInOrder(
    resolvers: List<Pair<String, suspend () -> T?>>,
    onNoResult: (String) -> Unit,
    onFailure: (String, Exception) -> Unit,
): T? {
    for ((name, resolve) in resolvers) {
        val result = try {
            resolve()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            onFailure(name, e)
            null
        }
        if (result != null) return result
        onNoResult(name)
    }
    return null
}

internal fun AudioSourceMode.recoveryKey(mediaId: String): String = "$mediaId|$name"
