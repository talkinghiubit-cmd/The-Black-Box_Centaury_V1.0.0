package com.example.security

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicReference

/**
 * Automatic lifecycle & post-share cleaner for temporary exported/shared artifacts
 * (`cacheDir/exports`, `cacheDir/shares`, and `filesDir/snapshots`).
 *
 * Ensures temporary HTML vaults, encrypted share capsules, and staged backup snapshots
 * are overwritten and purged once the Android Share Sheet finishes, on vault lock,
 * on app backgrounding (when no share chooser is active), on startup, and on vault wipe.
 */
object ExportFileCleaner {

    private const val TAG = "ExportFileCleaner"
    private const val MAX_WIPE_BYTES = 16L * 1024L * 1024L

    private val activeShareFileRef = AtomicReference<String?>(null)

    @Volatile
    private var shareInFlight: Boolean = false

    fun markShareStarted(file: File) {
        activeShareFileRef.set(safeCanonicalPath(file))
        shareInFlight = true
    }

    fun markShareFinished() {
        shareInFlight = false
    }

    fun clearActiveShare(file: File? = null) {
        if (file == null) {
            activeShareFileRef.set(null)
            shareInFlight = false
            return
        }
        val targetPath = safeCanonicalPath(file)
        activeShareFileRef.compareAndSet(targetPath, null)
        if (activeShareFileRef.get() == null) {
            shareInFlight = false
        }
    }

    fun isShareInFlight(): Boolean = shareInFlight

    fun getExportDirectories(context: Context): List<File> = listOf(
        File(context.cacheDir, "exports"),
        File(context.cacheDir, "shares"),
        File(context.filesDir, "snapshots")
    )

    /**
     * Best-effort zero-overwrite and deletion of a temporary exported file.
     */
    fun secureDeleteFile(file: File?): Boolean {
        if (file == null || !file.exists()) return false
        return try {
            if (file.isFile) {
                val length = file.length()
                if (length in 1L..MAX_WIPE_BYTES) {
                    runCatching {
                        RandomAccessFile(file, "rw").use { raf ->
                            val zeros = ByteArray(8192)
                            var remaining = length
                            raf.seek(0L)
                            while (remaining > 0L) {
                                val chunk = minOf(remaining, zeros.size.toLong()).toInt()
                                raf.write(zeros, 0, chunk)
                                remaining -= chunk
                            }
                            raf.fd.sync()
                        }
                    }
                }
            }
            val deleted = file.delete()
            if (!deleted && file.exists()) {
                Log.w(TAG, "Unable to delete temporary export file: ${file.name}")
            }
            deleted
        } catch (e: Exception) {
            Log.w(TAG, "Error purging temporary export file: ${e.message}")
            runCatching { file.delete() }.getOrDefault(false)
        }
    }

    /**
     * Sweeps exported/shared directories. Files whose age >= [maxAgeMs] (or any `.tmp` file)
     * are overwritten and deleted. Passing [maxAgeMs] = 0L deletes all files immediately.
     */
    fun cleanupExports(
        context: Context,
        maxAgeMs: Long = 0L,
        excludeFile: File? = null
    ): Int {
        val excludedPath = excludeFile?.let(::safeCanonicalPath)
            ?: if (shareInFlight) activeShareFileRef.get() else null
        val now = System.currentTimeMillis()
        var deletedCount = 0

        for (dir in getExportDirectories(context)) {
            if (!dir.exists() || !dir.isDirectory) continue
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (!child.isFile) continue
                val childPath = safeCanonicalPath(child)
                if (excludedPath != null && childPath == excludedPath) continue

                val isTemp = child.name.endsWith(".tmp")
                val ageMs = (now - child.lastModified()).coerceAtLeast(0L)
                if (isTemp || maxAgeMs <= 0L || ageMs >= maxAgeMs) {
                    if (secureDeleteFile(child)) {
                        deletedCount++
                    }
                }
            }
        }
        return deletedCount
    }

    /**
     * Purges all temporary exports except any file currently held open by an in-flight Share Sheet.
     */
    fun cleanupIdleExports(context: Context): Int {
        return cleanupExports(context = context, maxAgeMs = 0L, excludeFile = null)
    }

    /**
     * Unconditionally purges every exported/shared file and clears share tracking state.
     */
    fun cleanupAllExportsUnconditionally(context: Context): Int {
        clearActiveShare(null)
        return cleanupExports(context = context, maxAgeMs = 0L, excludeFile = null)
    }

    private fun safeCanonicalPath(file: File): String {
        return try {
            file.canonicalPath
        } catch (_: Exception) {
            file.absolutePath
        }
    }
}
