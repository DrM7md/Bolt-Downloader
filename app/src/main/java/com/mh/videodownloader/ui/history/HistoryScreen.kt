package com.mh.videodownloader.ui.history

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.videodownloader.R
import com.mh.videodownloader.data.db.HistoryEntry
import com.mh.videodownloader.ui.common.EmptyState
import com.mh.videodownloader.ui.library.DeleteConsentHandler
import com.mh.videodownloader.ui.library.DeleteEntriesDialog
import com.mh.videodownloader.ui.library.FolderDropTray
import com.mh.videodownloader.ui.library.MediaEntryCard
import com.mh.videodownloader.ui.library.MoveToFolderDialog
import com.mh.videodownloader.ui.library.SelectionBar
import com.mh.videodownloader.ui.library.rememberEntrySelection
import com.mh.videodownloader.util.shareMedia

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    contentPadding: PaddingValues,
    onPlay: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded by viewModel.entries.collectAsStateWithLifecycle()
    val entries = loaded.orEmpty()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val selection = rememberEntrySelection()
    var moving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<List<HistoryEntry>?>(null) }

    DeleteConsentHandler(viewModel)
    BackHandler(enabled = selection.active) { selection.clear() }

    val moveSelection: (List<Long>, Long?) -> Unit = { ids, folderId ->
        viewModel.move(ids, folderId)
        selection.clear()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (entries.isNotEmpty()) {
                    TextButton(onClick = viewModel::clearAll) {
                        Text(stringResource(R.string.clear_history))
                    }
                }
            }

            if (loaded != null && entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.History,
                    title = stringResource(R.string.history_empty_title),
                    subtitle = stringResource(R.string.history_empty_sub),
                    modifier = Modifier.padding(top = 48.dp),
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = contentPadding.calculateBottomPadding() +
                        if (selection.active) 140.dp else 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    MediaEntryCard(
                        entry = entry,
                        selection = selection,
                        onPlay = { onPlay(entry) },
                        onShare = {
                            if (!shareMedia(context, entry.fileUri, entry.mimeType, entry.title)) {
                                Toast.makeText(context, R.string.cannot_share, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = { deleting = listOf(entry) },
                    )
                }
            }
        }

        if (selection.active) {
            SelectionBar(
                count = selection.ids.size,
                onClose = selection::clear,
                onSelectAll = { selection.selectAll(entries.map { it.id }) },
                onMove = { moving = true },
                onDelete = { deleting = entries.filter { it.id in selection.ids } },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            FolderDropTray(
                folders = folders,
                onMove = moveSelection,
                onTap = { folderId -> moveSelection(selection.ids.toList(), folderId) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = contentPadding.calculateBottomPadding() + 8.dp)
                    .padding(horizontal = 8.dp),
            )
        }
    }

    if (moving) {
        val ids = selection.ids
        MoveToFolderDialog(
            currentFolders = entries.filter { it.id in ids }.map { it.folderId }.toSet(),
            folders = folders,
            onMove = { folderId ->
                moveSelection(ids.toList(), folderId)
                moving = false
            },
            onNewFolder = { name ->
                viewModel.moveToNewFolder(ids, name)
                selection.clear()
                moving = false
            },
            onDismiss = { moving = false },
        )
    }

    deleting?.let { list ->
        DeleteEntriesDialog(
            entries = list,
            onConfirm = { deleteFile ->
                viewModel.delete(list, deleteFile)
                selection.clear()
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}
