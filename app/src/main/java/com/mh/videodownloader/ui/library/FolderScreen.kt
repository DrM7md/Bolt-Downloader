package com.mh.videodownloader.ui.library

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.videodownloader.R
import com.mh.videodownloader.data.db.HistoryEntry
import com.mh.videodownloader.ui.common.EmptyState
import com.mh.videodownloader.util.shareMedia
import com.mh.videodownloader.ui.theme.forContent

@Composable
fun FolderScreen(
    viewModel: FolderViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPlay: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val folder by viewModel.folder.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val allFolders by viewModel.folders.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDeleteFolder by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<HistoryEntry?>(null) }
    var deleting by remember { mutableStateOf<HistoryEntry?>(null) }

    DeleteConsentHandler(viewModel)

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
                    .padding(top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
                Text(
                    text = folder?.name.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall.forContent(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename)) },
                            leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                            onClick = {
                                menuOpen = false
                                renaming = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.delete_folder),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                confirmDeleteFolder = true
                            },
                        )
                    }
                }
            }
        }

        if (items.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.FolderOpen,
                    title = stringResource(R.string.folder_empty),
                    subtitle = stringResource(R.string.folder_empty_sub),
                    modifier = Modifier.padding(top = 40.dp),
                )
            }
        }

        items(items, key = { it.id }) { entry ->
            MediaEntryCard(
                entry = entry,
                onPlay = { onPlay(entry) },
                onShare = {
                    if (!shareMedia(context, entry.fileUri, entry.mimeType, entry.title)) {
                        Toast.makeText(context, R.string.cannot_share, Toast.LENGTH_SHORT).show()
                    }
                },
                onMove = { moving = entry },
                onDelete = { deleting = entry },
            )
        }
    }

    if (renaming) {
        FolderNameDialog(
            title = stringResource(R.string.rename),
            initial = folder?.name.orEmpty(),
            confirmLabel = stringResource(R.string.save),
            onConfirm = {
                viewModel.rename(it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }

    if (confirmDeleteFolder) {
        AlertDialog(
            onDismissRequest = { confirmDeleteFolder = false },
            title = { Text(stringResource(R.string.delete_folder)) },
            text = { Text(stringResource(R.string.delete_folder_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteFolder = false
                    viewModel.deleteFolder()
                    onBack()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFolder = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    moving?.let { entry ->
        MoveToFolderDialog(
            entry = entry,
            folders = allFolders,
            onMove = { folderId ->
                viewModel.move(entry, folderId)
                moving = null
            },
            onNewFolder = { name ->
                viewModel.moveToNewFolder(entry, name)
                moving = null
            },
            onDismiss = { moving = null },
        )
    }

    deleting?.let { entry ->
        DeleteEntryDialog(
            entry = entry,
            onConfirm = { deleteFile ->
                viewModel.delete(entry, deleteFile)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}
