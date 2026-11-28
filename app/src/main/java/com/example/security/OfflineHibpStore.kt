package com.example.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.Reader
import java.util.Locale

data class OfflineHibpStats(
    val cachedPrefixCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val lastUpdatedAt: Long? = null
)

data class OfflineHibpImportResult(
    val sourceFileName: String,
    val importedEntries: Int,
    val updatedPrefixes: Int,
    val skippedLines: Int
)

/**
 * Persistent on-device cache and offline dataset store for HaveIBeenPwned k-Anonymity ranges.
 *
 * Only anonymous 5-character SHA-1 prefix files (`{PREFIX}.range`) containing public 35-char
 * suffixes and exposure counts are stored locally. Plaintext passwords and account identifiers
 * are never written to this cache.
 */
object OfflineHibpStore {

    private const val CACHE_DIR_NAME = "hibp_cache"
    private const val DEFAULT_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L // 7 days
    private const val MAX_AUTO_CACHED_FILES = 4_096
    const val MAX_IMPORT_BYTES = 50L * 1024L * 1024L // 50 MB
    const val MAX_IMPORT_LINES = 500_000

    private val PREFIX_REGEX = Regex("^[0-9A-F]{5}$")
    private val FULL_SHA1_REGEX = Regex("^[0-9A-F]{40}$")
    private val SUFFIX_SHA1_REGEX = Regex("^[0-9A-F]{35}$")
    private val cacheLock = Any()

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun getCacheDir(context: Context): File {
        val dir = File(context.applicationContext.filesDir, CACHE_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun prefixFile(context: Context, prefix: String): File? {
        val normalized = prefix.trim().uppercase(Locale.US)
        if (!PREFIX_REGEX.matches(normalized)) return null
        return File(getCacheDir(context), "$normalized.range")
    }

    fun readCachedRange(
        context: Context,
        prefix: String,
        allowExpired: Boolean = true,
        maxAgeMs: Long = DEFAULT_CACHE_TTL_MS
    ): String? {
        val file = prefixFile(context, prefix) ?: return null
        synchronized(cacheLock) {
            if (!file.exists() || !file.isFile) return null
            if (!allowExpired) {
                val age = System.currentTimeMillis() - file.lastModified()
                if (age > maxAgeMs) return null
            }
            return try {
                file.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun writeCachedRange(
        context: Context,
        prefix: String,
        rangeBody: String,
        enforceAutoLimit: Boolean = true
    ) {
        val file = prefixFile(context, prefix) ?: return
        synchronized(cacheLock) {
            try {
                atomicWriteText(file, rangeBody)
                if (enforceAutoLimit) {
                    pruneOldestIfNeeded(getCacheDir(context), MAX_AUTO_CACHED_FILES)
                }
            } catch (_: Exception) {
                // Best-effort cache write; never fail verification on disk cache write error
            }
        }
    }

    private fun atomicWriteText(target: File, content: String) {
        val parent = target.parentFile ?: return
        if (!parent.exists()) parent.mkdirs()
        val tempFile = File(parent, "${target.name}.tmp")
        try {
            tempFile.writeText(content, Charsets.UTF_8)
            if (target.exists()) {
                target.delete()
            }
            if (!tempFile.renameTo(target)) {
                target.writeText(content, Charsets.UTF_8)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun pruneOldestIfNeeded(dir: File, maxFiles: Int) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".range") } ?: return
        if (files.size <= maxFiles) return
        val excess = files.size - maxFiles
        files.sortedBy { it.lastModified() }
            .take(excess)
            .forEach { it.delete() }
    }

    fun getStats(context: Context): OfflineHibpStats {
        synchronized(cacheLock) {
            val dir = getCacheDir(context)
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".range") } ?: emptyArray()
            if (files.isEmpty()) return OfflineHibpStats()
            var totalBytes = 0L
            var newest = 0L
            for (file in files) {
                totalBytes += file.length()
                if (file.lastModified() > newest) newest = file.lastModified()
            }
            return OfflineHibpStats(
                cachedPrefixCount = files.size,
                totalSizeBytes = totalBytes,
                lastUpdatedAt = if (newest > 0L) newest else null
            )
        }
    }

    fun clearCache(context: Context) {
        synchronized(cacheLock) {
            val dir = getCacheDir(context)
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    fun pruneStaleCache(context: Context, maxAgeMs: Long = 30L * 24L * 60L * 60L * 1000L): Int {
        synchronized(cacheLock) {
            val dir = getCacheDir(context)
            val now = System.currentTimeMillis()
            var removed = 0
            dir.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                val isOrphanTmp = file.name.endsWith(".tmp")
                val isStaleRange = file.name.endsWith(".range") && (now - file.lastModified()) > maxAgeMs
                if ((isOrphanTmp || isStaleRange) && file.delete()) {
                    removed++
                }
            }
            return removed
        }
    }

    suspend fun importFromUri(context: Context, uri: Uri): Result<OfflineHibpImportResult> =
        withContext(Dispatchers.IO) {
            try {
                var fileName = "hibp_hashes.txt"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            val size = cursor.getLong(sizeIdx)
                            if (size > MAX_IMPORT_BYTES) {
                                return@withContext Result.failure(
                                    IllegalArgumentException("Offline HIBP file exceeds 50 MB limit.")
                                )
                            }
                        }
                    }
                }

                val stream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(
                        IllegalStateException("Unable to open selected offline HIBP file.")
                    )

                stream.use { input ->
                    InputStreamReader(input, Charsets.UTF_8).use { reader ->
                        importFromReader(context, reader, fileName)
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Imports an offline HIBP SHA-1 hash dataset (`FULL_SHA1:COUNT` or `FULL_SHA1` lines)
     * into the local prefix-partitioned `.range` cache for air-gapped verification.
     */
    fun importFromReader(
        context: Context,
        reader: Reader,
        sourceFileName: String = "hibp_offline.txt"
    ): Result<OfflineHibpImportResult> {
        return try {
            val buffered = reader as? BufferedReader ?: BufferedReader(reader)
            val prefixBuckets = HashMap<String, MutableMap<String, Int>>()
            val updatedPrefixes = HashSet<String>()
            var importedEntries = 0
            var skippedLines = 0
            var lineCount = 0

            fun flushBuckets() {
                if (prefixBuckets.isEmpty()) return
                for ((prefix, suffixMap) in prefixBuckets) {
                    mergePrefixEntries(context, prefix, suffixMap)
                    updatedPrefixes.add(prefix)
                }
                prefixBuckets.clear()
            }

            while (true) {
                val rawLine = buffered.readLine() ?: break
                lineCount++
                if (lineCount > MAX_IMPORT_LINES) {
                    return Result.failure(
                        IllegalArgumentException("Offline HIBP file exceeds $MAX_IMPORT_LINES line safety limit.")
                    )
                }

                val line = rawLine.trim().removePrefix("\uFEFF")
                if (line.isEmpty() || line.startsWith("#")) continue

                val colonIdx = line.indexOf(':')
                val hashPart: String
                val countPart: Int
                if (colonIdx > 0) {
                    hashPart = line.substring(0, colonIdx).trim().uppercase(Locale.US)
                    countPart = line.substring(colonIdx + 1).trim().toIntOrNull() ?: 1
                } else {
                    hashPart = line.uppercase(Locale.US)
                    countPart = 1
                }

                if (FULL_SHA1_REGEX.matches(hashPart) && countPart > 0) {
                    val prefix = hashPart.substring(0, 5)
                    val suffix = hashPart.substring(5)
                    val bucket = prefixBuckets.getOrPut(prefix) { LinkedHashMap() }
                    val existing = bucket[suffix] ?: 0
                    bucket[suffix] = maxOf(existing, countPart)
                    importedEntries++
                } else {
                    skippedLines++
                }

                if (prefixBuckets.size >= 512) {
                    flushBuckets()
                }
            }

            flushBuckets()

            if (importedEntries == 0) {
                return Result.failure(
                    IllegalArgumentException("No valid SHA-1 hash entries (40-char hex or SHA1:COUNT) found.")
                )
            }

            Result.success(
                OfflineHibpImportResult(
                    sourceFileName = sourceFileName,
                    importedEntries = importedEntries,
                    updatedPrefixes = updatedPrefixes.size,
                    skippedLines = skippedLines
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mergePrefixEntries(
        context: Context,
        prefix: String,
        newEntries: Map<String, Int>
    ) {
        val file = prefixFile(context, prefix) ?: return
        synchronized(cacheLock) {
            val merged = LinkedHashMap<String, Int>()
            if (file.exists()) {
                try {
                    file.forEachLine(Charsets.UTF_8) { raw ->
                        val trimmed = raw.trim()
                        val idx = trimmed.indexOf(':')
                        if (idx > 0) {
                            val sfx = trimmed.substring(0, idx).trim().uppercase(Locale.US)
                            val cnt = trimmed.substring(idx + 1).trim().toIntOrNull() ?: 0
                            if (SUFFIX_SHA1_REGEX.matches(sfx) && cnt > 0) {
                                merged[sfx] = cnt
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            for ((sfx, cnt) in newEntries) {
                val prev = merged[sfx] ?: 0
                merged[sfx] = maxOf(prev, cnt)
            }
            val content = buildString {
                for ((sfx, cnt) in merged) {
                    append(sfx).append(':').append(cnt).append('\n')
                }
            }
            atomicWriteText(file, content)
        }
    }
}
