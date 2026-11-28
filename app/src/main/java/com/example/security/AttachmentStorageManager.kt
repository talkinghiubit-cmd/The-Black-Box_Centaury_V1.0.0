package com.example.security

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.crypto.SecretKey

object AttachmentStorageManager {

    private const val ATTACHMENT_DIR = "vault_attachments"

    private fun getStorageDir(context: Context): File {
        val dir = File(context.filesDir, ATTACHMENT_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun resolveSafeAttachmentFile(context: Context, relPath: String): File? {
        val storageDir = getStorageDir(context).canonicalFile
        val target = File(context.filesDir, relPath).canonicalFile
        val storagePrefix = storageDir.path.let { if (it.endsWith(File.separator)) it else it + File.separator }
        return if (target.path.startsWith(storagePrefix)) target else null
    }

    /**
     * Encrypts and atomically writes raw byte array to internal storage.
     * Returns the relative storage path inside the attachment directory.
     */
    suspend fun saveEncryptedFile(
        context: Context,
        rawBytes: ByteArray,
        secretKey: SecretKey
    ): String = withContext(Dispatchers.IO) {
        val dir = getStorageDir(context)
        val fileId = UUID.randomUUID().toString()
        val file = File(dir, "$fileId.enc")
        val tempFile = File(dir, "$fileId.enc.tmp")

        val (ciphertext, iv) = BlackBoxSecurity.encryptFileBytes(rawBytes, secretKey)
        // Store 12-byte IV at the beginning of the file, followed by ciphertext
        val combined = iv + ciphertext
        try {
            tempFile.writeBytes(combined)
            if (!tempFile.renameTo(file)) {
                file.writeBytes(combined)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        } finally {
            combined.fill(0)
        }

        "$ATTACHMENT_DIR/$fileId.enc"
    }

    /**
     * Reads and decrypts stored file bytes from internal storage after validating path confinement.
     */
    suspend fun readDecryptedFile(
        context: Context,
        relPath: String,
        secretKey: SecretKey
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val file = resolveSafeAttachmentFile(context, relPath) ?: return@withContext null
            if (!file.exists() || !file.isFile) return@withContext null

            val combined = file.readBytes()
            if (combined.size < 12 + 16) return@withContext null

            val iv = combined.copyOfRange(0, 12)
            val ciphertext = combined.copyOfRange(12, combined.size)
            BlackBoxSecurity.decryptFileBytes(ciphertext, iv, secretKey)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Deletes physical encrypted file safely inside storage directory.
     */
    suspend fun deleteFile(context: Context, relPath: String) = withContext(Dispatchers.IO) {
        try {
            val file = resolveSafeAttachmentFile(context, relPath) ?: return@withContext
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * Permanently deletes all encrypted attachment blobs from internal storage.
     */
    suspend fun deleteAllAttachments(context: Context) = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, ATTACHMENT_DIR)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (_: Exception) {
            // ignore
        }
    }
}
