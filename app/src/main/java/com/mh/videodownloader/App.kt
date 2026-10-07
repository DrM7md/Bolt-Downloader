package com.mh.videodownloader

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
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
     * One image loader for the whole app. Adds a video-frame decoder so files picked from a
     * device folder get real thumbnails, and turns off crossfade: images should just appear.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
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
