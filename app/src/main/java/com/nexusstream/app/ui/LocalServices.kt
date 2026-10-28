package com.nexusstream.app.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.nexusstream.app.media.MediaPlaybackService

/** The bound playback service, for menus deep in the tree ("Play next", "Add to queue"). Null until bound. */
val LocalPlaybackService = compositionLocalOf<MediaPlaybackService?> { null }

/** App navigation, for menus that open pages ("Go to album", "Go to artist"). */
val LocalNav = staticCompositionLocalOf<YouTubeNav?> { null }
