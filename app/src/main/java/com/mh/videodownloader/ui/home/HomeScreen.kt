package com.mh.videodownloader.ui.home

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mh.videodownloader.R
import com.mh.videodownloader.data.engine.YtDlpEngine
import com.mh.videodownloader.domain.DownloadState
import com.mh.videodownloader.domain.DownloadTask
import com.mh.videodownloader.domain.FormatOption
import com.mh.videodownloader.domain.MediaInfo
import com.mh.videodownloader.ui.common.AppCard
import com.mh.videodownloader.ui.common.Chip
import com.mh.videodownloader.ui.common.EmptyState
import com.mh.videodownloader.ui.common.GradientButton
import com.mh.videodownloader.ui.common.SectionTitle
import com.mh.videodownloader.ui.common.SelectableRow
import com.mh.videodownloader.ui.common.ShimmerBlock
import com.mh.videodownloader.ui.theme.SourceColors
import com.mh.videodownloader.ui.theme.heroGradient
import com.mh.videodownloader.util.formatBytes
import com.mh.videodownloader.util.formatDuration
import com.mh.videodownloader.util.formatEta
import com.mh.videodownloader.util.ltr
import com.mh.videodownloader.util.percentOf

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val url by viewModel.url.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedHeight by viewModel.selectedHeight.collectAsStateWithLifecycle()
    val engineStatus by viewModel.engineStatus.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val isUpdating by viewModel.isUpdatingEngine.collectAsStateWithLifecycle()

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Coming back to the app with a link already copied is the common case — offer it silently
    // instead of making the user paste.
    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.offerClipboard(clipboard.getText()?.text?.toString())
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
    ) {
        item { Hero() }

        item {
            UrlInputCard(
                url = url,
                analyzing = state is HomeUiState.Analyzing,
                engineBusy = engineStatus == YtDlpEngine.Status.INITIALIZING,
                onUrlChanged = viewModel::onUrlChanged,
                onPaste = { viewModel.pasteFromClipboard(clipboard.getText()?.text?.toString()) },
                onClear = viewModel::clearUrl,
                onAnalyze = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    viewModel.analyze()
                },
                modifier = Modifier.padding(horizontal = 16.dp).offset(y = (-34).dp),
            )
        }

        item {
            AnimatedVisibility(
                visible = engineStatus == YtDlpEngine.Status.INITIALIZING ||
                    engineStatus == YtDlpEngine.Status.FAILED,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                EngineBanner(
                    failed = engineStatus == YtDlpEngine.Status.FAILED,
                    modifier = Modifier.padding(horizontal = 16.dp).offset(y = (-20).dp),
                )
            }
        }

        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp).offset(y = (-16).dp)) {
                when (val current = state) {
                    HomeUiState.Idle -> IdleHint()
                    HomeUiState.Analyzing -> AnalyzingSkeleton()
                    is HomeUiState.Error -> ErrorCard(
                        message = current.message,
                        canUpdate = current.canRetryAfterUpdate,
                        updating = isUpdating,
                        onRetry = viewModel::analyze,
                        onUpdateAndRetry = viewModel::updateEngineAndRetry,
                    )

                    is HomeUiState.Ready -> ReadyContent(
                        state = current,
                        selectedHeight = selectedHeight,
                        onSelect = viewModel::selectHeight,
                        onDownloadVideo = viewModel::downloadVideo,
                        onDownloadAudio = viewModel::downloadAudio,
                    )
                }
            }
        }

        if (downloads.isNotEmpty()) {
            item {
                SectionTitle(
                    text = stringOf(R.string.downloads_active),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            items(count = downloads.size, key = { downloads[it].id }) { index ->
                val task = downloads[index]
                DownloadRow(
                    task = task,
                    onCancel = { viewModel.cancelDownload(task.id) },
                    onDismiss = { viewModel.dismissTask(task.id) },
                    onOpen = {
                        task.savedUri?.let { uri ->
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(
                                    uri.toUri(),
                                    if (task.selection.isAudio) "audio/mpeg" else "video/mp4",
                                )
                                addFlags(
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_ACTIVITY_NEW_TASK
                                )
                            }
                            runCatching { context.startActivity(intent) }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun stringOf(res: Int): String = androidx.compose.ui.res.stringResource(res)

@Composable
private fun Hero(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(heroGradient())
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 60.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringOf(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringOf(R.string.home_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringOf(R.string.home_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
private fun UrlInputCard(
    url: String,
    analyzing: Boolean,
    engineBusy: Boolean,
    onUrlChanged: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 10.dp,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        stringOf(R.string.url_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                // A URL is never RTL, even when the rest of the app is.
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    textDirection = TextDirection.Ltr,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onAnalyze() }),
                trailingIcon = {
                    if (url.isEmpty()) {
                        IconButton(onClick = onPaste) {
                            Icon(
                                Icons.Rounded.ContentPaste,
                                contentDescription = stringOf(R.string.paste),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        IconButton(onClick = onClear) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringOf(R.string.clear),
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
            Spacer(Modifier.height(12.dp))
            GradientButton(
                text = if (analyzing) stringOf(R.string.analyzing) else stringOf(R.string.analyze),
                icon = Icons.Rounded.TravelExplore,
                onClick = onAnalyze,
                enabled = url.isNotBlank() && !engineBusy,
                loading = analyzing,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EngineBanner(failed: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (failed) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (failed) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column {
                Text(
                    text = stringOf(
                        if (failed) R.string.engine_init_failed else R.string.initializing_engine
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (failed) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (!failed) {
                    Text(
                        text = stringOf(R.string.initializing_engine_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleHint(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Rounded.Bolt,
        title = stringOf(R.string.idle_title),
        subtitle = stringOf(R.string.idle_subtitle),
        modifier = modifier,
    )
}

@Composable
private fun AnalyzingSkeleton(modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ShimmerBlock(
                modifier = Modifier.size(width = 118.dp, height = 74.dp),
                shape = RoundedCornerShape(14.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                ShimmerBlock(Modifier.fillMaxWidth().height(15.dp))
                ShimmerBlock(Modifier.fillMaxWidth(0.7f).height(15.dp))
                ShimmerBlock(Modifier.fillMaxWidth(0.45f).height(13.dp))
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    canUpdate: Boolean,
    updating: Boolean,
    onRetry: () -> Unit,
    onUpdateAndRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringOf(R.string.error_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canUpdate) {
                    OutlinedButton(onClick = onUpdateAndRetry, enabled = !updating) {
                        if (updating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Rounded.Update, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringOf(R.string.update_and_retry))
                    }
                }
                TextButton(onClick = onRetry) { Text(stringOf(R.string.retry)) }
            }
        }
    }
}

@Composable
private fun ReadyContent(
    state: HomeUiState.Ready,
    selectedHeight: Int?,
    onSelect: (Int) -> Unit,
    onDownloadVideo: () -> Unit,
    onDownloadAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MediaCard(state.info)

        if (state.formats.isNotEmpty()) {
            Column {
                SectionTitle(stringOf(R.string.video_quality))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.formats.reversed().forEach { format ->
                        QualityRow(
                            format = format,
                            selected = format.height == selectedHeight,
                            onClick = { onSelect(format.height) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                GradientButton(
                    text = stringOf(R.string.download_video),
                    icon = Icons.Rounded.Download,
                    onClick = onDownloadVideo,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        AudioCard(bytes = state.audioBytes, onDownload = onDownloadAudio)
    }
}

@Composable
private fun MediaCard(info: MediaInfo, modifier: Modifier = Modifier) {
    val sourceLabel = info.source.brandName ?: stringOf(info.source.labelRes ?: R.string.source_other)
    val accent = SourceColors[sourceLabel] ?: MaterialTheme.colorScheme.primary

    AppCard(modifier = modifier) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 118.dp, height = 74.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (info.thumbnail != null) {
                    AsyncImage(
                        model = info.thumbnail,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chip(
                        text = sourceLabel,
                        color = accent,
                        containerColor = accent.copy(alpha = 0.14f),
                    )
                    if (info.durationSec > 0) {
                        Chip(
                            text = ltr(formatDuration(info.durationSec)),
                            icon = Icons.Rounded.Schedule,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                }
                info.uploader?.let { uploader ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = uploader,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityRow(
    format: FormatOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        label = "qualityBorder",
    )
    SelectableRow(selected = selected, onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimary)
                    )
                } else {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(borderColor.copy(alpha = 0.35f))
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = ltr(format.label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = ltr(
                    buildString {
                        append(format.ext.uppercase())
                        if (format.fps >= 50) append(" · ${format.fps}fps")
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatBytes(format.approxBytes)?.let { ltr("~$it") }
                    ?: stringOf(R.string.size_unknown),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AudioCard(bytes: Long?, onDownload: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier, tonal = true) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringOf(R.string.audio_only),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringOf(R.string.audio_only_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                formatBytes(bytes)?.let {
                    Text(
                        text = ltr("~$it"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringOf(R.string.download_mp3), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
fun DownloadRow(
    task: DownloadTask,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = (percentOf(task.progress) / 100f),
        label = "downloadProgress",
    )
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (task.selection.isAudio) Icons.Rounded.MusicNote else Icons.Rounded.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when {
                    task.state.isActive -> IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringOf(R.string.cancel),
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    task.state == DownloadState.COMPLETED -> IconButton(
                        onClick = onOpen,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = stringOf(R.string.open),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    else -> IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (task.state.isActive) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stateText(task),
                    style = MaterialTheme.typography.labelMedium,
                    color = stateColor(task.state),
                )
                Spacer(Modifier.weight(1f))
                val trailing = buildList {
                    task.speed?.takeIf { task.state == DownloadState.DOWNLOADING }?.let { add(it) }
                    formatEta(task.etaSec)?.takeIf { task.state == DownloadState.DOWNLOADING }
                        ?.let { add(it) }
                    if (task.state.isActive) add("${percentOf(task.progress)}%")
                }.joinToString(" · ")
                if (trailing.isNotEmpty()) {
                    Text(
                        text = ltr(trailing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            task.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun stateText(task: DownloadTask): String = stringOf(
    when (task.state) {
        DownloadState.QUEUED -> R.string.state_queued
        DownloadState.DOWNLOADING -> R.string.state_downloading
        DownloadState.PROCESSING -> R.string.state_processing
        DownloadState.SAVING -> R.string.state_saving
        DownloadState.COMPLETED -> R.string.state_completed
        DownloadState.FAILED -> R.string.state_failed
        DownloadState.CANCELED -> R.string.state_canceled
    }
)

@Composable
private fun stateColor(state: DownloadState): Color = when (state) {
    DownloadState.COMPLETED -> MaterialTheme.colorScheme.primary
    DownloadState.FAILED -> MaterialTheme.colorScheme.error
    DownloadState.CANCELED -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
