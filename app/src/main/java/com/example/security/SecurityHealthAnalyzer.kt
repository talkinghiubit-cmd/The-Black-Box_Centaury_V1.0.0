package com.example.security

import com.example.model.DuplicatePasswordCluster
import com.example.model.VaultCategory
import com.example.model.VaultHealthReport
import com.example.model.VaultItem
import kotlin.math.roundToInt

/**
 * Analyzes all stored login credentials locally to produce a proportional Security Health Score
 * (0–100), classify password entropy, group duplicate/reused passwords, and flag breached or
 * expired credentials.
 */
object SecurityHealthAnalyzer {

    private const val NINETY_DAYS_MS = 90L * 24L * 60L * 60L * 1000L

    fun analyze(
        items: List<VaultItem>,
        nowMillis: Long = System.currentTimeMillis()
    ): VaultHealthReport {
        val logins = items.filter { it.category == VaultCategory.LOGIN && it.password.isNotEmpty() }
        if (logins.isEmpty()) {
            return VaultHealthReport(
                overallScore = 100,
                totalLogins = 0,
                strongCount = 0,
                weakCount = 0,
                reusedCount = 0,
                breachedCount = 0,
                expiredCount = 0,
                uncheckedCount = 0,
                moderateCount = 0,
                duplicateGroupCount = 0
            )
        }

        val strengthById = HashMap<String, Int>(logins.size)
        val weakItems = mutableListOf<VaultItem>()
        var strongCount = 0
        var moderateCount = 0

        for (login in logins) {
            val strength = BlackBoxSecurity.evaluatePasswordStrength(login.password)
            strengthById[login.id] = strength.score
            when {
                strength.score <= 1 -> weakItems.add(login)
                strength.score == 2 -> moderateCount++
                else -> strongCount++
            }
        }

        val passwordGroups = logins.groupBy { it.password.trim() }
        val duplicateClusters = passwordGroups.values
            .filter { it.size > 1 }
            .sortedByDescending { it.size }
            .mapIndexed { index, group ->
                DuplicatePasswordCluster(
                    id = "dup-group-$index-${group.first().id}",
                    items = group.sortedBy { it.title.lowercase() }
                )
            }

        val reusedItemIds = duplicateClusters.flatMap { cluster -> cluster.items.map { it.id } }.toSet()
        val reusedItems = logins.filter { it.id in reusedItemIds }

        val breachedItems = logins.filter { it.pwnedBreachCount > 0 }
        val uncheckedItems = logins.filter { it.pwnedBreachCount < 0 }

        val expiryCutoff = nowMillis - NINETY_DAYS_MS
        val expiredItems = logins.filter { it.passwordUpdatedAt in 1L until expiryCutoff }

        var totalItemScore = 0.0
        for (login in logins) {
            var itemScore = 100.0
            val strengthScore = strengthById[login.id] ?: 0
            when (strengthScore) {
                0 -> itemScore -= 42.0
                1 -> itemScore -= 30.0
                2 -> itemScore -= 8.0
            }
            if (login.id in reusedItemIds) {
                itemScore -= 24.0
            }
            if (login.pwnedBreachCount > 0) {
                itemScore -= 55.0
            }
            if (login.passwordUpdatedAt in 1L until expiryCutoff) {
                itemScore -= 8.0
            }
            totalItemScore += itemScore.coerceIn(0.0, 100.0)
        }

        val overallScore = (totalItemScore / logins.size).roundToInt().coerceIn(0, 100)

        return VaultHealthReport(
            overallScore = overallScore,
            totalLogins = logins.size,
            strongCount = strongCount,
            weakCount = weakItems.size,
            reusedCount = reusedItems.size,
            breachedCount = breachedItems.size,
            expiredCount = expiredItems.size,
            uncheckedCount = uncheckedItems.size,
            moderateCount = moderateCount,
            duplicateGroupCount = duplicateClusters.size,
            weakItems = weakItems,
            reusedItems = reusedItems,
            breachedItems = breachedItems,
            expiredItems = expiredItems,
            uncheckedItems = uncheckedItems,
            duplicateClusters = duplicateClusters
        )
    }
}
