package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.BlackBoxDatabase
import com.example.data.VaultRepository
import com.example.model.VaultCategory
import com.example.model.VaultItem
import com.example.security.AttachmentStorageManager
import com.example.security.BiometricManagerWrapper
import com.example.security.OfflineHibpStore
import com.example.security.UnlockBackoffManager
import com.example.ui.VaultUiState
import com.example.ui.screens.LockScreen
import com.example.ui.theme.BlackBoxTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ForgotPasswordResetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `forgot master password button requires ERASE confirmation before wiping vault`() {
        var wipeTriggered = false
        val context = ApplicationProvider.getApplicationContext<Application>()
        val lockedState = VaultUiState(
            isInitialized = true,
            isUnlocked = false,
            isLoading = false
        )

        composeTestRule.setContent {
            BlackBoxTheme {
                LockScreen(
                    uiState = lockedState,
                    biometricManager = BiometricManagerWrapper.getInstance(context),
                    onUnlockPassword = {},
                    onUnlockBiometrics = {},
                    onInitializeVault = { _, _ -> },
                    onTriggerBiometricPrompt = {},
                    onForgotPasswordWipeVault = { wipeTriggered = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("forgot_master_password_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("forgot_password_reset_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("forgot_password_confirm_wipe_button").assertIsNotEnabled()

        // Wrong word keeps erase button disabled
        composeTestRule.onNodeWithTag("forgot_password_confirm_input").performTextInput("RESET")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("forgot_password_confirm_wipe_button").assertIsNotEnabled()

        // Cancel keeps vault intact
        composeTestRule.onNodeWithTag("forgot_password_cancel_button").performClick()
        composeTestRule.waitForIdle()
        assertFalse(wipeTriggered)

        composeTestRule.onNodeWithTag("forgot_master_password_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("forgot_password_confirm_input").performTextInput("ERASE")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("forgot_password_confirm_wipe_button")
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()

        assertTrue(wipeTriggered)
    }

    @Test
    fun `wipeVault and storage cleanup erase all records, attachments, cache, and lockouts`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(context, BlackBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val repo = VaultRepository(db.vaultDao(), db.passwordDao(), db.secureNoteDao())
            val key = repo.initializeVault("OriginalMaster#2026", enableBiometrics = false)
            repo.saveItem(
                VaultItem(
                    id = "secret-1",
                    title = "Primary Email",
                    category = VaultCategory.LOGIN,
                    username = "user@example.com",
                    password = "SecretPassword!9"
                ),
                secretKey = key,
                partitionId = 0
            )
            val attPath = AttachmentStorageManager.saveEncryptedFile(
                context = context,
                rawBytes = "confidential-key".toByteArray(),
                secretKey = key
            )
            val attFile = File(context.filesDir, attPath)
            assertTrue(attFile.exists())

            val backoff = UnlockBackoffManager(context)
            repeat(5) { backoff.recordFailure() }
            assertTrue(backoff.getState().failedAttempts >= 5)

            repo.wipeVault()
            AttachmentStorageManager.deleteAllAttachments(context)
            OfflineHibpStore.clearCache(context)
            backoff.reset()

            assertFalse(repo.isVaultInitialized())
            assertNull(repo.getMetadata())
            assertTrue(repo.getRawEntities(0).isEmpty())
            assertFalse(attFile.exists())
            assertEquals(0, backoff.getState().failedAttempts)
        } finally {
            db.close()
        }
    }
}
