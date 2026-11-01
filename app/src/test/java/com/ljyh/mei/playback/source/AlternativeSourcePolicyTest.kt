package com.ljyh.mei.playback.source

import com.ljyh.mei.utils.preferences.AudioSourceMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlternativeSourcePolicyTest {
    @Test
    fun unblockPriorityTriesLxAfterNoMatch() = runBlocking {
        val calls = mutableListOf<String>()

        val result = AudioSourceMode.Unblock.resolveAlternative(
            lx = { calls += "lx"; "lx-result" },
            unblock = { calls += "unblock"; null },
        )

        assertEquals("lx-result", result)
        assertEquals(listOf("unblock", "lx"), calls)
    }

    @Test
    fun lxPriorityFallsBackAfterResolverFailure() = runBlocking {
        val calls = mutableListOf<String>()
        val failures = mutableListOf<String>()

        val result = AudioSourceMode.Lx.resolveAlternative(
            lx = { calls += "lx"; throw IllegalStateException("resolver failed") },
            unblock = { calls += "unblock"; "unblock-result" },
            onFailure = { source, _ -> failures += source },
        )

        assertEquals("unblock-result", result)
        assertEquals(listOf("lx", "unblock"), calls)
        assertEquals(listOf("LX"), failures)
    }

    @Test
    fun offDoesNotInvokeBackupResolvers() = runBlocking {
        val calls = mutableListOf<String>()

        val result = AudioSourceMode.Off.resolveAlternative(
            lx = { calls += "lx"; "lx-result" },
            unblock = { calls += "unblock"; "unblock-result" },
        )

        assertNull(result)
        assertEquals(emptyList<String>(), calls)
    }
}
