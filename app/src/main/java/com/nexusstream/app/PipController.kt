package com.nexusstream.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bridge between the activity (which owns picture-in-picture) and the Compose UI. */
object PipController {
    private val _isInPip = MutableStateFlow(false)
    val isInPip: StateFlow<Boolean> = _isInPip.asStateFlow()

    /** True while a video is playing (or loading) on the watch page; leaving the app then starts PiP. */
    @Volatile
    var canAutoEnter: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            onEligibilityChanged?.invoke(value)
        }

    /** Set by the activity: keeps Android's auto-enter PiP flag (Android 12+) in step with [canAutoEnter]. */
    @Volatile
    var onEligibilityChanged: ((Boolean) -> Unit)? = null

    /** Width / height of the playing video, so the picture-in-picture window matches it. */
    @Volatile
    var videoAspect: Float = 16f / 9f
        set(value) {
            if (field == value) return
            field = value
            if (canAutoEnter) onEligibilityChanged?.invoke(true)
        }

    /** Set by the activity; starts picture-in-picture immediately. */
    @Volatile
    var enter: (() -> Unit)? = null

    /** Emits when the PiP window is closed (swiped away / X): playback stops, as on YouTube. */
    val dismissed = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    internal fun setInPip(value: Boolean) {
        _isInPip.value = value
    }
}
