package com.tgaudiobooks.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.RemoteViews
import com.tgaudiobooks.app.MainActivity
import com.tgaudiobooks.app.R
import com.tgaudiobooks.app.player.AudiobookPlayerService
import java.io.File

class EchoGramWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, null).orEmpty().ifBlank { "EchoGram" }
        val author = prefs.getString(KEY_AUTHOR, null).orEmpty().ifBlank { "Аудиокниги" }
        val coverPath = prefs.getString(KEY_COVER_PATH, null)
        val isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)

        for (id in appWidgetIds) {
            val views = buildRemoteViews(context, title, author, coverPath, isPlaying)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    companion object {
        private const val PREFS_NAME = "echogram_widget"
        private const val KEY_TITLE = "title"
        private const val KEY_AUTHOR = "author"
        private const val KEY_COVER_PATH = "cover_path"
        private const val KEY_IS_PLAYING = "is_playing"

        fun updateAll(
            context: Context,
            title: String?,
            author: String?,
            coverPath: String?,
            isPlaying: Boolean
        ) {
            val safeTitle = title?.trim().orEmpty().ifBlank { "EchoGram" }
            val safeAuthor = author?.trim().orEmpty().ifBlank { "Аудиокниги" }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_TITLE, safeTitle)
                .putString(KEY_AUTHOR, safeAuthor)
                .putString(KEY_COVER_PATH, coverPath ?: "")
                .putBoolean(KEY_IS_PLAYING, isPlaying)
                .apply()

            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, EchoGramWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildRemoteViews(context, safeTitle, safeAuthor, coverPath, isPlaying)
            for (id in ids) {
                manager.updateAppWidget(id, views)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            title: String,
            author: String,
            coverPath: String?,
            isPlaying: Boolean
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.echogram_widget)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_author, author)
            views.setImageViewResource(
                R.id.widget_btn_play,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )

            val bitmap = decodeWidgetCoverBitmap(coverPath)
            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widget_cover, bitmap)
            } else {
                views.setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher_classic)
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                100,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPending)

            views.setOnClickPendingIntent(
                R.id.widget_btn_prev,
                servicePendingIntent(context, 101, AudiobookPlayerService.ACTION_WIDGET_PREV)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_play,
                servicePendingIntent(context, 102, AudiobookPlayerService.ACTION_WIDGET_PLAY_PAUSE)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_next,
                servicePendingIntent(context, 103, AudiobookPlayerService.ACTION_WIDGET_NEXT)
            )
            return views
        }

        private fun servicePendingIntent(
            context: Context,
            requestCode: Int,
            action: String
        ): PendingIntent {
            val intent = Intent(context, AudiobookPlayerService::class.java).apply {
                this.action = action
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, requestCode, intent, flags)
            } else {
                PendingIntent.getService(context, requestCode, intent, flags)
            }
        }

        private fun decodeWidgetCoverBitmap(coverPath: String?): Bitmap? {
            if (coverPath.isNullOrBlank()) return null
            val file = File(coverPath)
            if (!file.exists()) return null
            return runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                var sample = 1
                val maxDim = maxOf(opts.outWidth, opts.outHeight)
                while (maxDim / sample > 256) {
                    sample *= 2
                }
                BitmapFactory.Options().apply { inSampleSize = sample }.let {
                    BitmapFactory.decodeFile(file.absolutePath, it)
                }
            }.getOrNull()
        }
    }
}
