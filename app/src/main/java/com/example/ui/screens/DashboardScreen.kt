package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.VaultCategory
import com.example.model.VaultHealthReport
import com.example.model.VaultItem
import com.example.security.BlackBoxSecurity
import com.example.ui.VaultUiState
import com.example.ui.components.CategoryIcon
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlackBoxBorder
import com.example.ui.theme.BlackBoxPitch
import com.example.ui.theme.BlackBoxSurface
import com.example.ui.theme.BlackBoxSurfaceVariant
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanIce
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: VaultUiState,
    onSearchChanged: (String) -> Unit,
    onCategorySelected: (VaultCategory?) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onClearHealthFilter: () -> Unit,
    onItemClick: (VaultItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCopyText: (String, String, Boolean) -> Unit,
    onCreateNewItem: () -> Unit,
    onLockVault: () -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenReceiveShare: () -> Unit = {},
    onOpenCsvImport: () -> Unit = {},
    onOpenBackupExport: () -> Unit,
    onOpenSecurityHealth: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearMessages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.infoMessage, uiState.errorMessage) {
        val msg = uiState.infoMessage ?: uiState.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onClearMessages()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BlackBoxPitch,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.app_brand_upper),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = stringResource(R.string.app_tagline),
                            fontSize = 10.sp,
                            color = EmeraldNeon,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                actions = {
                    // Security Health & Breach Audit Icon with Badge (counts breached, weak, and duplicate logins)
                    IconButton(
                        onClick = onOpenSecurityHealth,
                        modifier = Modifier.testTag("open_health_button")
                    ) {
                        val health = uiState.healthReport
                        val uniqueFlaggedCount = (health.breachedItems + health.weakItems + health.reusedItems)
                            .distinctBy { it.id }
                            .size
                        val hasAlerts = uniqueFlaggedCount > 0
                        BadgedBox(
                            badge = {
                                if (hasAlerts) {
                                    Badge(
                                        containerColor = if (health.breachedCount > 0) CrimsonAlert else AmberWarning
                                    ) {
                                        Text(
                                            text = "$uniqueFlaggedCount",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.HealthAndSafety,
                                contentDescription = "Security Health Score",
                                tint = when {
                                    health.breachedCount > 0 -> CrimsonAlert
                                    health.overallScore >= 85 && !hasAlerts -> EmeraldNeon
                                    else -> AmberWarning
                                }
                            )
                        }
                    }

                    // Import from Google Chrome or .CSV
                    IconButton(
                        onClick = onOpenCsvImport,
                        modifier = Modifier.testTag("open_csv_import_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Import Chrome or CSV Passwords",
                            tint = CyanIce
                        )
                    }

                    // Lock Vault immediately
                    IconButton(
                        onClick = onLockVault,
                        modifier = Modifier.testTag("lock_vault_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Vault",
                            tint = AmberWarning
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BlackBoxSurface,
                    titleContentColor = TextPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNewItem,
                containerColor = EmeraldNeon,
                contentColor = Color(0xFF003816),
                modifier = Modifier.testTag("fab_create_item")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Secret Record"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BlackBoxPitch)
        ) {
            // Quick Security Toolbar Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickToolButton(
                    icon = Icons.Default.Key,
                    label = "Generator",
                    tint = CyanIce,
                    onClick = onOpenGenerator,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_generator_button")
                )
                QuickToolButton(
                    icon = Icons.Default.LockOpen,
                    label = "Receive",
                    tint = CyanIce,
                    onClick = onOpenReceiveShare,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_receive_share_button")
                )
                QuickToolButton(
                    icon = Icons.Default.FileDownload,
                    label = "Backup",
                    tint = EmeraldNeon,
                    onClick = onOpenBackupExport,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_backup_button")
                )
                QuickToolButton(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    tint = TextSecondary,
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_settings_button")
                )
            }

            // Inline Security Health Score Summary & Vulnerability Alert Card
            if (uiState.healthReport.totalLogins > 0) {
                DashboardHealthCard(
                    report = uiState.healthReport,
                    onClick = onOpenSecurityHealth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChanged,
                placeholder = { Text("Search encrypted items...", color = TextMuted) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextSecondary
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldNeon,
                    unfocusedBorderColor = BlackBoxBorder,
                    focusedContainerColor = BlackBoxSurface,
                    unfocusedContainerColor = BlackBoxSurface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("vault_search_input")
            )

            // Active Health Filter Banner (if filtering by vulnerable items)
            if (uiState.activeHealthFilter != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BlackBoxSurfaceVariant)
                        .border(1.dp, AmberWarning.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "FILTER: ${uiState.activeHealthFilter} PASSWORDS (${uiState.filteredItems.size})",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = AmberWarning
                    )
                    IconButton(onClick = onClearHealthFilter, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear filter",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Category & Favorite Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedCategory == null && !uiState.showFavoritesOnly && uiState.activeHealthFilter == null,
                        onClick = {
                            if (uiState.showFavoritesOnly) onToggleFavoritesFilter()
                            if (uiState.activeHealthFilter != null) onClearHealthFilter()
                            onCategorySelected(null)
                        },
                        label = { Text("All (${uiState.decryptedItems.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldContainer,
                            selectedLabelColor = EmeraldNeon,
                            containerColor = BlackBoxSurface,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = BlackBoxBorder,
                            selectedBorderColor = EmeraldNeon,
                            enabled = true,
                            selected = uiState.selectedCategory == null && !uiState.showFavoritesOnly && uiState.activeHealthFilter == null
                        ),
                        modifier = Modifier.testTag("filter_all")
                    )
                }

                item {
                    FilterChip(
                        selected = uiState.showFavoritesOnly,
                        onClick = onToggleFavoritesFilter,
                        leadingIcon = {
                            Icon(
                                imageVector = if (uiState.showFavoritesOnly) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorites",
                                tint = if (uiState.showFavoritesOnly) AmberWarning else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Favorites") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberWarning.copy(alpha = 0.15f),
                            selectedLabelColor = AmberWarning,
                            containerColor = BlackBoxSurface,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = BlackBoxBorder,
                            selectedBorderColor = AmberWarning,
                            enabled = true,
                            selected = uiState.showFavoritesOnly
                        ),
                        modifier = Modifier.testTag("filter_favorites")
                    )
                }

                items(VaultCategory.entries.toTypedArray()) { cat ->
                    val isSelected = uiState.selectedCategory == cat && !uiState.showFavoritesOnly && uiState.activeHealthFilter == null
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (uiState.showFavoritesOnly) onToggleFavoritesFilter()
                            if (uiState.activeHealthFilter != null) onClearHealthFilter()
                            onCategorySelected(if (isSelected) null else cat)
                        },
                        label = { Text(cat.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldContainer,
                            selectedLabelColor = EmeraldNeon,
                            containerColor = BlackBoxSurface,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = BlackBoxBorder,
                            selectedBorderColor = EmeraldNeon,
                            enabled = true,
                            selected = isSelected
                        ),
                        modifier = Modifier.testTag("filter_${cat.name.lowercase()}")
                    )
                }
            }

            // Items List
            if (uiState.filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (uiState.decryptedItems.isEmpty()) "VAULT IS EMPTY" else "NO MATCHING RECORDS",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (uiState.decryptedItems.isEmpty())
                                "Tap the + button to store your first encrypted password, note, card, or SSH key."
                            else
                                "Try clearing your search query or filters.",
                            fontSize = 13.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.filteredItems, key = { it.id }) { item ->
                        val isWeak = uiState.healthReport.weakItems.any { it.id == item.id }
                        val isDuplicate = uiState.healthReport.reusedItems.any { it.id == item.id }
                        VaultItemCard(
                            item = item,
                            totpSecondsRemaining = uiState.totpSecondsRemaining,
                            isWeak = isWeak,
                            isDuplicate = isDuplicate,
                            onClick = { onItemClick(item) },
                            onToggleFavorite = { onToggleFavorite(item.id) },
                            onCopyUsername = { onCopyText("Username", item.username, false) },
                            onCopyPassword = { onCopyText("Password", item.password, true) },
                            onCopyTotp = { code -> onCopyText("2FA Code", code, true) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultItemCard(
    item: VaultItem,
    totpSecondsRemaining: Int,
    isWeak: Boolean = false,
    isDuplicate: Boolean = false,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit,
    onCopyTotp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val totpCode = remember(item.totpSecret, totpSecondsRemaining) {
        if (item.totpSecret.isNotBlank()) BlackBoxSecurity.generateTotpCode(item.totpSecret) else null
    }
    val borderColor = when {
        item.pwnedBreachCount > 0 -> CrimsonAlert.copy(alpha = 0.65f)
        isWeak || isDuplicate -> AmberWarning.copy(alpha = 0.45f)
        else -> BlackBoxBorder
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                1.dp,
                borderColor,
                RoundedCornerShape(10.dp)
            )
            .testTag("item_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = BlackBoxSurfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIcon(category = item.category)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Breached Warning Icon
                    if (item.pwnedBreachCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Dangerous,
                            contentDescription = "Breached",
                            tint = CrimsonAlert,
                            modifier = Modifier.size(15.dp)
                        )
                    } else if (isWeak || isDuplicate) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = if (isDuplicate) "Duplicate password" else "Weak password",
                            tint = AmberWarning,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Attachments indicator
                    if (item.attachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attachments",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                val subtitle = when (item.category) {
                    VaultCategory.LOGIN -> if (item.username.isNotEmpty()) item.username else item.url
                    VaultCategory.NOTE -> item.notes.replace("\n", " ")
                    VaultCategory.CARD -> if (item.cardNumber.length > 4) "•••• ${item.cardNumber.takeLast(4)}" else item.cardHolder
                    VaultCategory.KEY -> "${item.keyType} Key"
                }

                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontFamily = if (item.category == VaultCategory.KEY || item.category == VaultCategory.CARD) FontFamily.Monospace else FontFamily.Default,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Inline TOTP Code chip
                if (totpCode != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BlackBoxSurface)
                            .clickable { onCopyTotp(totpCode) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "2FA: $totpCode",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = CyanIce
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${totpSecondsRemaining}s",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Quick Copy Username
                if (item.username.isNotEmpty()) {
                    IconButton(
                        onClick = onCopyUsername,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("copy_username_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy username",
                            tint = TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // Quick Copy Password
                if (item.password.isNotEmpty()) {
                    IconButton(
                        onClick = onCopyPassword,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("copy_password_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Copy password",
                            tint = EmeraldNeon,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // Favorite Star
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("toggle_favorite_${item.id}")
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (item.isFavorite) AmberWarning else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(BlackBoxSurface)
            .border(1.dp, BlackBoxBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DashboardHealthCard(
    report: VaultHealthReport,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasCritical = report.breachedCount > 0
    val hasWarnings = report.weakCount > 0 || report.reusedCount > 0 || report.expiredCount > 0
    val accent = when {
        hasCritical -> CrimsonAlert
        report.overallScore >= 85 && !hasWarnings -> EmeraldNeon
        else -> AmberWarning
    }
    val summaryText = buildList {
        if (report.breachedCount > 0) add("${report.breachedCount} breached")
        if (report.weakCount > 0) add("${report.weakCount} weak")
        if (report.reusedCount > 0) add("${report.reusedCount} duplicate")
        if (isEmpty() && report.expiredCount > 0) add("${report.expiredCount} expired")
        if (isEmpty()) add("${report.strongCount}/${report.totalLogins} strong & unique")
    }.joinToString(" • ")

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BlackBoxSurface)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("dashboard_health_card"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.5.dp, accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${report.overallScore}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = accent
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = when {
                        hasCritical -> "CRITICAL PASSWORD RISK"
                        hasWarnings -> "SECURITY HEALTH ALERT"
                        else -> "SECURITY HEALTH OPTIMAL"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = accent
                )
                Text(
                    text = summaryText,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = "AUDIT",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = accent,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(accent.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
