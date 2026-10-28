package com.ljyh.mei.ui.screen.playlist.component

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ljyh.mei.data.model.MediaMetadata
import com.ljyh.mei.data.model.room.Playlist
import com.ljyh.mei.ui.component.player.OverlayState
import com.ljyh.mei.ui.component.playlist.AddToPlaylistSheet
import com.ljyh.mei.ui.component.playlist.CreatePlaylistSheet
import com.ljyh.mei.ui.component.playlist.TrackActionMenu
import com.ljyh.mei.ui.model.UiPlaylist
import com.ljyh.mei.ui.screen.playlist.PlaylistViewModel
import com.ljyh.mei.ui.screen.playlist.PlaylistTrackAddOutcome
import com.ljyh.mei.utils.setClipboard


@Composable
fun PlaylistActionOverlay(
    overlay: OverlayState,
    isCreator: Boolean,
    playlistId: Long, // 当前歌单 ID，用于删除歌曲
    allMePlaylist: List<Playlist>, // 用户拥有的歌单列表
    onDismiss: () -> Unit,
    onUpdateOverlay: (OverlayState) -> Unit, // 用于切换状态（如从菜单跳到收藏页）
    onDownloadTrack: ((MediaMetadata) -> Unit)? = null,
    viewModel: PlaylistViewModel
) {
    val context = LocalContext.current
    var pendingAddMediaId by remember { mutableStateOf<Long?>(null) }
    var isCreatingPlaylist by remember { mutableStateOf(false) }

    when (overlay) {
        is OverlayState.AddToPlaylist -> {
            AddToPlaylistSheet(
                playlists = allMePlaylist,
                onDismiss = onDismiss,
                onCreateNewPlaylist = {
                    pendingAddMediaId = overlay.mediaId
                    onUpdateOverlay(OverlayState.CreatePlaylist)
                },
                onSelectPlaylist = { selectedPlaylist ->
                    viewModel.addSongToPlaylist(
                        pid = selectedPlaylist.id,
                        trackIds = overlay.mediaId.toString(),
                        previousTrackCount = selectedPlaylist.count
                    ) { outcome ->
                        val message = when (outcome) {
                            PlaylistTrackAddOutcome.Added -> "已添加到 ${selectedPlaylist.title}"
                            PlaylistTrackAddOutcome.AlreadyExists -> "歌曲已在 ${selectedPlaylist.title} 中"
                            PlaylistTrackAddOutcome.Failed -> "添加到歌单失败"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }
            )
        }

        OverlayState.CreatePlaylist -> {
            CreatePlaylistSheet(
                onDismiss = {
                    val mediaId = pendingAddMediaId
                    if (mediaId != null) {
                        onUpdateOverlay(OverlayState.AddToPlaylist(mediaId))
                    } else {
                        onDismiss()
                    }
                },
                onConfirm = { name, privacy ->
                    if (!isCreatingPlaylist) {
                        isCreatingPlaylist = true
                        viewModel.createPlaylist(name, privacy) { result ->
                            isCreatingPlaylist = false
                            when (result) {
                                is com.ljyh.mei.data.network.Resource.Success -> {
                                    if (result.data.code == 200) {
                                        Toast.makeText(context, "歌单“$name”已创建", Toast.LENGTH_SHORT).show()
                                        val mediaId = pendingAddMediaId
                                        if (mediaId != null) {
                                            onUpdateOverlay(OverlayState.AddToPlaylist(mediaId))
                                        } else {
                                            onDismiss()
                                        }
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "创建歌单失败（${result.data.code}）",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                is com.ljyh.mei.data.network.Resource.Error -> Toast.makeText(
                                    context,
                                    "创建歌单失败：${result.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                com.ljyh.mei.data.network.Resource.Loading -> Unit
                            }
                        }
                    }
                },
                isSubmitting = isCreatingPlaylist
            )
        }

        is OverlayState.TrackActionMenu -> {
            TrackActionMenu(
                targetTrack = overlay.track,
                isCreator = isCreator,
                onDismiss = onDismiss,
                onAddToPlaylist = {
                    viewModel.getAllMePlaylist()
                    onUpdateOverlay(OverlayState.AddToPlaylist(overlay.track.id))
                },
                onDownloadTrack = onDownloadTrack?.let { { it(overlay.track) } },
                onDelete = {
                    viewModel.deleteSongFromPlaylist(
                        playlistId.toString(),
                        overlay.track.id.toString()
                    ) { deleted ->
                        if (deleted) {
                            viewModel.markTrackRemoved(overlay.track.id)
                            Toast.makeText(context, "已从歌单删除", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "从歌单删除失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                    onDismiss()
                },
                onCopyId = {
                    setClipboard(context, overlay.track.id.toString(), "id")
                    onDismiss()
                },
                onCopyName = {
                    setClipboard(context, overlay.track.title, "name")
                    onDismiss()
                }
            )
        }

        else -> {}
    }
}
