package com.mh.videodownloader.ui.history

import android.content.ClipData
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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.videodownloader.R
import com.mh.videodownloader.ui.common.Brand
import com.mh.videodownloader.ui.common.EmptyState
import com.mh.videodownloader.ui.library.ConfirmDialog
import com.mh.videodownloader.ui.library.InfoDialog
import com.mh.videodownloader.ui.library.MediaRow
import com.mh.videodownloader.ui.library.RowAction
import com.mh.videodownloader.util.formatBytes
import com.mh.videodownloader.util.formatDate
import com.mh.videodownloader.util.shareMedia
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    contentPadding: PaddingValues,
    onPlay: (uri: String, title: String, mimeType: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded by viewModel.entries.collectAsStateWithLifecycle()
    val entries = loaded.orEmpty()
    val info by viewModel.info.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    message?.let { res ->
        val text = stringResource(res)
        LaunchedEffect(res) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.history_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) {
                            Text(stringResource(R.string.clear_history))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.history_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (loaded != null && entries.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.History,
                    title = stringResource(R.string.history_empty_title),
                    subtitle = stringResource(R.string.history_empty_sub),
                    modifier = Modifier.padding(top = 40.dp),
                )
            }
        }

        items(entries, key = { it.id }) { entry ->
            MediaRow(
                id = entry.id.toString(),
                thumbnail = entry.thumbnail,
                title = entry.title,
                meta = listOfNotNull(formatBytes(entry.sizeBytes), formatDate(entry.createdAt)),
                isAudio = entry.isAudio,
                brand = Brand.of(entry.sourceLabel),
                showBrand = true,
                onPlay = { viewModel.withFile(entry) { uri -> onPlay(uri.toString(), entry.title, entry.mimeType) } },
                actions = listOf(
                    RowAction(stringResource(R.string.info), Icons.Rounded.Info) { viewModel.showInfo(entry) },
                    RowAction(stringResource(R.string.share), Icons.Rounded.Share) {
                        viewModel.withFile(entry) { uri ->
                            if (!shareMedia(context, uri.toString(), entry.mimeType, entry.title)) {
                                Toast.makeText(context, R.string.cannot_share, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    // Removes the log line only; the video stays on the phone and in the library.
                    RowAction(stringResource(R.string.remove_from_history), Icons.Rounded.PlaylistRemove) {
                        viewModel.remove(entry)
                    },
                ),
            )
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.clear_history_title),
            message = stringResource(R.string.clear_history_message),
            confirmLabel = stringResource(R.string.clear_history),
            destructive = false,
            onConfirm = {
                viewModel.clearAll()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
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
