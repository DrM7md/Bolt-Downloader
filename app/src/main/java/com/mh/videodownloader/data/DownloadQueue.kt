package com.mh.videodownloader.data

import com.mh.videodownloader.domain.DownloadState
import com.mh.videodownloader.domain.DownloadTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for what is downloading right now.
 *
 * The service writes to it, the UI reads from it. Keeping the state here rather than inside the
 * service means the screen can be destroyed and rebuilt without losing a running download.
 */
class DownloadQueue {

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    val activeTasks = tasks.map { list -> list.filter { it.state.isActive } }

    fun enqueue(task: DownloadTask) {
        _tasks.value = _tasks.value + task
    }

    fun nextQueued(): DownloadTask? = _tasks.value.firstOrNull { it.state == DownloadState.QUEUED }

    fun find(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    fun hasPendingWork(): Boolean = _tasks.value.any { it.state.isActive }

    fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
    }

    fun remove(id: String) {
        _tasks.value = _tasks.value.filterNot { it.id == id }
    }

    fun clearFinished() {
        _tasks.value = _tasks.value.filter { it.state.isActive }
    }
}
