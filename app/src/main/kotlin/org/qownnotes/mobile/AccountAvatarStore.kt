package org.qownnotes.mobile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.qownnotes.mobile.core.Account

internal class AccountAvatarStore(
    application: Application,
    private val fetch: suspend (Account) -> ByteArray?
) {
    private val directory = File(application.filesDir, "account-avatars")
    private val memory = mutableMapOf<String, Bitmap>()
    private val mutexes = mutableMapOf<String, Mutex>()

    suspend fun load(account: Account): Bitmap? = withContext(Dispatchers.IO) {
        synchronized(memory) {
            memory[account.id]?.let { return@withContext it }
        }
        mutexFor(account.id).withLock {
            synchronized(memory) {
                memory[account.id]?.let { return@withLock it }
            }
            val cached = runCatching { avatarFile(account.id).takeIf(File::isFile)?.readBytes() }
                .getOrNull()
            val cachedBitmap = decode(cached)
            if (cachedBitmap != null) {
                synchronized(memory) { memory[account.id] = cachedBitmap }
                return@withLock cachedBitmap
            }
            val downloaded = runCatching { fetch(account) }.getOrNull()
            val downloadedBitmap = decode(downloaded)
            if (downloaded != null && downloadedBitmap != null) {
                runCatching {
                    directory.mkdirs()
                    avatarFile(account.id).writeBytes(downloaded)
                }
            }
            if (downloadedBitmap != null) {
                synchronized(memory) { memory[account.id] = downloadedBitmap }
            }
            downloadedBitmap
        }
    }

    suspend fun remove(accountId: String) = withContext(Dispatchers.IO) {
        synchronized(memory) { memory.remove(accountId) }
        synchronized(mutexes) { mutexes.remove(accountId) }
        avatarFile(accountId).delete()
    }

    private fun mutexFor(accountId: String): Mutex =
        synchronized(mutexes) { mutexes.getOrPut(accountId, ::Mutex) }

    private fun avatarFile(accountId: String) = File(directory, accountId)

    private fun decode(bytes: ByteArray?): Bitmap? =
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
}
