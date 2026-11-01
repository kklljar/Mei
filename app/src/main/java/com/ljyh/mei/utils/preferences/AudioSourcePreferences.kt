package com.ljyh.mei.utils.preferences

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ljyh.mei.utils.toEnum

/** The selected backup resolver runs first; the other resolver is used as fallback. */
enum class AudioSourceMode(val label: String) {
    Off("关闭备用音源"),
    Lx("LX 优先，失败后解灰"),
    Unblock("解灰优先，失败后 LX"),
}

val AudioSourceModeKey = stringPreferencesKey("audioSourceMode")

// Keep the stored enum values so existing mode choices remain the selected priority.
fun Preferences.audioSourceMode(): AudioSourceMode = this[AudioSourceModeKey].toEnum(AudioSourceMode.Lx)
