package com.mh.videodownloader

import android.app.Application
import com.mh.videodownloader.service.DownloadNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DownloadNotifier.createChannels(this)

        appScope.launch {
            // Unpacking python/ffmpeg takes ~10s on first launch only; start it immediately so
            // the user is rarely the one waiting for it.
            container.engine.ensureInitialized()
            runCatching { container.autoUpdateEngineIfDue() }
        }
    }
}
