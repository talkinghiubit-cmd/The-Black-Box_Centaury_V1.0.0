package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.VaultCategory
import com.example.model.VaultHealthReport
import com.example.model.VaultItem
import com.example.security.TotpAuthenticatorService
import com.example.ui.VaultUiState
import com.example.ui.components.CategoryIcon
import com.example.ui.components.CyberVaultBackdrop
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlackBoxBorder
import com.example.ui.theme.BlackBoxBorderSubtle
import com.example.ui.theme.BlackBoxDisplayFont
import com.example.ui.theme.BlackBoxMonoFont
import com.example.ui.theme.BlackBoxPitch
import com.example.ui.theme.BlackBoxSurface
import com.example.ui.theme.BlackBoxSurfaceElevated
import com.example.ui.theme.BlackBoxSurfaceVariant
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanIce
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.IndigoCipher
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BlackBoxSurfaceElevated)
                                .border(1.dp, EmeraldNeon.copy(alpha = 0.38f), RoundedCornerShape(10.dp))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_blackbox_icon),
                                contentDescription = "The Black-Box Emblem",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_brand_upper),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = BlackBoxDisplayFont,
                                letterSpacing = 0.8.sp,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldNeon)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = stringResource(R.string.app_tagline),
                                    fontSize = 10.sp,
                                    color = EmeraldNeon,
                                    fontFamily = BlackBoxMonoFont,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                actions = {
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

                    IconButton(
                        onClick = onOpenCsvImport,
                        modifier = Modifier.testTag("open_csv_import_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Import Browser or CSV Passwords",
                            tint = CyanIce
                        )
                    }

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
                contentColor = Color(0xFF00331D),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("fab_create_item")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Secret Record"
                )
            }
        }
    ) { innerPadding ->
        CyberVaultBackdrop(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Tactical Security Command Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        tint = IndigoCipher,
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

                // Circular SecOps Posture Summary Card
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
                    placeholder = { Text("Search encrypted items, URLs, keys...", color = TextMuted) },
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
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("vault_search_input")
                )

                // Active Health Filter Banner
                if (uiState.activeHealthFilter != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BlackBoxSurfaceVariant)
                            .border(1.dp, AmberWarning.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "FILTER: ${uiState.activeHealthFilter} PASSWORDS (${uiState.filteredItems.size})",
                            fontSize = 11.sp,
                            fontFamily = BlackBoxMonoFont,
                            fontWeight = FontWeight.Bold,
                            color = AmberWarning
                        )
                        IconButton(onClick = onClearHealthFilter, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear filter",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Category & Favorite Filter Rail
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        val isAllSelected = uiState.selectedCategory == null &&
                            !uiState.showFavoritesOnly &&
                            uiState.activeHealthFilter == null
                        FilterChip(
                            selected = isAllSelected,
                            onClick = {
                                if (uiState.showFavoritesOnly) onToggleFavoritesFilter()
                                if (uiState.activeHealthFilter != null) onClearHealthFilter()
                                onCategorySelected(null)
                            },
                            label = {
                                Text(
                                    text = "All (${uiState.decryptedItems.size})",
                                    fontFamily = BlackBoxMonoFont,
                                    fontSize = 11.sp
                                )
                            },
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
                                selected = isAllSelected
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
                            label = {
                                Text(
                                    text = "Favorites",
                                    fontFamily = BlackBoxMonoFont,
                                    fontSize = 11.sp
                                )
                            },
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
                        val isSelected = uiState.selectedCategory == cat &&
                            !uiState.showFavoritesOnly &&
                            uiState.activeHealthFilter == null
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (uiState.showFavoritesOnly) onToggleFavoritesFilter()
                                if (uiState.activeHealthFilter != null) onClearHealthFilter()
                                onCategorySelected(if (isSelected) null else cat)
                            },
                            label = {
                                Text(
                                    text = cat.displayName,
                                    fontFamily = BlackBoxMonoFont,
                                    fontSize = 11.sp
                                )
                            },
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

                // Encrypted Vault List
                if (uiState.filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(BlackBoxSurface)
                                    .border(1.dp, BlackBoxBorder, RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (uiState.decryptedItems.isEmpty()) "ENCLAVE IS EMPTY" else "NO MATCHING RECORDS",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = BlackBoxDisplayFont,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (uiState.decryptedItems.isEmpty())
                                    "Tap + to encrypt a password, confidential note, card, or cryptographic key."
                                else
                                    "Adjust your search query or category filter.",
                                fontSize = 13.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 24.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 92.dp),
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
    val totpToken = remember(item.totpSecret, totpSecondsRemaining) {
        if (item.totpSecret.isNotBlank()) TotpAuthenticatorService.generateToken(item.totpSecret) else null
    }
    val statusAccent = when {
        item.pwnedBreachCount > 0 -> CrimsonAlert
        isWeak || isDuplicate -> AmberWarning
        item.category == VaultCategory.NOTE -> CyanIce
        item.category == VaultCategory.KEY -> IndigoCipher
        item.category == VaultCategory.CARD -> AmberWarning
        else -> EmeraldNeon
    }
    val borderColor = when {
        item.pwnedBreachCount > 0 -> CrimsonAlert.copy(alpha = 0.65f)
        isWeak || isDuplicate -> AmberWarning.copy(alpha = 0.48f)
        else -> BlackBoxBorder
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .testTag("item_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = BlackBoxSurfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Subtle vertical category/posture bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(statusAccent.copy(alpha = 0.85f))
            )

            Spacer(modifier = Modifier.width(10.dp))

            CategoryIcon(category = item.category)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        fontSize = 15.sp,
                        fontFamily = BlackBoxDisplayFont,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

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
                        fontFamily = if (item.category == VaultCategory.KEY || item.category == VaultCategory.CARD) BlackBoxMonoFont else androidx.compose.ui.text.font.FontFamily.Default,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (item.category == VaultCategory.CARD && item.isNfcPayEnabled) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BlackBoxSurface)
                            .border(1.dp, CyanIce.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                            .testTag("card_nfc_pill_${item.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contactless,
                            contentDescription = stringResource(R.string.nfc_pay_cta),
                            tint = CyanIce,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.nfc_pay_cta),
                            fontSize = 10.sp,
                            fontFamily = BlackBoxMonoFont,
                            fontWeight = FontWeight.SemiBold,
                            color = CyanIce
                        )
                    }
                }

                if (totpToken != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val progress = (totpToken.remainingSeconds.toFloat() / totpToken.periodSeconds.toFloat()).coerceIn(0f, 1f)
                    val totpTint = if (totpToken.remainingSeconds <= 5) AmberWarning else CyanIce
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BlackBoxSurface)
                            .border(1.dp, totpTint.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                            .clickable { onCopyTotp(totpToken.code) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "2FA ${totpToken.formattedCode}",
                                fontSize = 11.sp,
                                fontFamily = BlackBoxMonoFont,
                                fontWeight = FontWeight.Bold,
                                color = totpTint
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${totpToken.remainingSeconds}s",
                                fontSize = 9.sp,
                                fontFamily = BlackBoxMonoFont,
                                color = TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .width(84.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(BlackBoxBorderSubtle)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .background(totpTint)
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.username.isNotEmpty()) {
                    IconButton(
                        onClick = onCopyUsername,
                        modifier = Modifier
                            .size(40.dp)
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

                if (item.password.isNotEmpty()) {
                    IconButton(
                        onClick = onCopyPassword,
                        modifier = Modifier
                            .size(40.dp)
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

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(40.dp)
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
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(BlackBoxSurfaceVariant, BlackBoxSurface)
                )
            )
            .border(1.dp, BlackBoxBorder, RoundedCornerShape(12.dp))
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
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = BlackBoxMonoFont,
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
        if (report.reusedCount > 0) add("${report.reusedCount} reused")
        if (isEmpty() && report.expiredCount > 0) add("${report.expiredCount} expired")
        if (isEmpty()) add("${report.strongCount}/${report.totalLogins} strong & unique")
    }.joinToString(" • ")

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(BlackBoxSurfaceVariant, BlackBoxSurface)
                )
            )
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("dashboard_health_card"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 3.5.dp.toPx()
                    drawArc(
                        color = BlackBoxBorder,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke)
                    )
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * (report.overallScore.coerceIn(0, 100) / 100f),
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "${report.overallScore}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BlackBoxMonoFont,
                    color = accent
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = when {
                        hasCritical -> "CRITICAL PASSWORD RISK"
                        hasWarnings -> "POSTURE HARDENING ALERT"
                        else -> "SECURITY POSTURE OPTIMAL"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BlackBoxMonoFont,
                    letterSpacing = 0.5.sp,
                    color = accent
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summaryText,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "AUDIT",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = BlackBoxMonoFont,
            color = accent,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.14f))
                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
