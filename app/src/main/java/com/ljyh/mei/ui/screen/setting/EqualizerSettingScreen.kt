package com.ljyh.mei.ui.screen.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ljyh.mei.constants.AutoEqEnabledKey
import com.ljyh.mei.constants.AutoEqIntensityKey
import com.ljyh.mei.constants.OutputLimiterEnabledKey
import com.ljyh.mei.constants.ParametricEqualizerProfileKey
import com.ljyh.mei.constants.ReverbProfileKey
import com.ljyh.mei.constants.UserEqualizerPresetsKey
import com.ljyh.mei.playback.equalizer.EqualizerPreset
import com.ljyh.mei.playback.equalizer.EqualizerPresets
import com.ljyh.mei.playback.equalizer.EqualizerProfile
import com.ljyh.mei.playback.equalizer.EqualizerProfileCodec
import com.ljyh.mei.playback.equalizer.EqFilter
import com.ljyh.mei.playback.equalizer.ReverbPresets
import com.ljyh.mei.playback.equalizer.ReverbProfileCodec
import com.ljyh.mei.ui.component.IconButton
import com.ljyh.mei.ui.component.ListPreference
import com.ljyh.mei.ui.component.PreferenceEntry
import com.ljyh.mei.ui.component.PreferenceGroupTitle
import com.ljyh.mei.ui.component.SwitchPreference
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.screen.backToMain
import com.ljyh.mei.utils.rememberPreference
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSettingScreen(scrollBehavior: TopAppBarScrollBehavior) {
    val navController = LocalNavController.current

    val (profileRaw, setProfileRaw) = rememberPreference(ParametricEqualizerProfileKey, "")
    val (reverbRaw, setReverbRaw) = rememberPreference(ReverbProfileKey, "")
    val (limiterEnabled, setLimiterEnabled) = rememberPreference(OutputLimiterEnabledKey, true)
    val (autoEqEnabled, setAutoEqEnabled) = rememberPreference(AutoEqEnabledKey, false)
    val (autoEqIntensity, setAutoEqIntensity) = rememberPreference(AutoEqIntensityKey, 50)
    val (userPresetsRaw, setUserPresetsRaw) = rememberPreference(UserEqualizerPresetsKey, "")

    val profile = remember(profileRaw) {
        EqualizerProfileCodec.decode(profileRaw) ?: EqualizerProfile()
    }
    val reverb = remember(reverbRaw) {
        ReverbProfileCodec.decode(reverbRaw)
    }
    val userPresets = remember(userPresetsRaw) {
        EqualizerPresets.decodeCustom(userPresetsRaw)
    }
    val allPresets = remember(userPresets) { EqualizerPresets.builtIn + userPresets }

    fun updateProfile(transform: (EqualizerProfile) -> EqualizerProfile) {
        setProfileRaw(EqualizerProfileCodec.encode(transform(profile)))
    }

    fun applyPreset(preset: EqualizerPreset) {
        updateProfile { current -> preset.profile.copy(enabled = current.enabled) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("音效") },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            tint = MaterialTheme.colorScheme.onSurface,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            Modifier
                .padding(paddingValues)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
        ) {
            PreferenceGroupTitle(title = "均衡器")

            SwitchPreference(
                title = { Text("启用均衡器") },
                description = "对在线播放与本地播放生效",
                icon = { Icon(Icons.Rounded.Equalizer, null) },
                checked = profile.enabled,
                onCheckedChange = { enabled -> updateProfile { it.copy(enabled = enabled) } }
            )

            ListPreference(
                title = { Text("预设") },
                description = allPresets.firstOrNull { it.id == profile.presetId }?.name ?: "自定义",
                icon = { Icon(Icons.Rounded.Tune, null) },
                selectedValue = profile.presetId,
                values = allPresets.map { it.id },
                valueText = { id -> allPresets.firstOrNull { it.id == id }?.name ?: id },
                onValueSelected = { id -> allPresets.firstOrNull { it.id == id }?.let(::applyPreset) }
            )

            if (profile.filters.isNotEmpty()) {
                profile.filters.forEach { filter ->
                    FilterGainRow(
                        filter = filter,
                        key = "${profileRaw.hashCode()}:${filter.id}",
                    ) { gain ->
                        updateProfile { current ->
                            current.copy(
                                filters = current.filters.map {
                                    if (it.id == filter.id) it.copy(gainDb = gain) else it
                                }
                            )
                        }
                    }
                }
            }

            PreferenceEntry(
                title = { Text("保存为自定义预设") },
                description = "最多保存 50 个，随当前曲线一起存储",
                icon = { Icon(Icons.Rounded.GraphicEq, null) },
                trailingContent = {
                    TextButton(
                        onClick = {
                            val name = "自定义 ${userPresets.size + 1}"
                            val preset = EqualizerPreset(
                                "user_${System.currentTimeMillis()}",
                                name,
                                profile,
                            )
                            setUserPresetsRaw(
                                EqualizerPresets.encodeCustom(userPresets + preset)
                            )
                        }
                    ) { Text("保存") }
                }
            )

            PreferenceGroupTitle(title = "自动均衡")
            SwitchPreference(
                title = { Text("按歌曲自动均衡") },
                description = "基于网易云音频分析自动生成曲线，仅播放时生效，可以随时手动覆盖",
                icon = { Icon(Icons.Rounded.Tune, null) },
                checked = autoEqEnabled,
                onCheckedChange = setAutoEqEnabled
            )
            if (autoEqEnabled) {
                PreferenceEntry(
                    title = { Text("自动均衡强度") },
                    description = "$autoEqIntensity%",
                    icon = { Icon(Icons.Rounded.GraphicEq, null) }
                )
                Slider(
                    value = autoEqIntensity.toFloat(),
                    onValueChange = { setAutoEqIntensity(it.roundToInt()) },
                    valueRange = 0f..100f,
                )
            }

            PreferenceGroupTitle(title = "混响")
            ListPreference(
                title = { Text("混响场景") },
                description = reverb?.let { r ->
                    ReverbPresets.all.firstOrNull { it.id == r.presetId }?.label ?: "自定义"
                } ?: "关闭",
                icon = { Icon(Icons.Rounded.SurroundSound, null) },
                selectedValue = reverb?.presetId ?: "off",
                values = ReverbPresets.all.map { it.id },
                valueText = { id -> ReverbPresets.all.firstOrNull { it.id == id }?.label ?: id },
                onValueSelected = { id ->
                    setReverbRaw(ReverbProfileCodec.encode(ReverbPresets.byId(id)))
                }
            )

            PreferenceGroupTitle(title = "输出")
            SwitchPreference(
                title = { Text("输出限制器") },
                description = "防止增益叠加后削波失真，建议开启",
                icon = { Icon(Icons.Rounded.SurroundSound, null) },
                checked = limiterEnabled,
                onCheckedChange = setLimiterEnabled
            )
        }
    }
}

@Composable
private fun FilterGainRow(
    filter: EqFilter,
    key: String,
    onGainChangeFinished: (Float) -> Unit,
) {
    var gain by remember(key) { mutableFloatStateOf(filter.gainDb) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = "${frequencyLabel(filter.frequencyHz)} · ${filter.type.label}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = "%+.1f dB".format(gain),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(end = 8.dp),
        )
        Slider(
            value = gain,
            onValueChange = { gain = it },
            onValueChangeFinished = { onGainChangeFinished((gain * 10).roundToInt() / 10f) },
            valueRange = -12f..12f,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun frequencyLabel(hz: Float): String =
    if (hz >= 1000f) "%.1f kHz".format(hz / 1000f) else "${hz.roundToInt()} Hz"
