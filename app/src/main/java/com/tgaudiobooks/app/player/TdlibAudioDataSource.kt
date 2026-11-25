package com.tgaudiobooks.app.player

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
import com.tgaudiobooks.app.data.telegram.TelegramRepository
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Custom Media3 DataSource that streams Telegram TDLib audio byte ranges progressively
 * without waiting for the full audiobook file to download, and delegates directly to local
 * file I/O when a track is already cached offline or completely downloaded in TDLib.
 */
@OptIn(UnstableApi::class)
class TdlibAudioDataSource(
    private val context: Context,
    private val telegramRepo: TelegramRepository
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

        val trackIdParam = dataSpec.uri.getQueryParameter("trackId")
        val offlineFile = trackIdParam?.let { telegramRepo.getOfflineAudioFile(it) }
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

        val resolvedInfo = try {
            runBlocking(Dispatchers.IO) {
                telegramRepo.resolvePlayableFile(fileId, chatId, messageId)
            }
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Interrupted while resolving TDLib file $fileId")
        } catch (ce: CancellationException) {
            throw InterruptedIOException("Cancelled while resolving TDLib file $fileId")
        }

        if (resolvedInfo != null && resolvedInfo.id > 0) {
            fileId = resolvedInfo.id
        }

        // Fast path: TDLib already has the complete file on disk — read directly via local file I/O
        if (resolvedInfo != null &&
            resolvedInfo.local.isDownloadingCompleted &&
            resolvedInfo.local.path.isNotBlank()
        ) {
            val localFile = File(resolvedInfo.local.path)
            if (localFile.exists() && localFile.length() > 0L) {
                val delegate = DefaultDataSource(context, false)
                fallbackDataSource = delegate
                return delegate.open(dataSpec.withUri(Uri.fromFile(localFile)))
            }
        }

        transferInitializing(dataSpec)

        // Only trust exact file.size from TDLib (never approximate expectedSize, which breaks EOF/seeks)
        totalSize = if (resolvedInfo != null && resolvedInfo.size > 0L) {
            resolvedInfo.size
        } else {
            0L
        }

        position = dataSpec.position.coerceAtLeast(0L)
        if (totalSize > 0L && position > totalSize) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }

        telegramRepo.activePlaybackFileId = fileId

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
        if (delegate != null) {
            return delegate.read(buffer, offset, length)
        }
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesRemaining).toInt()
        }
        if (toRead <= 0) return C.RESULT_END_OF_INPUT

        val read = try {
            runBlocking(Dispatchers.IO) {
                telegramRepo.readAudioStreamBytes(
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
            throw InterruptedIOException("Interrupted while reading TDLib stream for fileId=$fileId")
        } catch (ce: CancellationException) {
            throw InterruptedIOException("Cancelled while reading TDLib stream for fileId=$fileId")
        }

        if (read == -2) {
            val detail = telegramRepo.lastStreamError?.takeIf { it.isNotBlank() }
            throw IOException(detail ?: "Telegram stream stalled or failed for fileId=$fileId")
        }
        if (read <= 0) {
            return C.RESULT_END_OF_INPUT
        }

        position += read
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= read
        }
        bytesTransferred(read)
        return read
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        val delegate = fallbackDataSource
        if (delegate != null) {
            return delegate.responseHeaders
        }
        // Return emptyMap() so DefaultExtractorsFactory uses its canonical magic-byte sniff order
        // (FLAC/OGG/MP4/MKV/ADTS/MP3/WAV) instead of mislabeled Telegram MIME types hijacking sniffing.
        return emptyMap()
    }

    override fun getUri(): Uri? = fallbackDataSource?.uri ?: uri

    override fun close() {
        val delegate = fallbackDataSource
        fallbackDataSource = null
        uri = null
        if (delegate != null) {
            delegate.close()
            return
        }
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(
        private val context: Context,
        private val telegramRepo: TelegramRepository
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            TdlibAudioDataSource(context.applicationContext, telegramRepo)
    }
}
