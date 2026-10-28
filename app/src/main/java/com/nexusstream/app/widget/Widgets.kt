package com.nexusstream.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.nexusstream.app.MainActivity
import com.nexusstream.app.R
import com.nexusstream.app.media.MediaPlaybackService

/** Now-playing widget: artwork, title and artist, with previous / play-pause / next. */
class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
    }

    companion object {
        private var title: String? = null
        private var artist: String = ""
        private var playing = false
        private var art: Bitmap? = null

        /** Called by the playback service whenever the item or play state changes. */
        fun update(context: Context, newTitle: String?, newArtist: String, isPlaying: Boolean, artwork: Bitmap?) {
            title = newTitle
            artist = newArtist
            playing = isPlaying
            if (artwork != null || newTitle == null) art = artwork
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
            views.setTextViewText(R.id.widget_title, title ?: context.getString(R.string.widget_nothing_playing))
            views.setTextViewText(R.id.widget_artist, artist)
            val bitmap = art
            if (bitmap != null) views.setImageViewBitmap(R.id.widget_art, bitmap) else views.setImageViewResource(R.id.widget_art, R.drawable.ic_launcher_foreground)
            views.setImageViewResource(R.id.widget_play, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            views.setOnClickPendingIntent(R.id.widget_prev, serviceIntent(context, 11, MediaPlaybackService.ACTION_PREV))
            views.setOnClickPendingIntent(
                R.id.widget_play,
                serviceIntent(context, 12, if (playing) MediaPlaybackService.ACTION_PAUSE else MediaPlaybackService.ACTION_PLAY)
            )
            views.setOnClickPendingIntent(R.id.widget_next, serviceIntent(context, 13, MediaPlaybackService.ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context, 14, null))
            manager.updateAppWidget(ids, views)
        }

        private fun serviceIntent(context: Context, code: Int, action: String): PendingIntent {
            val intent = Intent(context, MediaPlaybackService::class.java).setAction(action)
            return PendingIntent.getForegroundService(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        internal fun openApp(context: Context, code: Int, destination: String?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(if (destination != null) MainActivity.ACTION_SHORTCUT else Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (destination != null) intent.putExtra(MainActivity.EXTRA_DESTINATION, destination)
            return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}

/** Search bar widget: opens search in the app. */
class SearchWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_search)
        views.setOnClickPendingIntent(R.id.widget_search_root, NowPlayingWidget.openApp(context, 21, "search"))
        manager.updateAppWidget(ids, views)
    }
}
