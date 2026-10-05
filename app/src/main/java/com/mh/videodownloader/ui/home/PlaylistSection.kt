package com.mh.videodownloader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mh.videodownloader.R
import com.mh.videodownloader.domain.PlaylistEntry
import com.mh.videodownloader.ui.common.AppCard
import com.mh.videodownloader.ui.common.Chip
import com.mh.videodownloader.ui.common.GradientButton
import com.mh.videodownloader.ui.common.SectionTitle
import com.mh.videodownloader.util.formatDuration
import com.mh.videodownloader.util.ltr

/**
 * The summary and controls for a playlist link. Entries themselves are emitted by the caller as
 * separate lazy items, because a playlist can run to hundreds of videos.
 */
@Composable
fun PlaylistHeader(
    state: HomeUiState.Playlist,
    onQuality: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onDownloadVideo: () -> Unit,
    onDownloadAudio: () -> Unit,
    onSingleVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = state.info
    val sourceLabel = info.source.brandName
        ?: stringResource(info.source.labelRes ?: R.string.source_other)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = info.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = info.uploader?.let { "$sourceLabel · $it" } ?: sourceLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(
                        text = stringResource(R.string.playlist_count, info.entries.size),
                        icon = Icons.Rounded.VideoLibrary,
                    )
                    Spacer(Modifier.weight(1f))
                    val allSelected = state.selected.size == info.entries.size
                    TextButton(onClick = { onSelectAll(!allSelected) }) {
                        Text(
                            stringResource(
                                if (allSelected) R.string.clear_selection else R.string.select_all
                            )
                        )
                    }
                }

                if (info.singleVideoUrl != null) {
                    TextButton(onClick = onSingleVideo, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.single_video_only))
                    }
                }
            }
        }

        Column {
            SectionTitle(stringResource(R.string.quality_cap))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PLAYLIST_QUALITY_CAPS.forEach { cap ->
                    FilterChip(
                        selected = state.qualityCap == cap,
                        onClick = { onQuality(cap) },
                        label = {
                            Text(
                                if (cap == BEST_QUALITY_CAP) stringResource(R.string.quality_best)
                                else ltr(cap.toString() + "p")
                            )
                        },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }

        GradientButton(
            text = stringResource(R.string.download_selected, state.selected.size),
            icon = Icons.Rounded.Download,
            onClick = onDownloadVideo,
            enabled = state.selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = onDownloadAudio,
            enabled = state.selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = CircleShape,
        ) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.download_selected_mp3),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
fun PlaylistRow(
    entry: PlaylistEntry,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(width = 62.dp, height = 40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                if (entry.thumbnail != null) {
                    AsyncImage(
                        model = entry.thumbnail,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (entry.durationSec > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = ltr(formatDuration(entry.durationSec)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
