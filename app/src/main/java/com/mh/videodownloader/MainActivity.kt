package com.mh.videodownloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mh.videodownloader.data.prefs.AppSettings
import com.mh.videodownloader.domain.SourceDetector
import com.mh.videodownloader.ui.AppRoot
import com.mh.videodownloader.ui.home.HomeViewModel
import com.mh.videodownloader.ui.theme.VideoDownloaderTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : AppCompatActivity() {

    /** Links arriving from other apps via the share sheet. */
    private val sharedLink = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        val container = (application as App).container

        setContent {
            val settings by container.settings.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            VideoDownloaderTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val homeViewModel: HomeViewModel = viewModel()
                    val incoming by sharedLink.collectAsStateWithLifecycle()

                    LaunchedEffect(incoming) {
                        incoming?.let { text ->
                            // Sharing straight from YouTube or TikTok is the fast path: fill the
                            // field and start analysing without another tap.
                            homeViewModel.submitExternalUrl(
                                value = text,
                                messageRes = R.string.shared_link_received,
                                autoAnalyze = true,
                            )
                            sharedLink.value = null
                        }
                    }

                    RequestRuntimePermissions()
                    AppRoot(homeViewModel = homeViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("text/") != true) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        sharedLink.value = SourceDetector.firstUrlIn(text) ?: text
    }
}

/**
 * Notifications carry the download progress, and on Android 8–9 public storage still needs the
 * legacy write permission. Both are asked for once, on first launch.
 */
@Composable
private fun RequestRuntimePermissions() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (wanted.isNotEmpty()) launcher.launch(wanted.toTypedArray())
    }
}
