package com.ljyh.mei.ui.component.item

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ljyh.mei.constants.CommonImageRadius
import com.ljyh.mei.constants.PlaylistThumbnailSize
import com.ljyh.mei.data.model.room.Playlist
import com.ljyh.mei.utils.smallImage

@Composable
fun PlaylistItem(
    playlist: Playlist,
    onDelete: ((String) -> Unit)? = null,
    onClick: (String) -> Unit
) {
    var offset by remember { mutableFloatStateOf(0f) }
    val revealWidth = with(LocalDensity.current) { 96.dp.toPx() }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))) {
        if (onDelete != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color(0xFFD32F2F)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier.width(96.dp).fillMaxHeight().background(Color(0xFFB71C1C)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = { onDelete(playlist.id); offset = 0f },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFFB71C1C),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(Icons.Rounded.Delete, "删除歌单", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
        Row(
        modifier = Modifier
            .offset { IntOffset(offset.roundToInt(), 0) }
            .fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .pointerInput(playlist.id, onDelete) {
                if (onDelete != null) detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount -> change.consume(); offset = (offset + amount).coerceIn(-revealWidth, 0f) },
                    onDragEnd = { if (offset > -revealWidth / 2) offset = 0f else offset = -revealWidth },
                    onDragCancel = { offset = 0f },
                )
            }
            .then(if (onDelete != null) Modifier.combinedClickable(
                onClick = { if (offset < 0f) offset = 0f else onClick(playlist.id) },
                onLongClick = { onDelete(playlist.id) },
            ) else Modifier.combinedClickable(onClick = { onClick(playlist.id) })),
        verticalAlignment = Alignment.CenterVertically,

        ) {
        AsyncImage(
            model = playlist.cover.smallImage(),
            contentDescription = null,
            modifier = Modifier
                .size(PlaylistThumbnailSize)
                .clip(RoundedCornerShape(CommonImageRadius))
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                text = playlist.title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.count} • ${playlist.authorName}",
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        }
    }
}
