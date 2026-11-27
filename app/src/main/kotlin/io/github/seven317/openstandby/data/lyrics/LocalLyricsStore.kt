package io.github.seven317.openstandby.data.lyrics

import android.content.Context
import android.net.Uri
import io.github.seven317.openstandby.domain.lyrics.LrcParser
import io.github.seven317.openstandby.domain.lyrics.Lyrics
import io.github.seven317.openstandby.domain.lyrics.YrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

internal class LocalLyricsStore(private val context: Context) {
    private val changes = Mutex()

    suspend fun lookup(key: String): Lyrics? = withContext(Dispatchers.IO) {
        changes.withLock {
            try {
                file(key).inputStream().use { it.readLyricsText() }?.let(::parseLocalLyrics)
            } catch (_: IOException) {
                null
            }
        }
    }

    suspend fun importFile(key: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        changes.withLock {
            try {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readLyricsText() }
                if (text == null || parseLocalLyrics(text) == null) return@withLock false
                val encoded = text.toByteArray(Charsets.UTF_8)
                if (encoded.size > MAX_LYRICS_BYTES) return@withLock false
                val destination = file(key)
                val directory = checkNotNull(destination.parentFile)
                if (!directory.isDirectory && !directory.mkdirs()) return@withLock false
                val pending = File.createTempFile("lyrics", ".lrc", directory)
                try {
                    pending.writeBytes(encoded)
                    Files.move(pending.toPath(), destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                } finally {
                    pending.delete()
                }
                true
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            } catch (_: IllegalArgumentException) {
                false
            }
        }
    }

    suspend fun remove(key: String): Boolean = withContext(Dispatchers.IO) {
        changes.withLock {
            val destination = file(key)
            !destination.exists() || destination.delete()
        }
    }

    private fun file(key: String): File = File(context.noBackupFilesDir, "local-lyrics/$key.lrc")
}

internal fun localLyricsKey(source: String, mediaId: String?, title: String?, artist: String?, album: String?): String? {
    val identity = when {
        !mediaId.isNullOrBlank() -> listOf(source, "id", mediaId, title.orEmpty(), artist.orEmpty())
        !title.isNullOrBlank() && !artist.isNullOrBlank() -> listOf(source, "metadata", title, artist, album.orEmpty())
        else -> return null
    }
    val input = identity.joinToString("") { "${it.length}:$it" }
    return MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}

internal fun parseLocalLyrics(text: String): Lyrics? {
    val clean = text.trim()
    if (clean.isEmpty() || clean.any { it.isISOControl() && it !in "\t\r\n" }) return null
    val lrc = LrcParser.parse(clean)
    if (lrc.any { it.text.isNotBlank() }) return Lyrics.Timed(lrc)
    val yrc = YrcParser.parse(clean)
    return if (yrc.any { it.text.isNotBlank() }) Lyrics.Timed(yrc) else Lyrics.Plain(clean)
}

private const val MAX_LYRICS_BYTES = 256 * 1024

private fun InputStream.readLyricsText(): String? {
    val buffer = ByteArray(8_192)
    val data = ByteArrayOutputStream()
    var read = read(buffer)
    while (read > 0) {
        if (data.size() + read > MAX_LYRICS_BYTES) return null
        data.write(buffer, 0, read)
        read = read(buffer)
    }
    val bytes = data.toByteArray()
    val charset = if (bytes.size >= 2 && (
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ||
            bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()
        )) Charsets.UTF_16 else Charsets.UTF_8
    return try {
        charset.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
    } catch (_: CharacterCodingException) {
        null
    }
}
