package com.mh.videodownloader.ui.library

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mh.videodownloader.R
import com.mh.videodownloader.data.db.Folder
import com.mh.videodownloader.ui.theme.forContent

/** Which downloads are ticked. Selection mode is simply "something is selected". */
@Stable
class EntrySelection {
    var ids by mutableStateOf(emptySet<Long>())
        private set

    val active: Boolean get() = ids.isNotEmpty()

    fun toggle(id: Long) {
        ids = if (id in ids) ids - id else ids + id
    }

    fun start(id: Long) {
        ids = setOf(id)
    }

    fun selectAll(all: Collection<Long>) {
        ids = all.toSet()
    }

    fun clear() {
        ids = emptySet()
    }
}

@Composable
fun rememberEntrySelection() = remember { EntrySelection() }

private const val DRAG_LABEL = "downloads"

/**
 * Makes a selected card draggable. Holding it picks up the whole selection, not only this card,
 * so several videos go into a folder in one drop. The ids travel as local state — the drag never
 * leaves the app — with a plain-text clip because the platform requires one.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.dragSelection(ids: () -> Collection<Long>): Modifier =
    dragAndDropSource { _ ->
        val payload = ids().toList()
        DragAndDropTransferData(
            clipData = ClipData.newPlainText(DRAG_LABEL, payload.joinToString(",")),
            localState = payload,
        )
    }

@Suppress("UNCHECKED_CAST")
private fun DragAndDropEvent.draggedIds(): List<Long>? =
    toAndroidDragEvent().localState as? List<Long>

/**
 * A place selected videos can be dropped on. [folderId] null means "take them out of any
 * folder". [onHover] lets the target light up while something is held over it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.folderDropTarget(
    folderId: Long?,
    onDrop: (ids: List<Long>, folderId: Long?) -> Unit,
    onHover: (Boolean) -> Unit,
): Modifier {
    val target = remember(folderId, onDrop) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                onHover(false)
                val ids = event.draggedIds() ?: return false
                onDrop(ids, folderId)
                return true
            }

            override fun onEntered(event: DragAndDropEvent) = onHover(true)
            override fun onExited(event: DragAndDropEvent) = onHover(false)
            override fun onEnded(event: DragAndDropEvent) = onHover(false)
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) && event.draggedIds() != null
        },
        target = target,
    )
}

/** Replaces the screen title while videos are selected. */
@Composable
fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = pluralStringResource(R.plurals.selected_count, count, count),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            BarAction(Icons.Rounded.DoneAll, stringResource(R.string.select_all), onSelectAll)
            BarAction(Icons.Rounded.DriveFileMove, stringResource(R.string.move_to_folder), onMove)
            BarAction(
                Icons.Rounded.DeleteOutline,
                stringResource(R.string.delete),
                onDelete,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun BarAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = tint)
    }
}

/**
 * Shown at the bottom while selecting: every folder as a chip you can drop the selection on —
 * or simply tap. It stays on screen for the whole selection (not just during a drag) because a
 * drop target that appears mid-drag never hears about the drag that is already under way.
 */
@Composable
fun FolderDropTray(
    folders: List<Folder>,
    onMove: (ids: List<Long>, folderId: Long?) -> Unit,
    onTap: (folderId: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 10.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.drag_to_folder),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.size(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "none") {
                    DropChip(
                        icon = Icons.Rounded.FolderOff,
                        label = stringResource(R.string.no_folder),
                        folderId = null,
                        onMove = onMove,
                        onTap = onTap,
                    )
                }
                items(folders, key = { it.id }) { folder ->
                    DropChip(
                        icon = if (folder.isPlaylist) Icons.AutoMirrored.Rounded.PlaylistPlay
                        else Icons.Rounded.Folder,
                        label = folder.name,
                        folderId = folder.id,
                        onMove = onMove,
                        onTap = onTap,
                    )
                }
            }
        }
    }
}

@Composable
private fun DropChip(
    icon: ImageVector,
    label: String,
    folderId: Long?,
    onMove: (ids: List<Long>, folderId: Long?) -> Unit,
    onTap: (folderId: Long?) -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = { onTap(folderId) },
        shape = CircleShape,
        color = if (hovered) scheme.primary else scheme.surfaceContainerLowest,
        modifier = Modifier
            .border(
                width = if (hovered) 2.dp else 1.dp,
                color = if (hovered) scheme.primary else scheme.outlineVariant,
                shape = CircleShape,
            )
            .folderDropTarget(folderId = folderId, onDrop = onMove, onHover = { hovered = it }),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (hovered) scheme.onPrimary else scheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.forContent(),
                color = if (hovered) scheme.onPrimary else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Outline that marks a folder card as the current drop spot. */
fun Modifier.dropHighlight(active: Boolean, color: androidx.compose.ui.graphics.Color): Modifier =
    if (active) border(2.dp, color, androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
    else this
