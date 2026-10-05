package com.mh.videodownloader.ui.history

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import com.mh.videodownloader.ui.library.DeleteEntryDialog
import com.mh.videodownloader.ui.library.MediaEntryCard
import com.mh.videodownloader.ui.library.MoveToFolderDialog
import com.mh.videodownloader.util.shareMedia

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    contentPadding: PaddingValues,
    onPlay: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var moving by remember { mutableStateOf<HistoryEntry?>(null) }
    var deleting by remember { mutableStateOf<HistoryEntry?>(null) }

    DeleteConsentHandler(viewModel)

    Column(modifier = modifier.fillMaxWidth()) {
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

        if (entries.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.History,
                title = stringResource(R.string.history_empty_title),
                subtitle = stringResource(R.string.history_empty_sub),
                modifier = Modifier.padding(top = 48.dp),
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
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
    }

    moving?.let { entry ->
        MoveToFolderDialog(
            entry = entry,
            folders = folders,
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
