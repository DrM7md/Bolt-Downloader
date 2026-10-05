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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mh.videodownloader.R
import com.mh.videodownloader.data.db.FolderSummary
import com.mh.videodownloader.data.db.HistoryEntry
import com.mh.videodownloader.ui.common.EmptyState
import com.mh.videodownloader.ui.common.SectionTitle
import com.mh.videodownloader.ui.theme.brandGradient
import com.mh.videodownloader.ui.theme.forContent
import com.mh.videodownloader.util.shareMedia

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    contentPadding: PaddingValues,
    onOpenFolder: (Long) -> Unit,
    onPlay: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadedFolders by viewModel.folderSummaries.collectAsStateWithLifecycle()
    val loadedUnsorted by viewModel.unsorted.collectAsStateWithLifecycle()
    val folders = loadedFolders.orEmpty()
    val unsorted = loadedUnsorted.orEmpty()
    val loaded = loadedFolders != null && loadedUnsorted != null
    val allFolders by viewModel.folders.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val selection = rememberEntrySelection()
    var creating by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<List<HistoryEntry>?>(null) }

    DeleteConsentHandler(viewModel)
    BackHandler(enabled = selection.active) { selection.clear() }

    val moveSelection: (List<Long>, Long?) -> Unit = { ids, folderId ->
        viewModel.move(ids, folderId)
        selection.clear()
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                // Room for the drop tray, so the last card can still be reached while selecting.
                bottom = contentPadding.calculateBottomPadding() + if (selection.active) 140.dp else 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.library_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = { creating = true }) {
                        Icon(
                            Icons.Rounded.CreateNewFolder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.new_folder))
                    }
                }
            }

            if (loaded && folders.isEmpty() && unsorted.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.VideoLibrary,
                        title = stringResource(R.string.library_empty_title),
                        subtitle = stringResource(R.string.library_empty_sub),
                        modifier = Modifier.padding(top = 40.dp),
                    )
                }
            }

            if (folders.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.folders)) }
                // Two cards per row, laid out by hand: a grid nested inside a LazyColumn would
                // fight it for the scroll. Keys are namespaced: folder and entry ids are both
                // small integers from separate tables and would otherwise collide.
                items(folders.chunked(2), key = { row -> "folder-" + row.first().folder.id }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { summary ->
                            FolderCard(
                                summary = summary,
                                onClick = { onOpenFolder(summary.folder.id) },
                                onDrop = moveSelection,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (unsorted.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        SectionTitle(text = stringResource(R.string.unsorted) + "  ·  " + unsorted.size)
                        if (!selection.active) {
                            Text(
                                text = stringResource(R.string.hold_to_select),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                            )
                        }
                    }
                }
                items(unsorted, key = { "entry-" + it.id }) { entry ->
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
                onSelectAll = { selection.selectAll(unsorted.map { it.id }) },
                onMove = { moving = true },
                onDelete = { deleting = unsorted.filter { it.id in selection.ids } },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            FolderDropTray(
                folders = allFolders,
                onMove = moveSelection,
                onTap = { folderId -> moveSelection(selection.ids.toList(), folderId) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = contentPadding.calculateBottomPadding() + 8.dp)
                    .padding(horizontal = 8.dp),
            )
        }
    }

    if (creating) {
        FolderNameDialog(
            title = stringResource(R.string.new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onConfirm = {
                viewModel.createFolder(it)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }

    if (moving) {
        val ids = selection.ids
        MoveToFolderDialog(
            currentFolders = unsorted.filter { it.id in ids }.map { it.folderId }.toSet(),
            folders = allFolders,
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

    deleting?.let { entries ->
        DeleteEntriesDialog(
            entries = entries,
            onConfirm = { deleteFile ->
                viewModel.delete(entries, deleteFile)
                selection.clear()
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun FolderCard(
    summary: FolderSummary,
    onClick: () -> Unit,
    onDrop: (List<Long>, Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val folder = summary.folder
    var hovered by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier
            .dropHighlight(hovered, scheme.primary)
            .folderDropTarget(folderId = folder.id, onDrop = onDrop, onHover = { hovered = it }),
        shape = MaterialTheme.shapes.large,
        color = if (hovered) scheme.primary.copy(alpha = 0.12f) else scheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            scheme.outlineVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            ) {
                if (summary.cover != null) {
                    AsyncImage(
                        model = summary.cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.45f))
                                )
                            )
                    )
                } else {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(brandGradient())
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(10.dp)
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (folder.isPlaylist) Icons.AutoMirrored.Rounded.PlaylistPlay
                        else Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleSmall.forContent(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.items_count, summary.itemCount, summary.itemCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
