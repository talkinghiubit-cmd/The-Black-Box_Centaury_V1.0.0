package com.example.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VaultHealthReport
import com.example.model.VaultItem
import com.example.security.BreachScanProgress
import com.example.security.OfflineHibpStats
import com.example.ui.components.SecurityBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlackBoxBorder
import com.example.ui.theme.BlackBoxPitch
import com.example.ui.theme.BlackBoxSurface
import com.example.ui.theme.BlackBoxSurfaceVariant
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanIce
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SecurityHealthSheet(
    healthReport: VaultHealthReport,
    isCheckingBreaches: Boolean,
    scanProgress: BreachScanProgress? = null,
    isNetworkOnline: Boolean = true,
    offlineHibpStats: OfflineHibpStats = OfflineHibpStats(),
    onRunBreachScan: () -> Unit,
    onImportOfflineHibpUri: (Uri) -> Unit = {},
    onClearOfflineHibpCache: () -> Unit = {},
    onFilterCategory: (String?) -> Unit,
    onSelectVulnerableItem: (VaultItem) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val offlineHibpPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportOfflineHibpUri(uri)
    }

    val scoreColor = when {
        healthReport.overallScore >= 85 -> EmeraldNeon
        healthReport.overallScore >= 60 -> AmberWarning
        else -> CrimsonAlert
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BlackBoxSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BlackBoxSurfaceVariant)
                            .border(1.dp, scoreColor, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = scoreColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "VAULT SECURITY HEALTH",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_health_sheet")) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Score Overview Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BlackBoxSurfaceVariant)
                    .border(1.dp, BlackBoxBorder, RoundedCornerShape(12.dp))
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SECURITY RATING",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                healthReport.overallScore >= 85 -> "EXCELLENT POSTURE"
                                healthReport.overallScore >= 60 -> "MODERATE DEFENSES"
                                else -> "ACTION REQUIRED"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${healthReport.totalLogins} monitored credential set(s)",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    // Score Circle
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(scoreColor.copy(alpha = 0.12f))
                            .border(2.dp, scoreColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${healthReport.overallScore}%",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = scoreColor
                            )
                            Text(
                                text = "SCORE",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            if (healthReport.totalLogins > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                PasswordStrengthDistributionCard(healthReport = healthReport)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // HaveIBeenPwned Scan Button
            Button(
                onClick = onRunBreachScan,
                enabled = !isCheckingBreaches,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BlackBoxSurfaceVariant,
                    contentColor = CyanIce
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .border(1.dp, CyanIce.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .testTag("run_hibp_scan_button")
            ) {
                if (isCheckingBreaches) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = CyanIce,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val progressText = if (scanProgress != null && scanProgress.totalCount > 0) {
                        val processed = scanProgress.checkedCount + scanProgress.errorCount + scanProgress.offlineUnverifiedCount
                        "AUDITING HIBP ($processed/${scanProgress.totalCount})..."
                    } else {
                        "AUDITING HIBP DATABASE..."
                    }
                    Text(
                        text = progressText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = if (isNetworkOnline) Icons.Default.Refresh else Icons.Default.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isNetworkOnline) {
                            "SCAN HAVEIBEENPWNED (K-ANONYMITY)"
                        } else {
                            "CHECK LOCAL HIBP CACHE (OFFLINE)"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isCheckingBreaches && scanProgress != null && scanProgress.totalCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val processed = (scanProgress.checkedCount + scanProgress.errorCount + scanProgress.offlineUnverifiedCount)
                val fraction = (processed.toFloat() / scanProgress.totalCount).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    color = CyanIce,
                    trackColor = BlackBoxSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "k-Anonymity: Only 5-char SHA-1 prefixes leave device",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
                if (healthReport.uncheckedCount > 0) {
                    Text(
                        text = "${healthReport.uncheckedCount} UNSCANNED",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = AmberWarning,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                onFilterCategory("UNCHECKED")
                                onDismiss()
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Offline HIBP Prefix Cache & Dataset Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BlackBoxSurfaceVariant)
                    .border(1.dp, BlackBoxBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isNetworkOnline) {
                            "OFFLINE CACHE: ${offlineHibpStats.cachedPrefixCount} PREFIXES READY"
                        } else {
                            "OFFLINE MODE • ${offlineHibpStats.cachedPrefixCount} PREFIXES CACHED"
                        },
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (offlineHibpStats.cachedPrefixCount > 0) EmeraldNeon else TextSecondary
                    )
                    Text(
                        text = "Synced ranges & imported SHA-1 lists work offline",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { offlineHibpPicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) },
                        enabled = !isCheckingBreaches,
                        modifier = Modifier.testTag("import_offline_hibp_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Load offline HIBP SHA-1 file",
                            tint = CyanIce,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (offlineHibpStats.cachedPrefixCount > 0) {
                        IconButton(
                            onClick = onClearOfflineHibpCache,
                            enabled = !isCheckingBreaches,
                            modifier = Modifier.testTag("clear_offline_hibp_cache_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear offline HIBP cache",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4 Breakdown Grid Cards
            Text(
                text = "VULNERABILITY CATEGORIES",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextMuted,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HealthMetricCard(
                    title = "BREACHED",
                    count = healthReport.breachedCount,
                    alertColor = CrimsonAlert,
                    icon = Icons.Default.Dangerous,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onFilterCategory("BREACHED")
                        onDismiss()
                    }
                )
                HealthMetricCard(
                    title = "WEAK KEYS",
                    count = healthReport.weakCount,
                    alertColor = AmberWarning,
                    icon = Icons.Default.Warning,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onFilterCategory("WEAK")
                        onDismiss()
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HealthMetricCard(
                    title = "DUPLICATES",
                    count = healthReport.reusedCount,
                    alertColor = AmberWarning,
                    icon = Icons.Default.LockReset,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onFilterCategory("REUSED")
                        onDismiss()
                    }
                )
                HealthMetricCard(
                    title = "EXPIRED",
                    count = healthReport.expiredCount,
                    alertColor = CyanIce,
                    icon = Icons.Default.History,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onFilterCategory("EXPIRED")
                        onDismiss()
                    }
                )
            }

            if (healthReport.duplicateClusters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "DUPLICATE PASSWORD GROUPS (${healthReport.duplicateClusters.size})",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AmberWarning,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                healthReport.duplicateClusters.forEachIndexed { idx, cluster ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BlackBoxSurfaceVariant)
                            .border(1.dp, AmberWarning.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "GROUP #${idx + 1} • ${cluster.items.size} ACCOUNTS SHARE IDENTICAL PASSWORD",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = AmberWarning
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            cluster.items.forEach { dupItem ->
                                Text(
                                    text = dupItem.title.ifBlank { dupItem.username.ifBlank { "Untitled" } },
                                    fontSize = 11.sp,
                                    color = TextPrimary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(BlackBoxPitch)
                                        .border(1.dp, BlackBoxBorder, RoundedCornerShape(6.dp))
                                        .clickable {
                                            onDismiss()
                                            onSelectVulnerableItem(dupItem)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Actionable list of items needing immediate attention
            val flaggedItems = (
                healthReport.breachedItems +
                    healthReport.weakItems +
                    healthReport.reusedItems +
                    healthReport.expiredItems
                ).distinctBy { it.id }

            if (flaggedItems.isNotEmpty()) {
                Text(
                    text = "ACTIONABLE RECOMMENDATIONS (${flaggedItems.size})",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                flaggedItems.take(25).forEach { item ->
                    val reasons = buildList {
                        if (item.pwnedBreachCount > 0) add("Breached (${item.pwnedBreachCount}x)")
                        if (healthReport.weakItems.any { it.id == item.id }) add("Weak")
                        if (healthReport.reusedItems.any { it.id == item.id }) add("Duplicate")
                        if (healthReport.expiredItems.any { it.id == item.id }) add("Expired >90d")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BlackBoxSurfaceVariant)
                            .border(1.dp, BlackBoxBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                onDismiss()
                                onSelectVulnerableItem(item)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = reasons.joinToString(" • "),
                                fontSize = 11.sp,
                                color = if (item.pwnedBreachCount > 0) CrimsonAlert else AmberWarning,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        SecurityBadge(
                            text = "ROTATE",
                            color = if (item.pwnedBreachCount > 0) CrimsonAlert else AmberWarning
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordStrengthDistributionCard(healthReport: VaultHealthReport) {
    val total = healthReport.totalLogins.coerceAtLeast(1)
    val strongFraction = healthReport.strongCount.toFloat() / total
    val moderateFraction = healthReport.moderateCount.toFloat() / total
    val weakFraction = healthReport.weakCount.toFloat() / total

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BlackBoxSurfaceVariant)
            .border(1.dp, BlackBoxBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "PASSWORD STRENGTH BREAKDOWN",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = TextMuted,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BlackBoxPitch)
        ) {
            if (strongFraction > 0f) {
                Box(
                    modifier = Modifier
                        .weight(strongFraction)
                        .height(8.dp)
                        .background(EmeraldNeon)
                )
            }
            if (moderateFraction > 0f) {
                Box(
                    modifier = Modifier
                        .weight(moderateFraction)
                        .height(8.dp)
                        .background(CyanIce)
                )
            }
            if (weakFraction > 0f) {
                Box(
                    modifier = Modifier
                        .weight(weakFraction)
                        .height(8.dp)
                        .background(CrimsonAlert)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "STRONG: ${healthReport.strongCount}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = EmeraldNeon
            )
            Text(
                text = "MODERATE: ${healthReport.moderateCount}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = CyanIce
            )
            Text(
                text = "WEAK: ${healthReport.weakCount}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (healthReport.weakCount > 0) CrimsonAlert else TextSecondary
            )
        }
    }
}

@Composable
private fun HealthMetricCard(
    title: String,
    count: Int,
    alertColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val activeColor = if (count > 0) alertColor else TextSecondary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BlackBoxSurfaceVariant)
            .border(1.dp, if (count > 0) alertColor.copy(alpha = 0.5f) else BlackBoxBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = activeColor
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$count",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (count > 0) activeColor else TextPrimary
            )
            Text(
                text = if (count > 0) "Tap to inspect" else "Clean",
                fontSize = 10.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
