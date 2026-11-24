package com.echogram.app.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import com.echogram.app.data.telegram.TelegramClientManager
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@OptIn(UnstableApi::class)
class TdlibAudioDataSource(
    private val context: Context,
    private val telegram: TelegramClientManager
) : BaseDataSource(/* isNetwork = */ true) {

    private var uri: Uri? = null
    private var fileId: Int = 0
    private var chatId: Long = 0L
    private var messageId: Long = 0L
    private var totalSize: Long = 0L
    private var position: Long = 0L
    private var bytesRemaining: Long = 0L
    private var opened: Boolean = false
    private var fallbackDataSource: DataSource? = null

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri

        if (dataSpec.uri.scheme != "tdlib") {
            val delegate = DefaultDataSource(context, false)
            fallbackDataSource = delegate
            return delegate.open(dataSpec)
        }

        val trackId = dataSpec.uri.getQueryParameter("trackId")
        val offlineFile = trackId?.let { telegram.getOfflineAudioFile(it) }
        if (offlineFile != null) {
            val delegate = DefaultDataSource(context, false)
            fallbackDataSource = delegate
            return delegate.open(dataSpec.withUri(Uri.fromFile(offlineFile)))
        }

        val parsedId = dataSpec.uri.host?.toIntOrNull()
            ?: dataSpec.uri.lastPathSegment?.toIntOrNull()
            ?: throw IOException("Invalid tdlib fileId in URI: ${dataSpec.uri}")

        fileId = parsedId
        chatId = dataSpec.uri.getQueryParameter("chatId")?.toLongOrNull() ?: 0L
        messageId = dataSpec.uri.getQueryParameter("msgId")?.toLongOrNull() ?: 0L

        val resolved = try {
            runBlocking(Dispatchers.IO) {
                telegram.resolvePlayableFile(fileId, chatId, messageId)
            }
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Interrupted while resolving TDLib file $fileId")
        } catch (ce: CancellationException) {
            throw InterruptedIOException("Cancelled while resolving TDLib file $fileId")
        }

        if (resolved != null && resolved.id > 0) {
            fileId = resolved.id
        }

        if (resolved != null && resolved.local.isDownloadingCompleted && resolved.local.path.isNotBlank()) {
            val localFile = File(resolved.local.path)
            if (localFile.exists() && localFile.length() > 0L) {
                val delegate = DefaultDataSource(context, false)
                fallbackDataSource = delegate
                return delegate.open(dataSpec.withUri(Uri.fromFile(localFile)))
            }
        }

        transferInitializing(dataSpec)

        // Only trust exact file.size from TDLib (never approximate expectedSize)
        totalSize = if (resolved != null && resolved.size > 0L) resolved.size else 0L
        position = dataSpec.position.coerceAtLeast(0L)
        if (totalSize > 0L && position > totalSize) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }

        telegram.activePlaybackFileId = fileId

        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            totalSize > 0L -> (totalSize - position).coerceAtLeast(0L)
            else -> C.LENGTH_UNSET.toLong()
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val delegate = fallbackDataSource
        if (delegate != null) return delegate.read(buffer, offset, length)
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesRemaining).toInt()
        }
        if (toRead <= 0) return C.RESULT_END_OF_INPUT

        val n = try {
            runBlocking(Dispatchers.IO) {
                telegram.readAudioStreamBytes(
                    fileId = fileId,
                    expectedSize = totalSize,
                    offset = position,
                    buffer = buffer,
                    outOffset = offset,
                    maxLength = toRead,
                    chatId = chatId,
                    messageId = messageId,
                    onResolvedFileId = { freshId -> fileId = freshId }
                )
            }
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Interrupted while reading TDLib stream $fileId")
        } catch (ce: CancellationException) {
            throw InterruptedIOException("Cancelled while reading TDLib stream $fileId")
        }

        if (n == -2) {
            throw IOException(telegram.lastStreamError ?: "Ошибка загрузки аудио из Telegram")
        }
        if (n <= 0) return C.RESULT_END_OF_INPUT

        position += n
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= n
        }
        bytesTransferred(n)
        return n
    }

    override fun getResponseHeaders(): Map<String, List<String>> =
        fallbackDataSource?.responseHeaders ?: emptyMap()

    override fun getUri(): Uri? = fallbackDataSource?.uri ?: uri

    override fun close() {
        val delegate = fallbackDataSource
        fallbackDataSource = null
        uri = null
        bytesRemaining = 0L
        if (delegate != null) {
            runCatching { delegate.close() }
            return
        }
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(
        private val context: Context,
        private val telegram: TelegramClientManager
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            TdlibAudioDataSource(context.applicationContext, telegram)
    }
}
