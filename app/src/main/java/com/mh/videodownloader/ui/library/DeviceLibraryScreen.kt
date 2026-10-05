package com.mh.videodownloader.ui.library

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFramePercent
import com.mh.videodownloader.R
import com.mh.videodownloader.data.DeviceItem
import com.mh.videodownloader.ui.common.AppCard
import com.mh.videodownloader.ui.common.EmptyState
import com.mh.videodownloader.ui.theme.forContent
import com.mh.videodownloader.util.formatBytes
import com.mh.videodownloader.util.formatDate
import com.mh.videodownloader.util.ltr
import com.mh.videodownloader.util.shareMedia

/** The library when its source is a folder picked from the device. */
@Composable
fun DeviceLibraryScreen(
    viewModel: DeviceLibraryViewModel,
    contentPadding: PaddingValues,
    onPlay: (uri: String, title: String, mimeType: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<DeviceItem?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val ready = state as? DeviceLibraryState.Ready
    // Back walks up the folder tree before it leaves the screen.
    BackHandler(enabled = (ready?.path?.size ?: 0) > 1) { viewModel.up() }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if ((ready?.path?.size ?: 0) > 1) {
                    IconButton(onClick = { viewModel.up() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                } else {
                    Spacer(Modifier.width(4.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.library_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    ready?.path?.let { path ->
                        Text(
                            text = path.joinToString(" / ") { it.name },
                            style = MaterialTheme.typography.bodySmall.forContent(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        when (val current = state) {
            DeviceLibraryState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            DeviceLibraryState.NoFolder -> item {
                EmptyState(
                    icon = Icons.Rounded.Folder,
                    title = stringResource(R.string.library_folder_none),
                    subtitle = stringResource(R.string.library_source_device_desc),
                    modifier = Modifier.padding(top = 40.dp),
                )
            }

            DeviceLibraryState.Unreadable -> item {
                EmptyState(
                    icon = Icons.Rounded.FolderOff,
                    title = stringResource(R.string.cannot_open_file),
                    subtitle = stringResource(R.string.folder_unreadable),
                    modifier = Modifier.padding(top = 40.dp),
                )
            }

            is DeviceLibraryState.Ready -> {
                if (current.items.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.VideoLibrary,
                            title = stringResource(R.string.folder_empty),
                            subtitle = stringResource(R.string.library_source_device_desc),
                            modifier = Modifier.padding(top = 40.dp),
                        )
                    }
                }
                items(current.items, key = { it.documentId }) { item ->
                    if (item.isFolder) {
                        DeviceFolderRow(item, onClick = { viewModel.open(item) })
                    } else {
                        DeviceFileCard(
                            item = item,
                            onPlay = { onPlay(item.uri.toString(), item.name, item.mimeType) },
                            onShare = {
                                if (!shareMedia(context, item.uri.toString(), item.mimeType, item.name)) {
                                    Toast.makeText(context, R.string.cannot_share, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDelete = { deleting = item },
                        )
                    }
                }
            }
        }
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_file_title)) },
            text = {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium.forContent(),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item)
                    deleting = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun DeviceFolderRow(item: DeviceItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall.forContent(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DeviceFileCard(
    item: DeviceItem,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 92.dp, height = 62.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!item.isAudio) {
                        // Coil's video decoder pulls a frame straight from the file — from 10%
                        // in, because the very first frame is so often a fade from black.
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.uri)
                                .videoFramePercent(0.1)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (item.isAudio) Icons.Rounded.MusicNote else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = item.name.substringBeforeLast('.'),
                    style = MaterialTheme.typography.titleSmall.forContent(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = listOfNotNull(
                        formatBytes(item.sizeBytes)?.let(::ltr),
                        item.modifiedAt.takeIf { it > 0 }?.let { ltr(formatDate(it)) },
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onPlay, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.open),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp),
                    )
                }
                IconButton(onClick = onShare, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.share),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}
