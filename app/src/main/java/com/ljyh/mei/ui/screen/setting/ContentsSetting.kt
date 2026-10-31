package com.ljyh.mei.ui.screen.setting

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ljyh.mei.constants.CookieKey
import com.ljyh.mei.constants.QqCookieKey
import com.ljyh.mei.utils.preferences.AudioSourceMode
import com.ljyh.mei.utils.preferences.AudioSourceModeKey
import com.ljyh.mei.constants.QqTimeout
import com.ljyh.mei.constants.QqTimeoutKey
import com.ljyh.mei.constants.DesktopLyricsEnabledKey
import com.ljyh.mei.constants.DesktopLyricsLockedKey
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.ui.ShareViewModel
import com.ljyh.mei.ui.component.EditTextPreference
import com.ljyh.mei.ui.component.IconButton
import com.ljyh.mei.ui.component.ListPreference
import com.ljyh.mei.ui.component.PreferenceEntry
import com.ljyh.mei.ui.component.PreferenceGroupTitle
import com.ljyh.mei.ui.component.SwitchPreference
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.ui.screen.backToMain
import androidx.core.net.toUri
import com.ljyh.mei.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentsSetting(
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ShareViewModel = hiltViewModel()
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val (cookie, onCookie) = rememberPreference(
        CookieKey,
        defaultValue = ""
    )
    var userName by remember { mutableStateOf("") }
    val userAccount by viewModel.userAccount.collectAsState()

    userName = when (val result = userAccount) {
        is Resource.Success -> {
            if(result.data.code == 200 && result.data.profile != null){
                Toast.makeText(context, "看上去还不错哦", Toast.LENGTH_SHORT).show()
                result.data.profile.nickname
            }else{
                Toast.makeText(context, "cookie 可能存在错误", Toast.LENGTH_SHORT).show()
                "error"
            }
        }

        is Resource.Error -> "error"
        Resource.Loading -> "~~~"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("内容设置") },
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
                title = "MUSIC"
            )

            EditTextPreference(
                title = { Text("网易云Cookie: MUSIC_U") },
                icon = { Icon(Icons.Rounded.Cookie, "网易云Cookie: MUSIC_U") },
                value = cookie,
                onValueChange = onCookie
            )

            PreferenceEntry(
                title = { Text("测试Cookie") },
                description = userName,
                icon = { Icon(Icons.Rounded.TipsAndUpdates, "测试 Cookie") },
                onClick = {
                    if (cookie == "") {
                        Toast.makeText(context, "还没有填写cookie", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.getUserAccount()
                    }
                }
            )

            val (qqTimeout, onQqTimeoutChange) = rememberPreference(
                QqTimeoutKey,
                defaultValue = QqTimeout.Sec8.name
            )
            val currentTimeout = try { QqTimeout.valueOf(qqTimeout) } catch (_: Exception) { QqTimeout.Sec8 }

            ListPreference(
                title = { Text("QQ 超时") },
                description = null,
                icon = { Icon(Icons.Rounded.TipsAndUpdates, "QQ 超时") },
                selectedValue = currentTimeout,
                values = QqTimeout.entries.toList(),
                valueText = { it.label },
                onValueSelected = { onQqTimeoutChange(it.name) }
            )

            val (qqCookie, onQqCookieChange) = rememberPreference(
                QqCookieKey,
                defaultValue = ""
            )
            EditTextPreference(
                title = { Text("QQ 音乐 Cookie") },
                icon = { Icon(Icons.Rounded.Cookie, contentDescription = null) },
                value = qqCookie,
                onValueChange = { onQqCookieChange(it.trim()) },
                singleLine = false,
                isInputValid = { true },
            )

            PreferenceGroupTitle(title = "备用音源")
            val (audioSourceModeStr, onAudioSourceModeChange) = rememberPreference(AudioSourceModeKey, defaultValue = AudioSourceMode.Lx.name)
            val selectedMode = runCatching { AudioSourceMode.valueOf(audioSourceModeStr) }
                .getOrDefault(AudioSourceMode.Lx)
            ListPreference(
                title = { Text("备用音源模式") },
                description = "LX 与多平台解灰互斥，避免同一次播放重复请求",
                icon = { Icon(Icons.Rounded.CloudSync, contentDescription = null) },
                selectedValue = selectedMode,
                values = AudioSourceMode.entries.toList(),
                valueText = { it.label },
                onValueSelected = { onAudioSourceModeChange(it.name) },
            )
            if (selectedMode == AudioSourceMode.Lx) {
                PreferenceEntry(
                    title = { Text("LX 自定义音源") },
                    description = "导入、排序与检查 JS 音源脚本",
                    icon = { Icon(Icons.Rounded.CloudSync, contentDescription = null) },
                    onClick = { Screen.LxSources.navigate(navController) },
                )
            }

            val (desktopLyricsEnabled, onDesktopLyricsEnabled) = rememberPreference(
                DesktopLyricsEnabledKey,
                defaultValue = false,
            )
            val (desktopLyricsLocked, onDesktopLyricsLockedChange) = rememberPreference(
                DesktopLyricsLockedKey,
                defaultValue = false,
            )

            PreferenceGroupTitle(title = "桌面歌词")
            var waitingForOverlayPermission by remember { mutableStateOf(false) }
            val overlayPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                if (Settings.canDrawOverlays(context) && waitingForOverlayPermission) {
                    onDesktopLyricsEnabled(true)
                }
                waitingForOverlayPermission = false
            }
            val overlayPermissionGranted = Settings.canDrawOverlays(context)
            SwitchPreference(
                title = { Text("桌面歌词") },
                description = if (overlayPermissionGranted) "在其他应用上层显示卡拉OK歌词，可在播放器内拖动与锁定"
                else "开启需授予悬浮窗权限",
                icon = { Icon(Icons.Rounded.Lyrics, contentDescription = null) },
                checked = desktopLyricsEnabled && overlayPermissionGranted,
                onCheckedChange = { checked ->
                    if (!checked) onDesktopLyricsEnabled(false)
                    else if (overlayPermissionGranted) onDesktopLyricsEnabled(true)
                    else {
                        waitingForOverlayPermission = true
                        overlayPermissionLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                "package:${context.packageName}".toUri(),
                            )
                        )
                    }
                },
            )
            SwitchPreference(
                title = { Text("启动时锁定") },
                description = "锁定后只显示歌词并穿透触摸，可在悬浮窗上解除锁定",
                icon = { Icon(Icons.Rounded.Lyrics, contentDescription = null) },
                checked = desktopLyricsLocked,
                onCheckedChange = onDesktopLyricsLockedChange,
                isEnabled = desktopLyricsEnabled,
            )
        }
    }
}
