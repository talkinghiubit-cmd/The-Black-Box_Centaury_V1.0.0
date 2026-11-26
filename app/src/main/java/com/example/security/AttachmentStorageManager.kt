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

    /**
     * Encrypts and writes raw byte array to internal storage.
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

        val (ciphertext, iv) = BlackBoxSecurity.encryptFileBytes(rawBytes, secretKey)
        // Store 12-byte IV at the beginning of the file, followed by ciphertext
        val combined = iv + ciphertext
        file.writeBytes(combined)

        "$ATTACHMENT_DIR/$fileId.enc"
    }

    /**
     * Reads and decrypts stored file bytes from internal storage.
     */
    suspend fun readDecryptedFile(
        context: Context,
        relPath: String,
        secretKey: SecretKey
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, relPath)
            if (!file.exists()) return@withContext null

            val combined = file.readBytes()
            if (combined.size <= 12) return@withContext null

            val iv = combined.copyOfRange(0, 12)
            val ciphertext = combined.copyOfRange(12, combined.size)
            BlackBoxSecurity.decryptFileBytes(ciphertext, iv, secretKey)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Deletes physical encrypted file.
     */
    suspend fun deleteFile(context: Context, relPath: String) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, relPath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
