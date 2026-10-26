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

    /** Set by the activity; starts picture-in-picture immediately. */
    @Volatile
    var enter: (() -> Unit)? = null

    internal fun setInPip(value: Boolean) {
        _isInPip.value = value
    }
}
