package com.ljyh.mei.ui.screen.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.HideSource
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ljyh.mei.constants.LoopPlaybackKey
import com.ljyh.mei.constants.SmartTransitionDurationKey
import com.ljyh.mei.constants.SmartTransitionEnabledKey
import com.ljyh.mei.constants.SmartTransitionMode
import com.ljyh.mei.constants.SmartTransitionModeKey
import com.ljyh.mei.constants.MusicQuality
import com.ljyh.mei.constants.MusicQualityKey
import com.ljyh.mei.constants.NoAudioSourceKey
import com.ljyh.mei.ui.component.EnumListPreference
import com.ljyh.mei.ui.component.ListPreference
import com.ljyh.mei.ui.component.IconButton
import com.ljyh.mei.ui.component.PreferenceEntry
import com.ljyh.mei.ui.component.PreferenceGroupTitle
import com.ljyh.mei.ui.component.SwitchPreference
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.ui.screen.backToMain
import com.ljyh.mei.utils.rememberEnumPreference
import com.ljyh.mei.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaySetting(
    scrollBehavior: TopAppBarScrollBehavior
){

    val navController = LocalNavController.current

    val (musicQuality, onMusicQualityChange) = rememberEnumPreference(
        key = MusicQualityKey,
        defaultValue = MusicQuality.EXHIGH,
    )
    val (loopPlayback, onLoopPlaybackChange) = rememberPreference(
        key = LoopPlaybackKey,
        defaultValue = true
    )

    val (noAudioSource, onNoAudioSourceChange) = rememberPreference(
        NoAudioSourceKey,
        defaultValue = false
    )


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放设置") },
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
            PreferenceGroupTitle(
                title = "PLAY"
            )
            SwitchPreference(
                title = { Text("循环播放") },
                icon = { Icon(Icons.Rounded.Loop, null) },
                checked = loopPlayback,
                onCheckedChange = onLoopPlaybackChange
            )
            SwitchPreference(
                title = { Text("无音频源时下一首歌曲") },
                description = "无音频源时下一首歌曲，否则暂停",
                icon = { Icon(Icons.Rounded.HideSource, null) },
                checked = noAudioSource,
                onCheckedChange = onNoAudioSourceChange
            )
            EnumListPreference(
                title = { Text("音乐质量") },
                icon = { Icon(Icons.Rounded.HighQuality, null) },
                selectedValue = musicQuality,
                onValueSelected = onMusicQualityChange,
                valueText = { "${it.text} ${it.explanation}" }
            )

            PreferenceEntry(
                title = { Text("音效") },
                description = "均衡器、自动均衡、混响与输出限制器",
                icon = { Icon(Icons.Rounded.Equalizer, null) },
                onClick = {
                    Screen.EqualizerSettings.navigate(navController)
                }
            )

            PreferenceEntry(
                title = { Text("USB 独占播放") },
                description = "直连 USB 解码器进行位完美输出",
                icon = { Icon(Icons.Rounded.Usb, null) },
                onClick = {
                    Screen.UsbExclusiveSettings.navigate(navController)
                }
            )

            PreferenceGroupTitle(title = "TRANSITION")
            val (transitionEnabled, onSmartTransitionEnabledChange) = rememberPreference(SmartTransitionEnabledKey, defaultValue = false)
            val (transitionModeStr, onSmartTransitionModeChange) = rememberPreference(SmartTransitionModeKey, defaultValue = SmartTransitionMode.Smart.name)
            val (transitionSeconds, onSmartTransitionSecondsChange) = rememberPreference(SmartTransitionDurationKey, defaultValue = 6)
            val transitionMode = runCatching { SmartTransitionMode.valueOf(transitionModeStr) }
                .getOrDefault(SmartTransitionMode.Smart)
            SwitchPreference(
                title = { Text("歌曲过渡") },
                description = "仅在节奏匹配时衔接；同专辑与不匹配的歌曲自然结束",
                icon = { Icon(Icons.Rounded.GraphicEq, null) },
                checked = transitionEnabled,
                onCheckedChange = onSmartTransitionEnabledChange,
            )
            if (transitionEnabled) {
                EnumListPreference(
                    title = { Text("过渡方式") },
                    icon = { Icon(Icons.Rounded.GraphicEq, null) },
                    selectedValue = transitionMode,
                    valueText = { if (it == SmartTransitionMode.Smart) "智能过渡" else "固定时长淡化" },
                    onValueSelected = { onSmartTransitionModeChange(it.name) },
                )
                if (transitionMode == SmartTransitionMode.Fixed) {
                    ListPreference(
                        title = { Text("淡化时长") },
                        selectedValue = transitionSeconds,
                        values = listOf(4, 6, 8, 10),
                        valueText = { "$it 秒" },
                        onValueSelected = { onSmartTransitionSecondsChange(it) },
                    )
                }
            }
        }
    }
}
