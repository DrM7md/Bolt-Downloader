package com.mh.videodownloader.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.videodownloader.BuildConfig
import com.mh.videodownloader.R
import com.mh.videodownloader.data.prefs.AppLanguage
import com.mh.videodownloader.data.prefs.LibrarySource
import com.mh.videodownloader.data.prefs.ThemeMode
import com.mh.videodownloader.data.storage.FolderStore
import com.mh.videodownloader.ui.common.AppCard
import com.mh.videodownloader.ui.common.SectionTitle
import com.mh.videodownloader.util.ltr

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val savePath by viewModel.saveFolderPath.collectAsStateWithLifecycle()

    LaunchedEffect(settings.saveFolder) { viewModel.refreshSaveFolder(settings.saveFolder) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::onSaveFolderPicked)
    }
    // Switching the library to "all phone videos" asks for video access right there, where the
    // reason is obvious. Declining keeps the choice; the library offers the button again.
    val videoPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
        )

        // ---- Appearance -----------------------------------------------------------------
        Section(stringResource(R.string.settings_appearance)) {
            Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(stringResource(R.string.theme_system), Icons.Rounded.PhoneAndroid, settings.themeMode == ThemeMode.SYSTEM) {
                    viewModel.setThemeMode(ThemeMode.SYSTEM)
                }
                Choice(stringResource(R.string.theme_light), Icons.Rounded.LightMode, settings.themeMode == ThemeMode.LIGHT) {
                    viewModel.setThemeMode(ThemeMode.LIGHT)
                }
                Choice(stringResource(R.string.theme_dark), Icons.Rounded.DarkMode, settings.themeMode == ThemeMode.DARK) {
                    viewModel.setThemeMode(ThemeMode.DARK)
                }
            }
            Divider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Rounded.Language)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(stringResource(R.string.language_system), null, settings.language == AppLanguage.SYSTEM) {
                    viewModel.setLanguage(AppLanguage.SYSTEM)
                }
                Choice(stringResource(R.string.language_ar), null, settings.language == AppLanguage.ARABIC) {
                    viewModel.setLanguage(AppLanguage.ARABIC)
                }
                Choice(stringResource(R.string.language_en), null, settings.language == AppLanguage.ENGLISH) {
                    viewModel.setLanguage(AppLanguage.ENGLISH)
                }
            }
        }

        // ---- Library ----------------------------------------------------------------------
        Section(stringResource(R.string.tab_library)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Rounded.VideoLibrary)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.library_source), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Choice(
                    stringResource(R.string.library_view_folder),
                    null,
                    settings.librarySource == LibrarySource.SAVE_FOLDER,
                ) { viewModel.setLibrarySource(LibrarySource.SAVE_FOLDER) }
                Choice(
                    stringResource(R.string.library_view_phone),
                    null,
                    settings.librarySource == LibrarySource.ALL_PHONE_VIDEOS,
                ) {
                    viewModel.setLibrarySource(LibrarySource.ALL_PHONE_VIDEOS)
                    videoPermission.launch(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO
                        else Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                }
            }
            Divider()
            // The save folder is where downloads go whichever view the library shows.
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Rounded.Folder)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.save_folder), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = ltr(savePath ?: stringResource(R.string.save_folder_default)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.save_folder_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = {
                    viewModel.prepareDefaultFolder()
                    folderPicker.launch(FolderStore.defaultFolderHint())
                }) { Text(stringResource(R.string.change)) }
            }
        }

        // ---- Playback ---------------------------------------------------------------------
        Section(stringResource(R.string.playback)) {
            SwitchRow(
                icon = Icons.Rounded.Headphones,
                title = stringResource(R.string.background_playback),
                subtitle = stringResource(R.string.background_playback_desc),
                checked = settings.backgroundPlayback,
                onCheckedChange = viewModel::setBackgroundPlayback,
            )
        }

        // ---- About ------------------------------------------------------------------------
        Section(stringResource(R.string.settings_about), tonal = true) {
            Row {
                IconBubble(Icons.Rounded.Info)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.about_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, tonal: Boolean = false, content: @Composable () -> Unit) {
    SectionTitle(text = title, modifier = Modifier.padding(start = 20.dp, end = 20.dp))
    AppCard(modifier = Modifier.padding(horizontal = 16.dp), tonal = tonal) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Choice(label: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun IconBubble(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}
