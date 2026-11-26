package com.example.autofill

import com.example.model.VaultCategory
import com.example.model.VaultItem
import java.net.URI
import java.util.Locale

data class AutofillMatchResult(
    val item: VaultItem,
    val score: Int,
    val isDirectMatch: Boolean
)

/**
 * Pure domain/package credential matcher for Android AutofillService and Chrome web domains.
 */
object AutofillMatcher {

    private val COMMON_PACKAGE_TOKENS = setOf(
        "com", "org", "net", "io", "app", "android", "mobile", "client", "lite", "pro", "v2"
    )

    fun extractHost(rawUrlOrDomain: String): String {
        val trimmed = rawUrlOrDomain.trim().lowercase(Locale.US)
        if (trimmed.isEmpty()) return ""
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val host = try {
            URI(withScheme).host.orEmpty()
        } catch (_: Exception) {
            trimmed.substringBefore('/').substringBefore(':')
        }
        return host
            .lowercase(Locale.US)
            .removePrefix("www.")
            .trim('.')
    }

    fun extractRegistrableRoot(host: String): String {
        val clean = extractHost(host)
        if (clean.isEmpty()) return ""
        val parts = clean.split('.').filter { it.isNotBlank() }
        return if (parts.size >= 2) {
            "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
        } else {
            clean
        }
    }

    fun matchLogins(
        items: List<VaultItem>,
        webDomain: String?,
        packageName: String?,
        maxResults: Int = 10
    ): List<AutofillMatchResult> {
        val logins = items.filter {
            it.category == VaultCategory.LOGIN && (it.username.isNotBlank() || it.password.isNotBlank())
        }
        if (logins.isEmpty()) return emptyList()

        val targetHost = extractHost(webDomain.orEmpty())
        val targetRoot = extractRegistrableRoot(targetHost)
        val packageTokens = packageName.orEmpty()
            .lowercase(Locale.US)
            .split('.', '_', '-')
            .filter { it.length >= 3 && it !in COMMON_PACKAGE_TOKENS }

        val ranked = logins.map { item ->
            val itemHost = extractHost(item.url)
            val itemRoot = extractRegistrableRoot(itemHost)
            val titleLower = item.title.lowercase(Locale.US)
            val urlLower = item.url.lowercase(Locale.US)

            var score = 0
            if (targetHost.isNotEmpty() && itemHost.isNotEmpty()) {
                when {
                    itemHost == targetHost -> score += 100
                    itemHost.endsWith(".$targetHost") || targetHost.endsWith(".$itemHost") -> score += 85
                    targetRoot.isNotEmpty() && itemRoot == targetRoot -> score += 75
                }
            }
            if (packageTokens.isNotEmpty()) {
                val matchedTokens = packageTokens.count { token ->
                    titleLower.contains(token) || urlLower.contains(token) || itemHost.contains(token)
                }
                if (matchedTokens > 0) {
                    score += 40 + matchedTokens * 10
                }
            }
            if (item.isFavorite) {
                score += 2
            }

            AutofillMatchResult(
                item = item,
                score = score,
                isDirectMatch = score >= 40
            )
        }.sortedWith(
            compareByDescending<AutofillMatchResult> { it.score }
                .thenByDescending { it.item.updatedAt }
        )

        val directMatches = ranked.filter { it.isDirectMatch }
        return if (directMatches.isNotEmpty()) {
            directMatches.take(maxResults)
        } else {
            ranked.take(maxResults)
        }
    }
}
