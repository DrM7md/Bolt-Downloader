package com.mh.videodownloader.ui.library

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mh.videodownloader.R
import com.mh.videodownloader.data.db.Folder
import com.mh.videodownloader.data.db.HistoryEntry
import com.mh.videodownloader.ui.common.AppCard
import com.mh.videodownloader.ui.common.Chip
import com.mh.videodownloader.util.formatBytes
import com.mh.videodownloader.util.formatDate
import com.mh.videodownloader.util.formatDuration
import com.mh.videodownloader.util.ltr
import com.mh.videodownloader.ui.theme.forContent

/** One downloaded file, as listed in History, the library and inside a folder. */
@Composable
fun MediaEntryCard(
    entry: HistoryEntry,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onMove: (() -> Unit)? = null,
) {
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumbnail(entry)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall.forContent(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // A long title plus three chips overflows a narrow screen, so they wrap
                    // onto a second line rather than squeezing the last one.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Chip(text = entry.sourceLabel)
                        formatBytes(entry.sizeBytes)?.let {
                            Chip(
                                text = ltr(it),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                        if (entry.durationSec > 0) {
                            Chip(
                                text = ltr(formatDuration(entry.durationSec)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // The actions take the full card width rather than sharing the text column: beside
            // the thumbnail, four buttons and the date do not fit on a 360dp screen.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ltr(formatDate(entry.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                CardAction(
                    icon = Icons.Rounded.PlayArrow,
                    description = stringResource(R.string.open),
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onPlay,
                )
                CardAction(
                    icon = Icons.Rounded.Share,
                    description = stringResource(R.string.share),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onShare,
                )
                if (onMove != null) {
                    CardAction(
                        icon = Icons.Rounded.DriveFileMove,
                        description = stringResource(R.string.move_to_folder),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onMove,
                    )
                }
                CardAction(
                    icon = Icons.Rounded.DeleteOutline,
                    description = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(entry: HistoryEntry) {
    Box(
        modifier = Modifier
            .size(width = 92.dp, height = 62.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (entry.thumbnail != null) {
            AsyncImage(
                model = entry.thumbnail,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
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
                if (entry.isAudio) Icons.Rounded.MusicNote else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CardAction(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(19.dp))
    }
}

/** Remove from the list, and only touch the real file when the user ticks the box. */
@Composable
fun DeleteEntryDialog(
    entry: HistoryEntry,
    onConfirm: (deleteFile: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var deleteFile by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_from_history)) },
        text = {
            Column {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyMedium.forContent(),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deleteFile, onCheckedChange = { deleteFile = it })
                    Text(
                        text = stringResource(R.string.delete_file_too),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteFile) }) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun FolderNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun MoveToFolderDialog(
    entry: HistoryEntry,
    folders: List<Folder>,
    onMove: (Long?) -> Unit,
    onNewFolder: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        FolderNameDialog(
            title = stringResource(R.string.new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onConfirm = { onNewFolder(it) },
            onDismiss = { creating = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_folder)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FolderChoice(
                    icon = Icons.Rounded.CreateNewFolder,
                    label = stringResource(R.string.new_folder),
                    selected = false,
                    accent = true,
                    onClick = { creating = true },
                )
                FolderChoice(
                    icon = Icons.Rounded.FolderOff,
                    label = stringResource(R.string.no_folder),
                    selected = entry.folderId == null,
                    onClick = { onMove(null) },
                )
                folders.forEach { folder ->
                    FolderChoice(
                        icon = if (folder.isPlaylist) Icons.AutoMirrored.Rounded.PlaylistPlay
                        else Icons.Rounded.Folder,
                        label = folder.name,
                        selected = entry.folderId == folder.id,
                        onClick = { onMove(folder.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun FolderChoice(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (accent || selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (accent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Launches the system "allow deleting this file?" prompt when the repository asks for one, and
 * reports the answer back. Lives next to any list that can delete files.
 */
@Composable
fun DeleteConsentHandler(viewModel: EntryActionsViewModel) {
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> viewModel.onConsentResult(result.resultCode == Activity.RESULT_OK) }

    LaunchedEffect(consent) {
        consent?.let { launcher.launch(IntentSenderRequest.Builder(it.request).build()) }
    }
}
