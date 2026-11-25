package com.tgaudiobooks.app.data.telegram

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.tgaudiobooks.app.data.model.AudioTrack
import com.tgaudiobooks.app.data.model.SourceType
import com.tgaudiobooks.app.data.model.TelegramChannel

data class TelegramChatLink(
    val nativeUri: String,
    val webUrl: String? = null
)

object TelegramDeepLinks {

    private fun extractServerMessageId(rawMessageId: Long): Long =
        if (rawMessageId >= (1L shl 20)) rawMessageId shr 20 else rawMessageId

    fun buildTrackLinks(
        track: AudioTrack,
        channels: List<TelegramChannel> = emptyList()
    ): TelegramChatLink? {
        val matchedChannel = channels.firstOrNull { it.channelId == track.channelId }
        val cleanUser = track.channelUsername
            .ifBlank { matchedChannel?.username.orEmpty() }
            .trim()
            .removePrefix("@")
        val serverMsgId = extractServerMessageId(track.messageId)
        val rawChatId = track.channelId.trim()
        val sourceType = matchedChannel?.sourceType

        val isSupergroupOrChannel = rawChatId.startsWith("-100") ||
            sourceType == SourceType.CHANNEL ||
            (sourceType == SourceType.GROUP && rawChatId.startsWith("-100"))
        val cleanSupergroupId = rawChatId.removePrefix("-100").trim()

        if (isSupergroupOrChannel && cleanUser.isNotEmpty() && serverMsgId > 0L) {
            return TelegramChatLink(
                nativeUri = "tg://resolve?domain=${Uri.encode(cleanUser)}&post=$serverMsgId",
                webUrl = "https://t.me/$cleanUser/$serverMsgId"
            )
        }

        if (isSupergroupOrChannel &&
            cleanSupergroupId.isNotEmpty() &&
            cleanSupergroupId.all { it.isDigit() } &&
            serverMsgId > 0L
        ) {
            return TelegramChatLink(
                nativeUri = "tg://privatepost?channel=$cleanSupergroupId&post=$serverMsgId",
                webUrl = "https://t.me/c/$cleanSupergroupId/$serverMsgId"
            )
        }

        if (cleanUser.isNotEmpty()) {
            return TelegramChatLink(
                nativeUri = "tg://resolve?domain=${Uri.encode(cleanUser)}",
                webUrl = "https://t.me/$cleanUser"
            )
        }

        if (rawChatId.startsWith("-100") &&
            cleanSupergroupId.isNotEmpty() &&
            cleanSupergroupId.all { it.isDigit() }
        ) {
            return TelegramChatLink(
                nativeUri = "tg://privatepost?channel=$cleanSupergroupId&post=1",
                webUrl = "https://t.me/c/$cleanSupergroupId/1"
            )
        }

        val cleanUserId = rawChatId.removePrefix("user_").trim()
        if (cleanUserId.isNotEmpty() && cleanUserId.all { it.isDigit() }) {
            return TelegramChatLink(
                nativeUri = "tg://openmessage?user_id=$cleanUserId",
                webUrl = null
            )
        }

        val cleanBasicGroupId = rawChatId.removePrefix("chat_").removePrefix("-").trim()
        if (rawChatId.startsWith("-") &&
            !rawChatId.startsWith("-100") &&
            cleanBasicGroupId.isNotEmpty() &&
            cleanBasicGroupId.all { it.isDigit() }
        ) {
            return TelegramChatLink(
                nativeUri = "tg://openmessage?chat_id=$cleanBasicGroupId",
                webUrl = null
            )
        }

        return null
    }

    fun buildChannelLinks(
        channel: TelegramChannel,
        fallbackTrack: AudioTrack? = null
    ): TelegramChatLink? {
        val cleanUser = channel.username.trim().removePrefix("@")
        if (cleanUser.isNotEmpty()) {
            return TelegramChatLink(
                nativeUri = "tg://resolve?domain=${Uri.encode(cleanUser)}",
                webUrl = "https://t.me/$cleanUser"
            )
        }

        val rawChatId = channel.channelId.trim()
        val cleanSupergroupId = rawChatId.removePrefix("-100").trim()
        if (rawChatId.startsWith("-100") &&
            cleanSupergroupId.isNotEmpty() &&
            cleanSupergroupId.all { it.isDigit() }
        ) {
            val postId = fallbackTrack
                ?.let { extractServerMessageId(it.messageId) }
                ?.takeIf { it > 0L }
                ?: 1L
            return TelegramChatLink(
                nativeUri = "tg://privatepost?channel=$cleanSupergroupId&post=$postId",
                webUrl = "https://t.me/c/$cleanSupergroupId/$postId"
            )
        }

        val cleanUserId = rawChatId.removePrefix("user_").trim()
        if (cleanUserId.isNotEmpty() && cleanUserId.all { it.isDigit() }) {
            return TelegramChatLink(
                nativeUri = "tg://openmessage?user_id=$cleanUserId",
                webUrl = null
            )
        }

        val cleanBasicGroupId = rawChatId.removePrefix("chat_").removePrefix("-").trim()
        if (rawChatId.startsWith("-") &&
            !rawChatId.startsWith("-100") &&
            cleanBasicGroupId.isNotEmpty() &&
            cleanBasicGroupId.all { it.isDigit() }
        ) {
            return TelegramChatLink(
                nativeUri = "tg://openmessage?chat_id=$cleanBasicGroupId",
                webUrl = null
            )
        }

        return null
    }

    fun openTelegramChat(context: Context, link: TelegramChatLink?): Boolean {
        if (link == null) {
            Toast.makeText(context, "Ссылка на чат Telegram недоступна", Toast.LENGTH_SHORT).show()
            return false
        }
        val candidates = listOfNotNull(link.nativeUri, link.webUrl)
        for (url in candidates) {
            val launched = runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return true
        }
        Toast.makeText(context, "Не удалось открыть Telegram", Toast.LENGTH_SHORT).show()
        return false
    }
}
