package com.mh.videodownloader.ui.library

import android.Manifest
import android.content.ClipData
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFramePercent
import com.mh.videodownloader.R
import com.mh.videodownloader.data.prefs.LibrarySource
import com.mh.videodownloader.data.storage.FolderStore
import com.mh.videodownloader.data.storage.FolderSummary
import com.mh.videodownloader.data.storage.PhoneVideo
import com.mh.videodownloader.data.storage.StoreItem
import com.mh.videodownloader.ui.common.EmptyState
import com.mh.videodownloader.ui.common.SectionTitle
import com.mh.videodownloader.ui.theme.brandGradient
import com.mh.videodownloader.ui.theme.forContent
import com.mh.videodownloader.util.formatBytes
import com.mh.videodownloader.util.formatDate
import com.mh.videodownloader.util.formatDuration
import com.mh.videodownloader.util.shareMedia
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    contentPadding: PaddingValues,
    onPlay: (uri: String, title: String, mimeType: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val info by viewModel.info.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    message?.let { res ->
        val text = stringResource(res)
        LaunchedEffect(res) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    when (settings?.librarySource) {
        null -> Unit
        LibrarySource.SAVE_FOLDER -> FolderLibrary(viewModel, contentPadding, onPlay, modifier)
        LibrarySource.ALL_PHONE_VIDEOS -> PhoneLibrary(viewModel, contentPadding, onPlay, modifier)
    }

    info?.let { details ->
        val copied = stringResource(R.string.link_copied)
        InfoDialog(
            details = details,
            onDismiss = viewModel::dismissInfo,
            onCopyLink = { url ->
                scope.launch {
                    clipboard.setClipEntry(ClipData.newPlainText("link", url).toClipEntry())
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Save folder: real folders on disk
// ---------------------------------------------------------------------------------------------

@Composable
private fun FolderLibrary(
    viewModel: LibraryViewModel,
    contentPadding: PaddingValues,
    onPlay: (uri: String, title: String, mimeType: String) -> Unit,
    modifier: Modifier,
) {
    val state by viewModel.folderState.collectAsStateWithLifecycle()
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val selection = rememberFileSelection()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<StoreItem?>(null) }
    var deleting by remember { mutableStateOf<List<StoreItem>?>(null) }
    var moving by remember { mutableStateOf<List<String>?>(null) }

    val grant = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::onFolderGranted)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val ready = state as? FolderState.Ready
    BackHandler(enabled = selection.active) { selection.clear() }
    BackHandler(enabled = !selection.active && (ready?.path?.size ?: 0) > 1) { viewModel.up() }

    val moveSelection: (List<String>, String) -> Unit = { ids, folderId ->
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
                        .padding(top = 16.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if ((ready?.path?.size ?: 0) > 1) {
                        IconButton(onClick = { viewModel.up() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.up_folder))
                        }
                    } else {
                        Spacer(Modifier.width(4.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if ((ready?.path?.size ?: 0) > 1) ready!!.current.name
                            else stringResource(R.string.library_title),
                            style = MaterialTheme.typography.headlineSmall.forContent(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                    if (ready != null) {
                        FilledTonalButton(onClick = { creating = true }) {
                            Icon(Icons.Rounded.CreateNewFolder, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.new_folder))
                        }
                    }
                }
            }

            when (val current = state) {
                FolderState.Loading -> item { Loading() }

                FolderState.NeedsAccess -> item {
                    AccessCard(
                        title = stringResource(R.string.grant_title),
                        body = stringResource(R.string.grant_desc),
                        button = stringResource(R.string.grant_button),
                        onClick = {
                            viewModel.prepareDefaultFolder()
                            grant.launch(FolderStore.defaultFolderHint())
                        },
                    )
                }

                FolderState.Unreadable -> item {
                    EmptyState(
                        icon = Icons.Rounded.FolderOpen,
                        title = stringResource(R.string.cannot_open_file),
                        subtitle = stringResource(R.string.folder_unreadable),
                        modifier = Modifier.padding(top = 40.dp),
                    )
                }

                is FolderState.Ready -> {
                    if (current.folders.isEmpty() && current.files.isEmpty()) {
                        item {
                            EmptyState(
                                icon = if (current.path.size > 1) Icons.Rounded.FolderOpen else Icons.Rounded.VideoLibrary,
                                title = stringResource(
                                    if (current.path.size > 1) R.string.folder_empty else R.string.library_empty_title
                                ),
                                subtitle = stringResource(
                                    if (current.path.size > 1) R.string.folder_empty_hint else R.string.library_empty_sub
                                ),
                                modifier = Modifier.padding(top = 40.dp),
                            )
                        }
                    }

                    if (current.folders.isNotEmpty()) {
                        // Two cards per row, laid out by hand: a grid nested inside a LazyColumn
                        // would fight it for the scroll.
                        items(current.folders.chunked(2), key = { "row-" + it.first().item.docId }) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { card ->
                                    FolderCardView(
                                        card = card,
                                        onOpen = { viewModel.open(card.item) },
                                        onDrop = moveSelection,
                                        onRename = { renaming = card.item },
                                        onMove = { moving = listOf(card.item.docId) },
                                        onDelete = { deleting = listOf(card.item) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    if (current.files.isNotEmpty()) {
                        item {
                            Column(Modifier.padding(top = 6.dp)) {
                                SectionTitle(pluralStringResource(R.plurals.files_count, current.files.size, current.files.size))
                                if (!selection.active) {
                                    Text(
                                        text = stringResource(R.string.hold_to_select),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
                                    )
                                }
                            }
                        }
                        items(current.files, key = { it.docId }) { file ->
                            MediaRow(
                                id = file.docId,
                                thumbnail = videoFrame(file.uri),
                                title = file.name.substringBeforeLast('.'),
                                meta = listOfNotNull(formatBytes(file.sizeBytes), formatDate(file.modifiedAt)),
                                isAudio = file.isAudio,
                                selection = selection,
                                onPlay = { onPlay(file.uri.toString(), file.name, file.mimeType) },
                                actions = listOf(
                                    RowAction(stringResource(R.string.info), Icons.Rounded.Info) { viewModel.showInfo(file) },
                                    RowAction(stringResource(R.string.share), Icons.Rounded.Share) {
                                        if (!shareMedia(context, file.uri.toString(), file.mimeType, file.name)) {
                                            Toast.makeText(context, R.string.cannot_share, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    RowAction(stringResource(R.string.move_to_folder), Icons.AutoMirrored.Rounded.DriveFileMove) {
                                        moving = listOf(file.docId)
                                    },
                                    RowAction(stringResource(R.string.rename_file), Icons.Rounded.DriveFileRenameOutline) {
                                        renaming = file
                                    },
                                    RowAction(stringResource(R.string.delete), Icons.Rounded.DeleteOutline, destructive = true) {
                                        deleting = listOf(file)
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }

        if (selection.active && ready != null) {
            SelectionBar(
                count = selection.ids.size,
                onClose = selection::clear,
                onSelectAll = { selection.selectAll(ready.files.map { it.docId }) },
                onMove = { moving = selection.ids.toList() },
                onDelete = { deleting = ready.files.filter { it.docId in selection.ids } },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            FolderDropTray(
                folders = allFolders.filter { it.docId != ready.current.docId },
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
        NameDialog(
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

    renaming?.let { item ->
        NameDialog(
            title = stringResource(R.string.rename),
            initial = if (item.isFolder) item.name else item.name.substringBeforeLast('.'),
            confirmLabel = stringResource(R.string.save),
            onConfirm = { name ->
                // Keep the extension: renaming "clip" must not turn clip.mp4 into an unplayable "clip".
                val ext = if (item.isFolder || !item.name.contains('.')) "" else "." + item.name.substringAfterLast('.')
                viewModel.rename(item, name + ext)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    moving?.let { ids ->
        MovePickerDialog(
            folders = allFolders,
            currentFolderId = ready?.current?.docId,
            // A folder being moved can't land in itself or anywhere beneath it.
            excluded = ready?.folders.orEmpty().map { it.item.docId }.filter { it in ids }.toSet(),
            onMove = { folderId ->
                moveSelection(ids, folderId)
                moving = null
            },
            onNewFolder = { name ->
                viewModel.moveToNewFolder(ids, name)
                selection.clear()
                moving = null
            },
            onDismiss = { moving = null },
        )
    }

    deleting?.let { items ->
        val folder = items.singleOrNull()?.takeIf { it.isFolder }
        ConfirmDialog(
            title = stringResource(if (folder != null) R.string.delete_folder else R.string.delete_files_title),
            message = if (folder != null) stringResource(R.string.delete_folder_message, folder.name)
            else items.singleOrNull()?.name ?: pluralStringResource(R.plurals.files_count, items.size, items.size),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                viewModel.delete(items)
                selection.clear()
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun FolderCardView(
    card: FolderCard,
    onOpen: () -> Unit,
    onDrop: (List<String>, String) -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hovered by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        modifier = modifier
            .dropHighlight(hovered, scheme.primary)
            .folderDropTarget(folderId = card.item.docId, onDrop = onDrop, onHover = { hovered = it }),
        shape = MaterialTheme.shapes.large,
        color = if (hovered) scheme.primary.copy(alpha = 0.12f) else scheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            ) {
                val cover = card.summary.cover
                if (cover != null) {
                    AsyncImage(
                        model = videoFrame(cover),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.45f))))
                    )
                } else {
                    Box(Modifier.matchParentSize().background(brandGradient()))
                }
                Box(
                    modifier = Modifier
                        .padding(10.dp)
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Folder, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = card.item.name,
                        style = MaterialTheme.typography.titleSmall.forContent(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = folderContents(card.summary),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more), tint = scheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename)) },
                            leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                            onClick = {
                                menuOpen = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_to_folder)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null) },
                            onClick = {
                                menuOpen = false
                                onMove()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_folder), color = scheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = scheme.error) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Every video on the phone
// ---------------------------------------------------------------------------------------------

private val videoPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
private fun PhoneLibrary(
    viewModel: LibraryViewModel,
    contentPadding: PaddingValues,
    onPlay: (uri: String, title: String, mimeType: String) -> Unit,
    modifier: Modifier,
) {
    val state by viewModel.phoneState.collectAsStateWithLifecycle()
    val consent by viewModel.deleteConsent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<PhoneVideo?>(null) }

    val hasPermission = {
        ContextCompat.checkSelfPermission(context, videoPermission) == PackageManager.PERMISSION_GRANTED
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.loadPhone(hasPermission())
    }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onDeleteConsentResult()
    }
    LaunchedEffect(consent) {
        consent?.let { consentLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.loadPhone(hasPermission()) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 6.dp)) {
                Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = stringResource(R.string.library_view_phone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (val current = state) {
            PhoneState.Loading -> item { Loading() }
            PhoneState.NeedsPermission -> item {
                AccessCard(
                    title = stringResource(R.string.phone_permission_title),
                    body = stringResource(R.string.phone_permission_desc),
                    button = stringResource(R.string.phone_permission_button),
                    onClick = { askPermission.launch(videoPermission) },
                )
            }

            is PhoneState.Ready -> {
                if (current.videos.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.VideoLibrary,
                            title = stringResource(R.string.library_empty_title),
                            subtitle = stringResource(R.string.library_view_phone_desc),
                            modifier = Modifier.padding(top = 40.dp),
                        )
                    }
                }
                item {
                    SectionTitle(pluralStringResource(R.plurals.files_count, current.videos.size, current.videos.size))
                }
                items(current.videos, key = { it.uri.toString() }) { video ->
                    MediaRow(
                        id = video.uri.toString(),
                        thumbnail = videoFrame(video.uri),
                        title = video.name.substringBeforeLast('.'),
                        meta = listOfNotNull(
                            formatBytes(video.sizeBytes),
                            video.durationMs.takeIf { it > 0 }?.let { formatDuration((it / 1000).toInt()) },
                            formatDate(video.addedAt),
                        ),
                        isAudio = false,
                        onPlay = { onPlay(video.uri.toString(), video.name, video.mimeType) },
                        actions = listOf(
                            RowAction(stringResource(R.string.info), Icons.Rounded.Info) { viewModel.showInfo(video) },
                            RowAction(stringResource(R.string.share), Icons.Rounded.Share) {
                                if (!shareMedia(context, video.uri.toString(), video.mimeType, video.name)) {
                                    Toast.makeText(context, R.string.cannot_share, Toast.LENGTH_SHORT).show()
                                }
                            },
                            RowAction(stringResource(R.string.delete), Icons.Rounded.DeleteOutline, destructive = true) {
                                deleting = video
                            },
                        ),
                    )
                }
            }
        }
    }

    deleting?.let { video ->
        ConfirmDialog(
            title = stringResource(R.string.delete_files_title),
            message = video.name,
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                viewModel.deletePhoneVideos(listOf(video))
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun AccessCard(title: String, body: String, button: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** A frame from 10% into the video — the very first frame is so often a fade from black. */
@Composable
private fun videoFrame(uri: android.net.Uri): ImageRequest =
    ImageRequest.Builder(LocalContext.current)
        .data(uri)
        .videoFramePercent(0.1)
        .build()

/** "3 videos", or "3 videos · 2 folders" when the folder also holds subfolders. */
@Composable
private fun folderContents(summary: FolderSummary): String {
    val videos = pluralStringResource(R.plurals.items_count, summary.itemCount, summary.itemCount)
    if (summary.folderCount == 0) return videos
    val folders = pluralStringResource(R.plurals.folders_count, summary.folderCount, summary.folderCount)
    return if (summary.itemCount == 0) folders else "$videos · $folders"
}
