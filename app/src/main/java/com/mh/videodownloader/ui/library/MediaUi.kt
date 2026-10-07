package com.mh.videodownloader.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mh.videodownloader.R
import com.mh.videodownloader.data.storage.FolderRef
import com.mh.videodownloader.data.storage.MediaDetails
import com.mh.videodownloader.ui.common.AppCard
import com.mh.videodownloader.ui.common.Brand
import com.mh.videodownloader.ui.common.SourceIcon
import com.mh.videodownloader.ui.theme.forContent
import com.mh.videodownloader.util.formatBytes
import com.mh.videodownloader.util.formatDate
import com.mh.videodownloader.util.formatDuration
import com.mh.videodownloader.util.ltr

/** One entry of a row's ⋮ menu. */
data class RowAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * One video or audio file, as listed in the library and the download log.
 *
 * Tap plays. Everything else lives behind the ⋮ menu, which keeps the card compact. When a
 * [selection] is passed, a long-press starts selecting; while selecting, a tap ticks and a
 * long-press on a ticked card picks the whole selection up to drag onto a folder.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaRow(
    id: String,
    thumbnail: Any?,
    title: String,
    meta: List<String>,
    isAudio: Boolean,
    onPlay: () -> Unit,
    actions: List<RowAction>,
    modifier: Modifier = Modifier,
    brand: Brand? = null,
    showBrand: Boolean = false,
    selection: FileSelection? = null,
) {
    val selecting = selection?.active == true
    val selected = selection?.ids?.contains(id) == true
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }

    // Order matters: the drag source sits inside the click handler so it sees the touch first.
    // The other way round, the tap detector consumes the press and the drag never starts.
    val gestures = Modifier
        .combinedClickable(
            onClick = { if (selection != null && selecting) selection.toggle(id) else onPlay() },
            onLongClick = if (selection != null && !selecting) {
                { selection.start(id) }
            } else {
                null
            },
        )
        .then(
            if (selection != null && selecting && selected) Modifier.dragSelection { selection.ids }
            else Modifier
        )

    AppCard(
        modifier = modifier
            .then(if (selected) Modifier.border(2.dp, scheme.primary, MaterialTheme.shapes.large) else Modifier)
            .clip(MaterialTheme.shapes.large)
            .then(gestures),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumb(thumbnail, isAudio, checked = if (selecting) selected else null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Full width so a short Latin title lines up with a wrapped one (content direction).
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.forContent(),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (showBrand) SourceIcon(brand, size = 14.dp)
                    Text(
                        text = meta.joinToString("  ·  ") { ltr(it) },
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!selecting && actions.isNotEmpty()) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.more),
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        actions.forEach { action ->
                            val tint = if (action.destructive) scheme.error else scheme.onSurface
                            DropdownMenuItem(
                                text = { Text(action.label, color = tint) },
                                leadingIcon = { Icon(action.icon, contentDescription = null, tint = tint) },
                                onClick = {
                                    menuOpen = false
                                    action.onClick()
                                },
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.width(10.dp))
            }
        }
    }
}

@Composable
private fun Thumb(model: Any?, isAudio: Boolean, checked: Boolean?) {
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null && !isAudio) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        val scheme = MaterialTheme.colorScheme
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    when (checked) {
                        true -> scheme.primary
                        false -> Color.White.copy(alpha = 0.85f)
                        null -> Color.Black.copy(alpha = 0.45f)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (checked) {
                true -> Icon(Icons.Rounded.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(17.dp))
                false -> Unit
                null -> Icon(
                    if (isAudio) Icons.Rounded.MusicNote else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Everything known about a file, with the path selectable so it can be copied. */
@Composable
fun InfoDialog(details: MediaDetails, onDismiss: () -> Unit, onCopyLink: ((String) -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.info)) },
        text = {
            SelectionContainer {
                Column(
                    modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    InfoLine(stringResource(R.string.info_name), details.name, content = true)
                    details.path?.let { InfoLine(stringResource(R.string.info_path), it, latin = true) }
                    formatBytes(details.sizeBytes)?.let { InfoLine(stringResource(R.string.info_size), it, latin = true) }
                    if (details.date > 0) {
                        InfoLine(stringResource(R.string.info_date), formatDate(details.date), latin = true)
                    }
                    details.durationMs?.takeIf { it > 0 }?.let {
                        InfoLine(stringResource(R.string.info_duration), formatDuration((it / 1000).toInt()), latin = true)
                    }
                    if ((details.width ?: 0) > 0 && (details.height ?: 0) > 0) {
                        InfoLine(stringResource(R.string.info_resolution), "${details.width} × ${details.height}", latin = true)
                    }
                    InfoLine(stringResource(R.string.info_type), details.mimeType, latin = true)
                    details.sourceUrl?.let { InfoLine(stringResource(R.string.info_source), it, latin = true) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = details.sourceUrl?.takeIf { onCopyLink != null }?.let { url ->
            { TextButton(onClick = { onCopyLink?.invoke(url) }) { Text(stringResource(R.string.copy_link)) } }
        },
    )
}

@Composable
private fun InfoLine(label: String, value: String, latin: Boolean = false, content: Boolean = false) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (latin) ltr(value) else value,
            style = if (content) MaterialTheme.typography.bodyMedium.forContent() else MaterialTheme.typography.bodyMedium,
        )
    }
}

/** New folder / rename, for folders and files alike. */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Opens ready to type: focused, with the current name selected so typing replaces it.
    var name by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name)) },
                textStyle = MaterialTheme.typography.bodyLarge.forContent(),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.text.trim()) }, enabled = name.text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium.forContent()) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * "Move to…": every folder under the save folder, indented by depth, plus "new folder". The
 * folder the files are already in is shown but disabled.
 */
@Composable
fun MovePickerDialog(
    folders: List<FolderRef>,
    currentFolderId: String?,
    excluded: Set<String> = emptySet(),
    onMove: (folderId: String) -> Unit,
    onNewFolder: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog(
            title = stringResource(R.string.new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onConfirm = onNewFolder,
            onDismiss = { creating = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_folder)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PickerRow(
                    icon = Icons.Rounded.CreateNewFolder,
                    label = stringResource(R.string.new_folder),
                    depth = 0,
                    accent = true,
                    enabled = true,
                    onClick = { creating = true },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                folders.forEach { folder ->
                    PickerRow(
                        icon = Icons.Rounded.Folder,
                        label = folder.name,
                        depth = folder.depth,
                        accent = false,
                        enabled = folder.docId != currentFolderId &&
                            excluded.none { folder.docId == it || folder.docId.startsWith("$it/") },
                        onClick = { onMove(folder.docId) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PickerRow(
    icon: ImageVector,
    label: String,
    depth: Int,
    accent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = (12 + depth * 18).dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = when {
                    !enabled -> scheme.outline
                    accent -> scheme.primary
                    else -> scheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.forContent(),
                color = when {
                    !enabled -> scheme.outline
                    accent -> scheme.primary
                    else -> scheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
