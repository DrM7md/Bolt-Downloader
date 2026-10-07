package com.mh.videodownloader

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.mh.videodownloader.data.storage.VideoFrame
import com.mh.videodownloader.data.storage.VideoFrameFetcher
import com.mh.videodownloader.service.DownloadNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * One image loader for the whole app. Local videos get their thumbnails from
     * [VideoFrameFetcher] (extracted once, cached on disk), and crossfade is off: images
     * should just appear.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameFetcher.Factory(context))
                add(VideoFrameFetcher.Key(), VideoFrame::class)
            }
            .crossfade(false)
            .build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DownloadNotifier.createChannels(this)

        appScope.launch {
            // Unpacking python/ffmpeg takes ~10s on first launch only; start it immediately so
            // the user is rarely the one waiting for it.
            container.engine.ensureInitialized()
            runCatching { container.housekeeping() }
            runCatching { container.autoUpdateEngineIfDue() }
        }
    }
}
