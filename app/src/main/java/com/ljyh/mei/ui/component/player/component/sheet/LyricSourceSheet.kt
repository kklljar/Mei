package com.ljyh.mei.ui.component.player.component.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ljyh.mei.ui.model.LyricSelectionState
import com.ljyh.mei.ui.model.LyricVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricSourceSheet(
    state: LyricSelectionState,
    onDismiss: () -> Unit,
    onSelect: (LyricVariant) -> Unit,
    onFindQqLyrics: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("歌词版本", style = MaterialTheme.typography.titleLarge)
            Text(
                "只影响当前歌曲，选择结果会自动保存。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LyricVariantRow(
                title = LyricVariant.Auto.label,
                subtitle = state.active?.let { "当前使用${it.label}" } ?: "按歌词质量自动选择",
                selected = state.preferenceLoaded && state.requested == LyricVariant.Auto,
                enabled = state.preferenceLoaded,
                onClick = { onSelect(LyricVariant.Auto) },
            )
            state.candidates.forEach { candidate ->
                LyricVariantRow(
                    title = candidate.variant.label,
                    subtitle = candidate.preview.ifBlank { "已获取歌词" },
                    selected = state.requested == candidate.variant,
                    onClick = { onSelect(candidate.variant) },
                )
            }
            if (state.candidates.isEmpty()) {
                Text(
                    if (state.isLoading) "正在获取可用版本…" else "暂时没有可用的歌词版本",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            TextButton(onClick = onFindQqLyrics, modifier = Modifier.align(Alignment.End)) {
                Text("手动匹配 QQ 歌词")
            }
        }
    }
}

@Composable
private fun LyricVariantRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(14.dp),
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null)
    }
}
