package com.nexusstream.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bridge between the activity (which owns picture-in-picture) and the Compose UI. */
object PipController {
    private val _isInPip = MutableStateFlow(false)
    val isInPip: StateFlow<Boolean> = _isInPip.asStateFlow()

    /** True while a video is playing on the watch page; leaving the app then starts PiP. */
    @Volatile
    var canAutoEnter: Boolean = false

    /** Width / height of the playing video, so the picture-in-picture window matches it. */
    @Volatile
    var videoAspect: Float = 16f / 9f

    /** Set by the activity; starts picture-in-picture immediately. */
    @Volatile
    var enter: (() -> Unit)? = null

    internal fun setInPip(value: Boolean) {
        _isInPip.value = value
    }
}
