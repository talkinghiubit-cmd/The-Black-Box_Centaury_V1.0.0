package com.example.security

import android.content.Context
import com.example.model.VaultCategory
import com.example.model.VaultItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

data class KAnonymityPartition(
    val prefix: String,
    val suffix: String
)

enum class BreachVerificationSource {
    ONLINE_HIBP,
    OFFLINE_CACHE
}

sealed interface BreachCheckResult {
    data class Clean(
        val hashPrefix: String,
        val source: BreachVerificationSource = BreachVerificationSource.ONLINE_HIBP
    ) : BreachCheckResult

    data class Breached(
        val count: Int,
        val hashPrefix: String,
        val source: BreachVerificationSource = BreachVerificationSource.ONLINE_HIBP
    ) : BreachCheckResult

    data class OfflineUnverified(val hashPrefix: String) : BreachCheckResult

    data class Failure(val reason: String) : BreachCheckResult
}

data class BreachScanProgress(
    val checkedCount: Int,
    val totalCount: Int,
    val breachedCount: Int,
    val errorCount: Int,
    val offlineUnverifiedCount: Int = 0
)

data class BreachAuditSummary(
    val resultsByRecordId: Map<String, Int>,
    val checkedCount: Int,
    val breachedCount: Int,
    val errorCount: Int,
    val uniquePrefixQueries: Int,
    val cacheHits: Int,
    val offlineUnverifiedCount: Int = 0,
    val wasOfflineMode: Boolean = false
)

fun interface HibpRangeFetcher {
    suspend fun fetchRange(prefix: String): Result<String>
}

object BreachChecker {

    private val HEX_PREFIX_REGEX = Regex("^[0-9A-F]{5}$")
    private val HEX_SUFFIX_REGEX = Regex("^[0-9A-F]{35}$")

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    val defaultRangeFetcher: HibpRangeFetcher = HibpRangeFetcher { prefix ->
        withContext(Dispatchers.IO) {
            if (!HEX_PREFIX_REGEX.matches(prefix)) {
                return@withContext Result.failure(IllegalArgumentException("Invalid k-anonymity prefix"))
            }
            try {
                val request = Request.Builder()
                    .url("https://api.pwnedpasswords.com/range/$prefix")
                    .header("User-Agent", "TheBlackBox-Android-Vault")
                    .header("Add-Padding", "true") // Protects against response-size traffic analysis
                    .header("Cache-Control", "no-store")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("HIBP HTTP ${response.code}")
                        )
                    }
                    val body = response.body?.string()
                        ?: return@withContext Result.failure(IllegalStateException("Empty HIBP response"))
                    Result.success(body)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Locally computes the SHA-1 hash of [password] and splits it into:
     * - a 5-character uppercase hex prefix (safe to query via HIBP k-Anonymity API)
     * - a 35-character uppercase hex suffix (kept strictly on-device for local matching)
     */
    fun partitionForKAnonymity(password: String): KAnonymityPartition {
        val sha1 = BlackBoxSecurity.sha1Hex(password).uppercase(Locale.US)
        require(sha1.length == 40) { "Invalid SHA-1 digest length" }
        return KAnonymityPartition(
            prefix = sha1.substring(0, 5),
            suffix = sha1.substring(5)
        )
    }

    /**
     * Locally searches an HIBP `/range/{prefix}` response body for [expectedSuffix].
     * Ignores `:0` dummy padding entries emitted when `Add-Padding: true` is active.
     */
    fun parseRangeResponse(responseBody: String, expectedSuffix: String): Int {
        val normalizedSuffix = expectedSuffix.trim().uppercase(Locale.US)
        if (!HEX_SUFFIX_REGEX.matches(normalizedSuffix)) return 0

        for (rawLine in responseBody.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val separatorIdx = line.indexOf(':')
            if (separatorIdx <= 0) continue

            val candidateSuffix = line.substring(0, separatorIdx).trim().uppercase(Locale.US)
            if (candidateSuffix == normalizedSuffix) {
                val count = line.substring(separatorIdx + 1).trim().toIntOrNull() ?: 0
                return if (count > 0) count else 0
            }
        }
        return 0
    }

    /**
     * Offline-first single-password verification:
     * 1. Checks persistent on-device [OfflineHibpStore] prefix ranges first.
     * 2. If offline, never hangs on network sockets—resolves cached/imported prefixes locally
     *    or returns [BreachCheckResult.OfflineUnverified].
     * 3. If online and cache misses or is stale, queries HIBP k-Anonymity `/range/{prefix}`
     *    and caches the response locally for future offline use.
     */
    suspend fun checkPasswordOfflineFirst(
        context: Context,
        password: String,
        rangeFetcher: HibpRangeFetcher = defaultRangeFetcher,
        isOnline: Boolean = OfflineHibpStore.isNetworkAvailable(context)
    ): BreachCheckResult {
        if (password.isEmpty()) return BreachCheckResult.Clean("")

        val partition = partitionForKAnonymity(password)

        if (!isOnline) {
            val offlineRange = OfflineHibpStore.readCachedRange(context, partition.prefix, allowExpired = true)
                ?: return BreachCheckResult.OfflineUnverified(partition.prefix)
            val count = parseRangeResponse(offlineRange, partition.suffix)
            return if (count > 0) {
                BreachCheckResult.Breached(count, partition.prefix, BreachVerificationSource.OFFLINE_CACHE)
            } else {
                BreachCheckResult.Clean(partition.prefix, BreachVerificationSource.OFFLINE_CACHE)
            }
        }

        val freshCached = OfflineHibpStore.readCachedRange(context, partition.prefix, allowExpired = false)
        if (freshCached != null) {
            val count = parseRangeResponse(freshCached, partition.suffix)
            return if (count > 0) {
                BreachCheckResult.Breached(count, partition.prefix, BreachVerificationSource.OFFLINE_CACHE)
            } else {
                BreachCheckResult.Clean(partition.prefix, BreachVerificationSource.OFFLINE_CACHE)
            }
        }

        val rangeResult = rangeFetcher.fetchRange(partition.prefix)
        return rangeResult.fold(
            onSuccess = { body ->
                OfflineHibpStore.writeCachedRange(context, partition.prefix, body)
                val count = parseRangeResponse(body, partition.suffix)
                if (count > 0) {
                    BreachCheckResult.Breached(count, partition.prefix, BreachVerificationSource.ONLINE_HIBP)
                } else {
                    BreachCheckResult.Clean(partition.prefix, BreachVerificationSource.ONLINE_HIBP)
                }
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                val staleFallback = OfflineHibpStore.readCachedRange(context, partition.prefix, allowExpired = true)
                if (staleFallback != null) {
                    val count = parseRangeResponse(staleFallback, partition.suffix)
                    if (count > 0) {
                        BreachCheckResult.Breached(count, partition.prefix, BreachVerificationSource.OFFLINE_CACHE)
                    } else {
                        BreachCheckResult.Clean(partition.prefix, BreachVerificationSource.OFFLINE_CACHE)
                    }
                } else {
                    BreachCheckResult.Failure(error.message ?: "Network error contacting HaveIBeenPwned")
                }
            }
        )
    }

    /**
     * Checks a single password using HaveIBeenPwned k-Anonymity (stateless, no disk cache).
     * The password, full SHA-1 hash, and 35-char suffix NEVER leave the device.
     */
    suspend fun checkPasswordDetailed(
        password: String,
        rangeFetcher: HibpRangeFetcher = defaultRangeFetcher
    ): BreachCheckResult {
        if (password.isEmpty()) return BreachCheckResult.Clean("")

        val partition = partitionForKAnonymity(password)
        val rangeResult = rangeFetcher.fetchRange(partition.prefix)
        return rangeResult.fold(
            onSuccess = { body ->
                val count = parseRangeResponse(body, partition.suffix)
                if (count > 0) {
                    BreachCheckResult.Breached(count = count, hashPrefix = partition.prefix)
                } else {
                    BreachCheckResult.Clean(hashPrefix = partition.prefix)
                }
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                BreachCheckResult.Failure(error.message ?: "Network error contacting HaveIBeenPwned")
            }
        )
    }

    /**
     * Convenience wrapper returning:
     * - `0` if clean
     * - `>0` if found in public data leaks (exposure count)
     * - `-1` on network/API failure
     */
    suspend fun checkPassword(
        password: String,
        rangeFetcher: HibpRangeFetcher = defaultRangeFetcher
    ): Int {
        return when (val result = checkPasswordDetailed(password, rangeFetcher)) {
            is BreachCheckResult.Clean -> 0
            is BreachCheckResult.Breached -> result.count
            is BreachCheckResult.OfflineUnverified,
            is BreachCheckResult.Failure -> -1
        }
    }

    /**
     * Offline-first batch vault audit: uses persistent on-device [OfflineHibpStore] ranges
     * first (working completely offline for cached/imported prefixes) and falls back to online
     * HIBP k-Anonymity range fetches only when connected and needed.
     */
    suspend fun auditPasswordsOfflineFirst(
        context: Context,
        items: List<VaultItem>,
        rangeFetcher: HibpRangeFetcher = defaultRangeFetcher,
        isOnline: Boolean = OfflineHibpStore.isNetworkAvailable(context),
        rateLimitDelayMs: Long = 150L,
        onProgress: (BreachScanProgress) -> Unit = {}
    ): BreachAuditSummary {
        val loginItems = items.filter { it.category == VaultCategory.LOGIN && it.password.isNotEmpty() }
        val total = loginItems.size
        if (total == 0) {
            return BreachAuditSummary(
                resultsByRecordId = emptyMap(),
                checkedCount = 0,
                breachedCount = 0,
                errorCount = 0,
                uniquePrefixQueries = 0,
                cacheHits = 0,
                offlineUnverifiedCount = 0,
                wasOfflineMode = !isOnline
            )
        }

        val transientPrefixCache = HashMap<String, String>()
        val results = LinkedHashMap<String, Int>(total)
        var checked = 0
        var breached = 0
        var errors = 0
        var offlineUnverified = 0
        var uniqueQueries = 0
        var cacheHits = 0

        try {
            for (item in loginItems) {
                val partition = partitionForKAnonymity(item.password)
                val inMemoryBody = transientPrefixCache[partition.prefix]
                var resolvedBody: String? = null
                var missedWhileOffline = false

                if (inMemoryBody != null) {
                    cacheHits++
                    resolvedBody = inMemoryBody
                } else {
                    val diskCached = OfflineHibpStore.readCachedRange(
                        context = context,
                        prefix = partition.prefix,
                        allowExpired = !isOnline
                    )
                    if (diskCached != null) {
                        cacheHits++
                        transientPrefixCache[partition.prefix] = diskCached
                        resolvedBody = diskCached
                    } else if (!isOnline) {
                        missedWhileOffline = true
                    } else {
                        if (uniqueQueries > 0 && rateLimitDelayMs > 0L) {
                            delay(rateLimitDelayMs)
                        }
                        uniqueQueries++
                        val fetched = rangeFetcher.fetchRange(partition.prefix)
                        if (fetched.isSuccess) {
                            val responseBody = fetched.getOrThrow()
                            OfflineHibpStore.writeCachedRange(context, partition.prefix, responseBody)
                            transientPrefixCache[partition.prefix] = responseBody
                            resolvedBody = responseBody
                        } else {
                            val err = fetched.exceptionOrNull()
                            if (err is CancellationException) throw err
                            // Fall back to stale cached range if available
                            val stale = OfflineHibpStore.readCachedRange(
                                context = context,
                                prefix = partition.prefix,
                                allowExpired = true
                            )
                            if (stale != null) {
                                cacheHits++
                                transientPrefixCache[partition.prefix] = stale
                                resolvedBody = stale
                            }
                        }
                    }
                }

                when {
                    resolvedBody != null -> {
                        val count = parseRangeResponse(resolvedBody, partition.suffix)
                        results[item.id] = count
                        checked++
                        if (count > 0) breached++
                    }
                    missedWhileOffline -> {
                        offlineUnverified++
                    }
                    else -> {
                        errors++
                    }
                }

                onProgress(
                    BreachScanProgress(
                        checkedCount = checked,
                        totalCount = total,
                        breachedCount = breached,
                        errorCount = errors,
                        offlineUnverifiedCount = offlineUnverified
                    )
                )
            }
        } finally {
            transientPrefixCache.clear()
        }

        return BreachAuditSummary(
            resultsByRecordId = results,
            checkedCount = checked,
            breachedCount = breached,
            errorCount = errors,
            uniquePrefixQueries = uniqueQueries,
            cacheHits = cacheHits,
            offlineUnverifiedCount = offlineUnverified,
            wasOfflineMode = !isOnline
        )
    }

    /**
     * Audits a collection of vault items locally against HIBP k-Anonymity ranges.
     * Uses a transient, scan-scoped prefix cache so reused passwords or shared 5-char prefixes
     * only trigger a single network lookup and are verified locally in memory.
     */
    suspend fun auditPasswords(
        items: List<VaultItem>,
        rangeFetcher: HibpRangeFetcher = defaultRangeFetcher,
        rateLimitDelayMs: Long = 150L,
        onProgress: (BreachScanProgress) -> Unit = {}
    ): BreachAuditSummary {
        val loginItems = items.filter { it.category == VaultCategory.LOGIN && it.password.isNotEmpty() }
        val total = loginItems.size
        if (total == 0) {
            return BreachAuditSummary(
                resultsByRecordId = emptyMap(),
                checkedCount = 0,
                breachedCount = 0,
                errorCount = 0,
                uniquePrefixQueries = 0,
                cacheHits = 0
            )
        }

        val transientPrefixCache = HashMap<String, String>()
        val results = LinkedHashMap<String, Int>(total)
        var checked = 0
        var breached = 0
        var errors = 0
        var uniqueQueries = 0
        var cacheHits = 0

        try {
            for (item in loginItems) {
                val partition = partitionForKAnonymity(item.password)
                val cachedBody = transientPrefixCache[partition.prefix]
                val body: String? = if (cachedBody != null) {
                    cacheHits++
                    cachedBody
                } else {
                    if (uniqueQueries > 0 && rateLimitDelayMs > 0L) {
                        delay(rateLimitDelayMs)
                    }
                    uniqueQueries++
                    val fetched = rangeFetcher.fetchRange(partition.prefix)
                    if (fetched.isSuccess) {
                        val responseBody = fetched.getOrThrow()
                        transientPrefixCache[partition.prefix] = responseBody
                        responseBody
                    } else {
                        val err = fetched.exceptionOrNull()
                        if (err is CancellationException) throw err
                        null
                    }
                }

                if (body != null) {
                    val count = parseRangeResponse(body, partition.suffix)
                    results[item.id] = count
                    checked++
                    if (count > 0) breached++
                } else {
                    errors++
                }

                onProgress(
                    BreachScanProgress(
                        checkedCount = checked,
                        totalCount = total,
                        breachedCount = breached,
                        errorCount = errors
                    )
                )
            }
        } finally {
            transientPrefixCache.clear()
        }

        return BreachAuditSummary(
            resultsByRecordId = results,
            checkedCount = checked,
            breachedCount = breached,
            errorCount = errors,
            uniquePrefixQueries = uniqueQueries,
            cacheHits = cacheHits
        )
    }
}
