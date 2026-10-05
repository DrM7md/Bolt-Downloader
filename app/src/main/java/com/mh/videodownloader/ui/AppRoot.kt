package com.mh.videodownloader.ui

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mh.videodownloader.R
import com.mh.videodownloader.player.PlayerScreen
import com.mh.videodownloader.ui.history.HistoryScreen
import com.mh.videodownloader.ui.history.HistoryViewModel
import com.mh.videodownloader.ui.home.HomeScreen
import com.mh.videodownloader.ui.home.HomeViewModel
import com.mh.videodownloader.ui.library.FolderScreen
import com.mh.videodownloader.ui.library.FolderViewModel
import com.mh.videodownloader.ui.library.LibraryScreen
import com.mh.videodownloader.ui.library.LibraryViewModel
import com.mh.videodownloader.ui.settings.SettingsScreen
import com.mh.videodownloader.ui.settings.SettingsViewModel

private enum class Destination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.tab_home, Icons.Rounded.Home),
    LIBRARY("library", R.string.tab_library, Icons.Rounded.VideoLibrary),
    HISTORY("history", R.string.tab_history, Icons.Rounded.History),
    SETTINGS("settings", R.string.tab_settings, Icons.Rounded.Settings),
}

private const val FOLDER_ROUTE = "folder/{${FolderViewModel.ARG_FOLDER_ID}}"
private const val PLAYER_ROUTE = "player?uri={uri}&title={title}&mime={mime}"

private fun NavHostController.openPlayer(uri: String, title: String, mime: String) =
    navigate("player?uri=${Uri.encode(uri)}&title=${Uri.encode(title)}&mime=${Uri.encode(mime)}")

@Composable
fun AppRoot(
    homeViewModel: HomeViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val inPlayer = currentDestination?.route == PLAYER_ROUTE

    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val homeMessage by homeViewModel.message.collectAsStateWithLifecycle()
    val queuedCount by homeViewModel.queuedCount.collectAsStateWithLifecycle()
    val settingsMessage by settingsViewModel.message.collectAsStateWithLifecycle()

    // A queued playlist reports how many went in, with Arabic's own plural forms.
    val homeText = homeMessage?.let { res ->
        if (res == R.string.playlist_queued) {
            pluralStringResource(R.plurals.items_count, queuedCount, queuedCount) +
                " · " + stringResource(res)
        } else {
            stringResource(res)
        }
    }
    LaunchedEffect(homeMessage) {
        homeText?.let {
            snackbarHostState.showSnackbar(it)
            homeViewModel.consumeMessage()
        }
    }
    val settingsText = settingsMessage?.let { stringResource(it) }
    LaunchedEffect(settingsMessage) {
        settingsText?.let {
            snackbarHostState.showSnackbar(it)
            settingsViewModel.consumeMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // The player is a full-screen surface; tabs under a video are just noise.
            if (inPlayer) return@Scaffold
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                Destination.entries.forEach { destination ->
                    val inFolder = currentDestination?.route == FOLDER_ROUTE
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == destination.route } == true ||
                        (destination == Destination.LIBRARY && inFolder)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.HOME.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Destination.HOME.route) {
                HomeScreen(
                    viewModel = homeViewModel,
                    contentPadding = innerPadding,
                    onPlay = { uri, title, mime -> navController.openPlayer(uri, title, mime) },
                )
            }
            composable(Destination.LIBRARY.route) {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    contentPadding = innerPadding,
                    onOpenFolder = { id -> navController.navigate("folder/$id") },
                    onPlay = { e -> navController.openPlayer(e.fileUri, e.title, e.mimeType) },
                )
            }
            composable(
                route = FOLDER_ROUTE,
                arguments = listOf(
                    navArgument(FolderViewModel.ARG_FOLDER_ID) { type = NavType.LongType },
                ),
            ) {
                FolderScreen(
                    viewModel = viewModel(),
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                    onPlay = { e -> navController.openPlayer(e.fileUri, e.title, e.mimeType) },
                )
            }
            composable(Destination.HISTORY.route) {
                HistoryScreen(
                    viewModel = historyViewModel,
                    contentPadding = innerPadding,
                    onPlay = { e -> navController.openPlayer(e.fileUri, e.title, e.mimeType) },
                )
            }
            composable(Destination.SETTINGS.route) {
                SettingsScreen(viewModel = settingsViewModel, contentPadding = innerPadding)
            }
            composable(
                route = PLAYER_ROUTE,
                arguments = listOf(
                    navArgument("uri") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("mime") { type = NavType.StringType; defaultValue = "video/mp4" },
                ),
            ) { entry ->
                val args = entry.arguments
                PlayerScreen(
                    uri = args?.getString("uri").orEmpty(),
                    title = args?.getString("title").orEmpty(),
                    mimeType = args?.getString("mime") ?: "video/mp4",
                    backgroundPlayback = settings.backgroundPlayback,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
