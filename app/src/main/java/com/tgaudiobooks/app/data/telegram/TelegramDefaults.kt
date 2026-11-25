package com.tgaudiobooks.app.data.telegram

import android.content.Context
import com.tgaudiobooks.app.BuildConfig

object TelegramDefaults {
    fun getApiId(@Suppress("UNUSED_PARAMETER") context: Context): Int =
        BuildConfig.TG_API_ID

    fun getApiHash(@Suppress("UNUSED_PARAMETER") context: Context): String =
        BuildConfig.TG_API_HASH.trim()
}
