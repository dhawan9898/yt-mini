package com.nexusstream.app.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.nexusstream.app.media.MediaPlaybackService
import com.nexusstream.app.model.MediaItemModel

/** The bound playback service, for menus deep in the tree ("Play next", "Add to queue"). Null until bound. */
val LocalPlaybackService = compositionLocalOf<MediaPlaybackService?> { null }

/** App navigation, for menus that open pages ("Go to album", "Go to artist"). */
val LocalNav = staticCompositionLocalOf<YouTubeNav?> { null }

/**
 * Plays a list in order, starting at the given video ("Play all", "Shuffle"). Tapping one video
 * elsewhere plays it alone and continues with related videos; these buttons must keep the list.
 */
val LocalPlayInOrder = staticCompositionLocalOf<((MediaItemModel, List<MediaItemModel>) -> Unit)?> { null }
