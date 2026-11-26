package com.example.autofill

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.data.BlackBoxDatabase
import com.example.data.VaultRepository
import com.example.model.VaultItem
import com.example.security.BiometricAccessResult
import com.example.security.BiometricVaultAuthService
import com.example.security.UnlockBackoffManager
import com.example.ui.components.SecurityBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlackBoxBorder
import com.example.ui.theme.BlackBoxPitch
import com.example.ui.theme.BlackBoxSurface
import com.example.ui.theme.BlackBoxSurfaceVariant
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.BlackBoxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Secure authentication and credential selection activity invoked by [BlackBoxAutofillService]
 * when filling credentials into third-party apps or Google Chrome.
 */
@RequiresApi(Build.VERSION_CODES.O)
class AutofillAuthActivity : FragmentActivity() {

    private lateinit var repository: VaultRepository
    private lateinit var backoffManager: UnlockBackoffManager
    private val biometricAuthService by lazy { BiometricVaultAuthService.getInstance(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()

        val db = BlackBoxDatabase.getDatabase(applicationContext)
        repository = VaultRepository(
            vaultDao = db.vaultDao(),
            passwordDao = db.passwordDao(),
            secureNoteDao = db.secureNoteDao()
        )
        backoffManager = UnlockBackoffManager(applicationContext)

        @Suppress("DEPRECATION")
        val usernameId: AutofillId? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_USERNAME_ID, AutofillId::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_USERNAME_ID)
        }

        @Suppress("DEPRECATION")
        val passwordId: AutofillId? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_PASSWORD_ID, AutofillId::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_PASSWORD_ID)
        }

        val webDomain = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)

        setContent {
            BlackBoxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BlackBoxPitch
                ) {
                    AutofillAuthScreen(
                        repository = repository,
                        backoffManager = backoffManager,
                        biometricAvailable = biometricAuthService.isBiometricAvailable(),
                        modalityLabel = biometricAuthService.getModalityLabel(),
                        webDomain = webDomain,
                        targetPackage = targetPackage,
                        onLaunchBiometrics = { onSuccess ->
                            biometricAuthService.authenticateToAccessVault(
                                activity = this,
                                title = "Autofill Authentication",
                                subtitle = biometricAuthService.getModalityLabel(),
                                description = "Verify identity to unlock credentials for ${webDomain ?: targetPackage ?: "this app"}",
                                negativeButtonText = "Use Master Password",
                                onResult = { result ->
                                    if (result is BiometricAccessResult.Granted) {
                                        lifecycleScope.launch {
                                            val unlocked = repository.unlockWithBiometrics()
                                            if (unlocked != null) {
                                                backoffManager.reset()
                                                val items = repository.getAllDecryptedItems(unlocked.first, partitionId = 0)
                                                AutofillVaultSession.updateUnlockedItems(items, isDuress = false)
                                                onSuccess(AutofillVaultSession.getUnlockedLogins())
                                            }
                                        }
                                    }
                                }
                            )
                        },
                        onSelectItem = { selectedItem ->
                            val dataset = AutofillDatasetBuilder.buildDataset(
                                packageName = packageName,
                                item = selectedItem,
                                usernameId = usernameId,
                                passwordId = passwordId
                            )
                            if (dataset != null) {
                                val replyIntent = Intent().apply {
                                    putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
                                }
                                setResult(Activity.RESULT_OK, replyIntent)
                            } else {
                                setResult(Activity.RESULT_CANCELED)
                            }
                            finish()
                        },
                        onCancel = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_USERNAME_ID = "extra_username_id"
        const val EXTRA_PASSWORD_ID = "extra_password_id"
        const val EXTRA_WEB_DOMAIN = "extra_web_domain"
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    }
}

@Composable
private fun AutofillAuthScreen(
    repository: VaultRepository,
    backoffManager: UnlockBackoffManager,
    biometricAvailable: Boolean,
    modalityLabel: String,
    webDomain: String?,
    targetPackage: String?,
    onLaunchBiometrics: (onUnlocked: (List<VaultItem>) -> Unit) -> Unit,
    onSelectItem: (VaultItem) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isInitialized by remember { mutableStateOf(true) }
    var isBiometricEnabled by remember { mutableStateOf(false) }
    var unlockedLogins by remember {
        mutableStateOf(
            if (AutofillVaultSession.isUnlocked()) AutofillVaultSession.getUnlockedLogins() else emptyList()
        )
    }
    var isUnlocked by remember { mutableStateOf(AutofillVaultSession.isUnlocked()) }
    var isBusy by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var lockoutRemaining by remember { mutableIntStateOf(backoffManager.getState().lockoutRemainingSeconds) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val meta = repository.getMetadata()
        isInitialized = meta != null && meta.isVaultInitialized
        isBiometricEnabled = meta?.isBiometricEnabled == true && biometricAvailable
        if (!isUnlocked && isInitialized && isBiometricEnabled) {
            onLaunchBiometrics { logins ->
                unlockedLogins = logins
                isUnlocked = true
            }
        }
    }

    LaunchedEffect(lockoutRemaining) {
        if (lockoutRemaining > 0) {
            delay(1000L)
            lockoutRemaining = backoffManager.getState().lockoutRemainingSeconds
        }
    }

    val performPasswordUnlock = {
        if (password.isNotEmpty() && !isBusy && lockoutRemaining <= 0) {
            scope.launch {
                isBusy = true
                errorMsg = null
                val result = repository.unlockWithPasswordOrDuress(password)
                if (result != null) {
                    backoffManager.reset()
                    lockoutRemaining = 0
                    val items = repository.getAllDecryptedItems(result.secretKey, result.partitionId)
                    AutofillVaultSession.updateUnlockedItems(items, isDuress = result.isDuress)
                    unlockedLogins = AutofillVaultSession.getUnlockedLogins()
                    isUnlocked = true
                } else {
                    val state = backoffManager.recordFailure()
                    lockoutRemaining = state.lockoutRemainingSeconds
                    errorMsg = if (state.isLockedOut) {
                        "Too many failed attempts. Wait ${state.lockoutRemainingSeconds}s."
                    } else {
                        "Invalid Master Password."
                    }
                }
                isBusy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
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
                        .border(1.dp, EmeraldNeon, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = EmeraldNeon,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "BLACK-BOX AUTOFILL",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                    Text(
                        text = webDomain?.takeIf { it.isNotBlank() } ?: targetPackage.orEmpty().ifBlank { "Credential Request" },
                        fontSize = 11.sp,
                        color = EmeraldNeon,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onCancel, modifier = Modifier.testTag("autofill_cancel_button")) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel Autofill",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (!isInitialized) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BlackBoxSurfaceVariant)
                    .border(1.dp, BlackBoxBorder, RoundedCornerShape(10.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Open The Black-Box first to initialize your encrypted vault before using Autofill.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        } else if (!isUnlocked) {
            Text(
                text = "Verify your identity to release matching credentials.",
                fontSize = 13.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Master Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { performPasswordUnlock() }),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password visibility",
                            tint = TextSecondary
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldNeon,
                    unfocusedBorderColor = BlackBoxBorder,
                    focusedContainerColor = BlackBoxSurface,
                    unfocusedContainerColor = BlackBoxSurface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (lockoutRemaining > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "BRUTE-FORCE COOLDOWN ACTIVE: ${lockoutRemaining}s",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = AmberWarning
                )
            } else if (errorMsg != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = errorMsg!!, fontSize = 12.sp, color = CrimsonAlert)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = performPasswordUnlock,
                enabled = password.isNotEmpty() && !isBusy && lockoutRemaining <= 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldNeon,
                    contentColor = Color(0xFF003816)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF003816), strokeWidth = 2.dp)
                } else {
                    Text("UNLOCK & SELECT CREDENTIAL", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }

            if (isBiometricEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        onLaunchBiometrics { logins ->
                            unlockedLogins = logins
                            isUnlocked = true
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = EmeraldNeon,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("USE ${modalityLabel.uppercase()}", color = EmeraldNeon, fontFamily = FontFamily.Monospace)
                }
            }
        } else {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter vault logins...", color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldNeon,
                    unfocusedBorderColor = BlackBoxBorder,
                    focusedContainerColor = BlackBoxSurface,
                    unfocusedContainerColor = BlackBoxSurface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            val matched = remember(unlockedLogins, webDomain, targetPackage, searchQuery) {
                val base = AutofillMatcher.matchLogins(unlockedLogins, webDomain, targetPackage, maxResults = 50)
                val q = searchQuery.trim()
                if (q.isEmpty()) base else base.filter {
                    it.item.title.contains(q, ignoreCase = true) ||
                        it.item.username.contains(q, ignoreCase = true) ||
                        it.item.url.contains(q, ignoreCase = true)
                }
            }

            if (matched.isEmpty()) {
                Text(
                    text = "No matching login entries found in your vault.",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(matched, key = { it.item.id }) { match ->
                        val item = match.item
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(BlackBoxSurfaceVariant)
                                .border(
                                    1.dp,
                                    if (match.isDirectMatch) EmeraldNeon.copy(alpha = 0.55f) else BlackBoxBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectItem(item) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = EmeraldNeon,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = item.title.ifBlank { item.url.ifBlank { "Untitled Login" } },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.username.ifBlank { item.url },
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (match.isDirectMatch) {
                                SecurityBadge(text = "MATCH", color = EmeraldNeon)
                            }
                        }
                    }
                }
            }
        }
    }
}
