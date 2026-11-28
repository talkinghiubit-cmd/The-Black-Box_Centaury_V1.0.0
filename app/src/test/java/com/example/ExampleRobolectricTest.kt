package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BlackBoxDatabase
import com.example.data.EncryptedBlob
import com.example.data.EncryptedBlobConverter
import com.example.data.EncryptedField
import com.example.data.EncryptedFieldConverter
import com.example.data.PasswordEntity
import com.example.data.SecureNoteEntity
import com.example.data.VaultMetadataEntity
import com.example.data.VaultPartition
import com.example.model.VaultCategory
import com.example.model.VaultItem
import com.example.security.BiometricVaultAuthenticator
import com.example.security.BiometricVaultAuthService
import com.example.security.BlackBoxSecurity
import com.example.security.PasswordOptions
import com.example.security.SecurePasswordGenerator
import com.example.security.SecureShareManager
import com.example.security.ShareDuration
import com.example.security.ShareExpiredException
import com.example.security.ShareDecryptionException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("The Black-Box", appName)
    }

    @Test
    fun `test AES-256-GCM encryption and decryption roundtrip`() {
        val masterPassword = "TestSecretPassword123!".toCharArray()
        val salt = BlackBoxSecurity.generateRandomSalt()
        val key = BlackBoxSecurity.deriveMasterKey(masterPassword, salt)

        val item = VaultItem(
            id = "test-123",
            title = "Test GitHub",
            category = VaultCategory.LOGIN,
            username = "octocat",
            password = "superSecureToken999!",
            notes = "Test encrypted note"
        )

        val serialized = BlackBoxSecurity.serializeItemPayload(item)
        val (ciphertext, iv) = BlackBoxSecurity.encrypt(serialized, key)

        val decryptedJson = BlackBoxSecurity.decrypt(ciphertext, iv, key)
        val decryptedItem = BlackBoxSecurity.deserializeItemPayload(
            id = item.id,
            title = item.title,
            category = item.category,
            isFavorite = item.isFavorite,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt,
            jsonString = decryptedJson
        )

        assertEquals("octocat", decryptedItem.username)
        assertEquals("superSecureToken999!", decryptedItem.password)
        assertEquals("Test encrypted note", decryptedItem.notes)
    }

    @Test
    fun `test password strength generator entropy`() {
        val password = SecurePasswordGenerator.generatePassword(PasswordOptions(length = 24))
        val strength = SecurePasswordGenerator.evaluateStrength(password)
        assertTrue(strength.entropyBits > 60)
        assertTrue(strength.score >= 3)
    }

    @Test
    fun `test SecurePasswordGenerator with custom symbols and character sets`() {
        val customSymbols = "#$%^"
        val options = PasswordOptions(
            length = 32,
            includeUppercase = true,
            includeLowercase = true,
            includeNumbers = true,
            includeSymbols = true,
            customSymbols = customSymbols,
            excludeAmbiguous = true
        )
        val password = SecurePasswordGenerator.generatePassword(options)

        assertEquals(32, password.length)
        assertTrue(password.any { it.isUpperCase() })
        assertTrue(password.any { it.isLowerCase() })
        assertTrue(password.any { it.isDigit() })
        assertTrue(password.any { it in customSymbols })

        // Verify ambiguous characters are strictly excluded
        val ambiguous = SecurePasswordGenerator.AMBIGUOUS_CHARS
        assertFalse(password.any { it in ambiguous })
    }

    @Test
    fun `test SecurePasswordGenerator passphrase mode`() {
        val passphrase = SecurePasswordGenerator.generatePassphrase(wordCount = 5, separator = "-")
        val parts = passphrase.split("-")
        // 5 words + 1 trailing number segment = 6 segments
        assertTrue(parts.size >= 5)
        assertTrue(passphrase.length >= 20)
    }

    @Test
    fun `test SecurePasswordGenerator entropy analysis`() {
        val strongPass = "kX9#mP2\$vL8@qW5!zR4^"
        val analysis = SecurePasswordGenerator.analyzeEntropy(strongPass)
        assertTrue(analysis.entropyBits >= 80)
        assertEquals(4, analysis.score)
        assertTrue(analysis.estimatedCrackTime.contains("Military Grade") || analysis.estimatedCrackTime.contains("Millions"))
    }

    @Test
    fun `test biometric authenticator availability check`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authenticator = BiometricVaultAuthenticator.getInstance(context)
        val availability = authenticator.checkAvailability()
        assertNotNull(availability)
    }

    @Test
    fun `test BiometricManagerWrapper modalities and status detection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wrapper = com.example.security.BiometricManagerWrapper.getInstance(context)
        val status = wrapper.checkStatus()
        assertNotNull(status)

        val modalities = wrapper.getAvailableModalities()
        assertNotNull(modalities)

        val label = wrapper.getModalityDisplayName()
        assertTrue(label.isNotEmpty())

        val actionHint = wrapper.getSensorActionHint()
        assertTrue(actionHint.isNotEmpty())

        val enrollmentIntent = wrapper.createEnrollmentIntent()
        assertNotNull(enrollmentIntent)
    }

    @Test
    fun `test EncryptedFieldConverter type conversion`() {
        val converter = EncryptedFieldConverter()
        val original = EncryptedField(ciphertext = "BASE64CIPHERTEXT==", iv = "BASE64IV==")

        val json = converter.fromEncryptedField(original)
        assertNotNull(json)

        val restored = converter.toEncryptedField(json)
        assertNotNull(restored)
        assertEquals("BASE64CIPHERTEXT==", restored?.ciphertext)
        assertEquals("BASE64IV==", restored?.iv)
    }

    @Test
    fun `test Room database insertion of PasswordEntity and SecureNoteEntity`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = BlackBoxDatabase.getDatabase(context)

        val pwEntity = PasswordEntity(
            id = "pw-test-1",
            title = "Personal Google Account",
            username = "user@example.com",
            encryptedPassword = EncryptedField("CIPHER_PW", "IV_PW"),
            url = "https://accounts.google.com"
        )
        db.passwordDao().insertPassword(pwEntity)

        val queriedPw = db.passwordDao().getPasswordById("pw-test-1")
        assertNotNull(queriedPw)
        assertEquals("Personal Google Account", queriedPw?.title)
        assertEquals("CIPHER_PW", queriedPw?.encryptedPassword?.ciphertext)

        val noteEntity = SecureNoteEntity(
            id = "note-test-1",
            title = "Recovery Seed Phrase",
            encryptedContent = EncryptedField("CIPHER_NOTE", "IV_NOTE"),
            folder = "Seeds"
        )
        db.secureNoteDao().insertNote(noteEntity)

        val queriedNote = db.secureNoteDao().getNoteById("note-test-1")
        assertNotNull(queriedNote)
        assertEquals("Recovery Seed Phrase", queriedNote?.title)
        assertEquals("CIPHER_NOTE", queriedNote?.encryptedContent?.ciphertext)
    }

    @Test
    fun `test ViewModelLifecycleObserver conceals sensitive UI content on background`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = com.example.ui.VaultViewModel(app)

        var biometricTriggered = false
        var biometricCancelled = false

        val observer = com.example.ui.VaultViewModelLifecycleObserver(
            viewModel = viewModel,
            onTriggerBiometric = { biometricTriggered = true },
            onCancelBiometric = { biometricCancelled = true },
            autoPromptDelayMs = 0L,
            coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )

        val mockOwner = object : androidx.lifecycle.LifecycleOwner {
            override val lifecycle = androidx.lifecycle.LifecycleRegistry(this)
        }

        // Simulate app pausing (moving to recent tasks / background)
        observer.onPause(mockOwner)
        assertTrue(viewModel.uiState.value.isSensitiveContentHidden)
        assertTrue(biometricCancelled)

        // Simulate app stopping (fully backgrounded)
        observer.onStop(mockOwner)
        assertTrue(viewModel.uiState.value.isAppInBackground)
        assertFalse(viewModel.uiState.value.isUnlocked)
        assertTrue(viewModel.uiState.value.isSensitiveContentHidden)

        // Simulate app starting again (coming to foreground)
        observer.onStart(mockOwner)
        assertFalse(viewModel.uiState.value.isAppInBackground)
    }

    @Test
    fun `test dual-vault partition isolation in PasswordDao`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = BlackBoxDatabase.getDatabase(context)
        val passwordDao = db.passwordDao()

        // Clear any previous records
        passwordDao.deleteAllPasswords()

        // 1. Insert into Primary Vault (partition 0)
        val primaryPw = PasswordEntity(
            id = "pw-primary-1",
            title = "Real Banking Credential",
            username = "real_user",
            encryptedPassword = EncryptedField("PRIMARY_CIPHER", "IV1"),
            partitionId = VaultPartition.PRIMARY
        )
        passwordDao.insertPassword(primaryPw)

        // 2. Insert into Duress Decoy Vault (partition 1)
        val decoyPw = PasswordEntity(
            id = "pw-decoy-1",
            title = "Decoy Netflix Account",
            username = "decoy_user",
            encryptedPassword = EncryptedField("DECOY_CIPHER", "IV2"),
            partitionId = VaultPartition.DURESS
        )
        passwordDao.insertPassword(decoyPw)

        // 3. Verify partition counts and isolation
        val primaryCount = passwordDao.getPasswordCountByPartition(VaultPartition.PRIMARY)
        val duressCount = passwordDao.getPasswordCountByPartition(VaultPartition.DURESS)
        assertEquals(1, primaryCount)
        assertEquals(1, duressCount)

        val primaryList = passwordDao.getPasswordsByPartition(VaultPartition.PRIMARY)
        assertEquals(1, primaryList.size)
        assertEquals("pw-primary-1", primaryList[0].id)
        assertEquals("Real Banking Credential", primaryList[0].title)

        val duressList = passwordDao.getPasswordsByPartition(VaultPartition.DURESS)
        assertEquals(1, duressList.size)
        assertEquals("pw-decoy-1", duressList[0].id)
        assertEquals("Decoy Netflix Account", duressList[0].title)

        // 4. Verify partition-isolated lookup
        assertNotNull(passwordDao.getPasswordByIdAndPartition("pw-primary-1", VaultPartition.PRIMARY))
        assertEquals(null, passwordDao.getPasswordByIdAndPartition("pw-primary-1", VaultPartition.DURESS))

        // 5. Delete duress partition records only
        passwordDao.deletePasswordsByPartition(VaultPartition.DURESS)
        assertEquals(1, passwordDao.getPasswordCountByPartition(VaultPartition.PRIMARY))
        assertEquals(0, passwordDao.getPasswordCountByPartition(VaultPartition.DURESS))
    }

    @Test
    fun `test dual-vault partition isolation in SecureNoteDao`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = BlackBoxDatabase.getDatabase(context)
        val noteDao = db.secureNoteDao()

        noteDao.deleteAllNotes()

        val primaryNote = SecureNoteEntity(
            id = "note-primary-1",
            title = "Confidential Strategy",
            encryptedContent = EncryptedField("CIPHER_A", "IV_A"),
            folder = "Finance",
            partitionId = VaultPartition.PRIMARY
        )
        noteDao.insertNote(primaryNote)

        val decoyNote = SecureNoteEntity(
            id = "note-decoy-1",
            title = "Shopping Grocery List",
            encryptedContent = EncryptedField("CIPHER_B", "IV_B"),
            folder = "Shopping",
            partitionId = VaultPartition.DURESS
        )
        noteDao.insertNote(decoyNote)

        assertEquals(1, noteDao.getNoteCountByPartition(VaultPartition.PRIMARY))
        assertEquals(1, noteDao.getNoteCountByPartition(VaultPartition.DURESS))

        val primaryNotes = noteDao.getNotesByPartition(VaultPartition.PRIMARY)
        assertEquals("Confidential Strategy", primaryNotes[0].title)

        val decoyNotes = noteDao.getNotesByPartition(VaultPartition.DURESS)
        assertEquals("Shopping Grocery List", decoyNotes[0].title)

        noteDao.deleteNotesByPartition(VaultPartition.DURESS)
        assertEquals(1, noteDao.getNoteCountByPartition(VaultPartition.PRIMARY))
        assertEquals(0, noteDao.getNoteCountByPartition(VaultPartition.DURESS))
    }

    @Test
    fun `test VaultMetadataDao dual-vault configuration and lifecycle`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = BlackBoxDatabase.getDatabase(context)
        val metadataDao = db.metadataDao()

        metadataDao.deleteMetadata()

        val initialMeta = VaultMetadataEntity(
            id = 1,
            masterSalt = "PRIMARY_SALT_BASE64",
            verifierHash = "PRIMARY_VERIFIER_HASH",
            hasDuressPin = false
        )
        metadataDao.insertMetadata(initialMeta)

        val retrieved = metadataDao.getMetadata()
        assertNotNull(retrieved)
        assertEquals(false, retrieved?.hasDuressPin)
        assertFalse(retrieved?.isDualVaultConfigured ?: true)

        // Configure Duress PIN
        metadataDao.updateDuressPin(
            hasDuress = true,
            salt = "DURESS_SALT_BASE64",
            hash = "DURESS_VERIFIER_HASH"
        )
        val updated = metadataDao.getMetadata()
        assertNotNull(updated)
        assertEquals(true, updated?.hasDuressPin)
        assertTrue(updated?.isDualVaultConfigured == true)

        // Remove Duress PIN
        metadataDao.removeDuressPin()
        val cleared = metadataDao.getMetadata()
        assertNotNull(cleared)
        assertEquals(false, cleared?.hasDuressPin)
        assertFalse(cleared?.isDualVaultConfigured ?: true)
    }

    @Test
    fun `test SecureShareManager createEncryptedCapsule and decryptCapsule roundtrip`() {
        val originalItem = VaultItem(
            id = "share-test-1",
            title = "Shared Secret Database",
            category = VaultCategory.LOGIN,
            username = "admin_user",
            password = "SuperSecretPassword123!#",
            url = "https://internal.vault.secure",
            notes = "Time-sensitive shared credential"
        )
        val passphrase = "correct horse battery staple"

        // 1. Create encrypted capsule
        val capsule = SecureShareManager.createEncryptedCapsule(
            item = originalItem,
            passphrase = passphrase,
            duration = ShareDuration.ONE_HOUR,
            burnAfterReading = false
        )

        assertNotNull(capsule)
        assertEquals("Shared Secret Database", capsule.itemTitle)
        assertFalse(capsule.isExpired())
        assertNotNull(capsule.passwordEntropyBits)
        assertTrue((capsule.passwordStrengthScore ?: 0) >= 3)

        // 2. Export as portable token and re-parse
        val token = SecureShareManager.exportCapsuleAsToken(capsule)
        assertTrue(token.startsWith("BBOX-SHARE-v1:"))

        val parsedCapsule = SecureShareManager.parseCapsuleToken(token)
        assertEquals(capsule.id, parsedCapsule.id)
        assertEquals(capsule.itemTitle, parsedCapsule.itemTitle)
        assertEquals(capsule.salt, parsedCapsule.salt)
        assertEquals(capsule.ciphertext, parsedCapsule.ciphertext)

        // 3. Decrypt with correct passphrase
        val decrypted = SecureShareManager.decryptCapsule(parsedCapsule, passphrase)
        assertEquals(originalItem.title, decrypted.title)
        assertEquals(originalItem.username, decrypted.username)
        assertEquals(originalItem.password, decrypted.secret)
        assertEquals(originalItem.url, decrypted.url)
        assertEquals(originalItem.notes, decrypted.notes)
        assertNotNull(decrypted.passwordStrength)
        assertTrue(decrypted.remainingTimeText.isNotEmpty())
    }

    @Test(expected = ShareExpiredException::class)
    fun `test SecureShareManager expired capsule rejection`() {
        val item = VaultItem(
            id = "exp-test",
            title = "Expired Item",
            category = VaultCategory.LOGIN,
            password = "testPassword123"
        )
        val passphrase = "test-passphrase"

        // Create capsule with an expiration in the past
        val capsule = SecureShareManager.createEncryptedCapsule(
            item = item,
            passphrase = passphrase,
            duration = ShareDuration.ONE_HOUR
        ).copy(expiresAt = System.currentTimeMillis() - 5000L) // 5 seconds in the past

        assertTrue(capsule.isExpired())
        // Decrypting must throw ShareExpiredException
        SecureShareManager.decryptCapsule(capsule, passphrase)
    }

    @Test(expected = ShareDecryptionException::class)
    fun `test SecureShareManager wrong passphrase rejection`() {
        val item = VaultItem(
            id = "wrong-pw-test",
            title = "Encrypted Secret",
            category = VaultCategory.LOGIN,
            password = "testPassword123"
        )
        val capsule = SecureShareManager.createEncryptedCapsule(
            item = item,
            passphrase = "correct-passphrase",
            duration = ShareDuration.TWENTY_FOUR_HOURS
        )

        // Attempt decryption with wrong passphrase
        SecureShareManager.decryptCapsule(capsule, "wrong-passphrase")
    }

    @Test(expected = ShareDecryptionException::class)
    fun `test SecureShareManager tamper resistance via AAD`() {
        val item = VaultItem(
            id = "tamper-test",
            title = "Tamper Proof Secret",
            category = VaultCategory.LOGIN,
            password = "testPassword123"
        )
        val passphrase = "passphrase-for-tamper-test"
        val capsule = SecureShareManager.createEncryptedCapsule(
            item = item,
            passphrase = passphrase,
            duration = ShareDuration.ONE_HOUR
        )

        // Tamper with expiration timestamp in the capsule header without knowing key
        val tamperedCapsule = capsule.copy(expiresAt = capsule.expiresAt + 86400_000L)

        // Decryption MUST fail because AAD is bound to the original expiration timestamp
        SecureShareManager.decryptCapsule(tamperedCapsule, passphrase)
    }

    @Test
    fun `test EncryptedBlob packing and unpacking`() {
        val iv = ByteArray(12) { (it * 3).toByte() }
        val ciphertext = "SuperSecretEncryptedPayloadBytesWithTag12345".toByteArray(Charsets.UTF_8)
        val originalBlob = EncryptedBlob(ciphertext = ciphertext, iv = iv)

        // Pack to binary SQLite BLOB
        val packed = originalBlob.toBlob()
        assertEquals(12 + ciphertext.size, packed.size)

        // Verify first 12 bytes are IV
        for (i in 0 until 12) {
            assertEquals(iv[i], packed[i])
        }

        // Unpack from binary SQLite BLOB
        val unpacked = EncryptedBlob.fromBlob(packed)
        assertEquals(originalBlob, unpacked)
        assertTrue(originalBlob.ciphertext.contentEquals(unpacked.ciphertext))
        assertTrue(originalBlob.iv.contentEquals(unpacked.iv))

        // Conversion between EncryptedField and EncryptedBlob
        val field = originalBlob.toEncryptedField()
        val fromField = EncryptedBlob.fromEncryptedField(field)
        assertEquals(originalBlob, fromField)
    }

    @Test
    fun `test EncryptedBlobConverter TypeConverter roundtrip`() {
        val converter = EncryptedBlobConverter()
        val iv = ByteArray(12) { it.toByte() }
        val ct = "SensitiveData123".toByteArray(Charsets.UTF_8)
        val blob = EncryptedBlob(ciphertext = ct, iv = iv)

        val byteArray = converter.fromEncryptedBlob(blob)
        assertNotNull(byteArray)
        assertEquals(12 + ct.size, byteArray?.size)

        val restored = converter.toEncryptedBlob(byteArray)
        assertNotNull(restored)
        assertEquals(blob, restored)

        // Null safety
        assertEquals(null, converter.fromEncryptedBlob(null))
        assertEquals(null, converter.toEncryptedBlob(null))
        assertEquals(null, converter.toEncryptedBlob(ByteArray(5))) // under 12 bytes
    }

    @Test
    fun `test Room database insertion and retrieval of PasswordEntity with EncryptedBlob`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = BlackBoxDatabase.getDatabase(context)
        val passwordDao = db.passwordDao()

        val iv = ByteArray(12) { 7 }
        val ct = "EncryptedSecretVaultPassword".toByteArray(Charsets.UTF_8)
        val blob = EncryptedBlob(ciphertext = ct, iv = iv)

        val pwEntity = PasswordEntity(
            id = "blob-pw-test-1",
            title = "ProtonMail Secure",
            username = "security_lead",
            encryptedPassword = blob.toEncryptedField(),
            encryptedPasswordBlob = blob,
            url = "https://mail.proton.me",
            isFavorite = true,
            partitionId = VaultPartition.PRIMARY
        )

        passwordDao.insertPassword(pwEntity)

        val retrieved = passwordDao.getPasswordByIdAndPartition("blob-pw-test-1", VaultPartition.PRIMARY)
        assertNotNull(retrieved)
        assertEquals("ProtonMail Secure", retrieved?.title)
        assertEquals("security_lead", retrieved?.username)
        assertTrue(retrieved?.isFavorite == true)
        assertEquals(pwEntity.encryptedPassword, retrieved?.encryptedPassword)

        // Verify native binary blob was stored and retrieved via Room TypeConverter
        assertNotNull(retrieved?.encryptedPasswordBlob)
        assertEquals(blob, retrieved?.encryptedPasswordBlob)
        assertTrue(blob.ciphertext.contentEquals(retrieved?.encryptedPasswordBlob?.ciphertext))
        assertTrue(blob.iv.contentEquals(retrieved?.encryptedPasswordBlob?.iv))
    }

    @Test
    fun `test Room database insertion and retrieval of SecureNoteEntity with EncryptedBlob`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = BlackBoxDatabase.getDatabase(context)
        val noteDao = db.secureNoteDao()

        val iv = ByteArray(12) { 9 }
        val ct = "Ultra Confidential Recovery Mnemonic Phrases".toByteArray(Charsets.UTF_8)
        val blob = EncryptedBlob(ciphertext = ct, iv = iv)

        val noteEntity = SecureNoteEntity(
            id = "blob-note-test-1",
            title = "Hardware Wallet Seed",
            encryptedContent = blob.toEncryptedField(),
            encryptedContentBlob = blob,
            folder = "Crypto",
            isFavorite = true,
            partitionId = VaultPartition.DURESS
        )

        noteDao.insertNote(noteEntity)

        val retrieved = noteDao.getNoteByIdAndPartition("blob-note-test-1", VaultPartition.DURESS)
        assertNotNull(retrieved)
        assertEquals("Hardware Wallet Seed", retrieved?.title)
        assertEquals("Crypto", retrieved?.folder)
        assertEquals(VaultPartition.DURESS, retrieved?.partitionId)

        // Verify native binary blob persistence
        assertNotNull(retrieved?.encryptedContentBlob)
        assertEquals(blob, retrieved?.encryptedContentBlob)
        assertTrue(blob.ciphertext.contentEquals(retrieved?.encryptedContentBlob?.ciphertext))
    }

    @Test
    fun `test Room database VaultMetadataEntity with binary BLOB`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = BlackBoxDatabase.getDatabase(context)
        val metaDao = db.metadataDao()

        val biometricKeyBytes = ByteArray(32) { (it * 2).toByte() }

        val meta = VaultMetadataEntity(
            id = 1,
            masterSalt = "MASTER_SALT_BASE64",
            verifierHash = "VERIFIER_HASH_BASE64",
            isBiometricEnabled = true,
            biometricKeyBlob = biometricKeyBytes,
            hasDuressPin = true,
            duressSalt = "DURESS_SALT_BASE64",
            duressVerifierHash = "DURESS_HASH_BASE64"
        )

        metaDao.insertMetadata(meta)

        val retrieved = metaDao.getMetadata()
        assertNotNull(retrieved)
        assertEquals(true, retrieved?.isBiometricEnabled)
        assertTrue(biometricKeyBytes.contentEquals(retrieved?.biometricKeyBlob))
        assertTrue(retrieved?.isDualVaultConfigured == true)
    }

    @Test
    fun `test BiometricVaultAuthService instance and status queries`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = BiometricVaultAuthService.getInstance(context)
        assertNotNull(service)

        // Verify singleton instance consistency
        val service2 = BiometricVaultAuthService.getInstance(context)
        assertEquals(service, service2)

        // In Robolectric environment without simulated hardware, should return boolean without crashing
        val isAvail = service.isBiometricAvailable()
        val status = service.getAvailabilityStatus()
        assertNotNull(status)

        val modality = service.getModalityLabel()
        assertTrue(modality.isNotEmpty())

        val hint = service.getSensorActionHint()
        assertTrue(hint.isNotEmpty())

        assertFalse(service.isAuthenticating.value)
    }

    @Test
    fun `test SecureShareManager preserves NOTE, CARD, and KEY fields without polluting password`() {
        val passphrase = "audit-passphrase-2026"

        // 1. NOTE should keep password empty and preserve notes
        val noteItem = VaultItem(
            id = "note-share-1",
            title = "Recovery Codes",
            category = VaultCategory.NOTE,
            notes = "alpha-beta-gamma-delta"
        )
        val noteCapsule = SecureShareManager.createEncryptedCapsule(noteItem, passphrase, ShareDuration.ONE_HOUR)
        val decryptedNote = SecureShareManager.decryptCapsule(noteCapsule, passphrase)
        assertEquals(VaultCategory.NOTE, decryptedNote.category)
        assertEquals("", decryptedNote.password)
        assertEquals("alpha-beta-gamma-delta", decryptedNote.notes)

        // 2. CARD should preserve card fields
        val cardItem = VaultItem(
            id = "card-share-1",
            title = "Corporate Visa",
            category = VaultCategory.CARD,
            cardHolder = "Ada Lovelace",
            cardNumber = "4111222233334444",
            cardExpiry = "12/29",
            cardCvv = "842"
        )
        val cardCapsule = SecureShareManager.createEncryptedCapsule(cardItem, passphrase, ShareDuration.ONE_HOUR)
        val decryptedCard = SecureShareManager.decryptCapsule(cardCapsule, passphrase)
        assertEquals(VaultCategory.CARD, decryptedCard.category)
        assertEquals("Ada Lovelace", decryptedCard.cardHolder)
        assertEquals("4111222233334444", decryptedCard.cardNumber)
        assertEquals("12/29", decryptedCard.cardExpiry)
        assertEquals("842", decryptedCard.cardCvv)
        assertEquals("", decryptedCard.password)

        // 3. KEY should preserve keyType and keyValue
        val keyItem = VaultItem(
            id = "key-share-1",
            title = "Prod Deploy Key",
            category = VaultCategory.KEY,
            keyType = "SSH-ED25519",
            keyValue = "AAAAC3NzaC1lZDI1NTE5AAAAIExampleKeyData"
        )
        val keyCapsule = SecureShareManager.createEncryptedCapsule(keyItem, passphrase, ShareDuration.ONE_HOUR)
        val decryptedKey = SecureShareManager.decryptCapsule(keyCapsule, passphrase)
        assertEquals(VaultCategory.KEY, decryptedKey.category)
        assertEquals("SSH-ED25519", decryptedKey.keyType)
        assertEquals("AAAAC3NzaC1lZDI1NTE5AAAAIExampleKeyData", decryptedKey.keyValue)
        assertEquals("", decryptedKey.password)
    }

    @Test
    fun `test VaultRepository setBiometricEnabled verifies password and changeMasterPassword preserves duress partition`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = BlackBoxDatabase.getDatabase(context)
        val repo = com.example.data.VaultRepository(db.vaultDao(), db.passwordDao(), db.secureNoteDao())
        repo.wipeVault()

        val primaryKey = repo.initializeVault("PrimaryMaster123!", enableBiometrics = false)
        repo.saveItem(
            VaultItem(
                id = "primary-login-1",
                title = "Primary Bank",
                category = VaultCategory.LOGIN,
                username = "owner",
                password = "RealPassword!99"
            ),
            secretKey = primaryKey,
            partitionId = VaultPartition.PRIMARY
        )

        val duressKey = repo.setupDuressPin("9999")
        repo.saveItem(
            VaultItem(
                id = "duress-login-1",
                title = "Decoy Streaming",
                category = VaultCategory.LOGIN,
                username = "decoy",
                password = "DecoyPassword!11"
            ),
            secretKey = duressKey,
            partitionId = VaultPartition.DURESS
        )

        // Wrong password must fail biometric toggle verification
        val wrongBioResult = repo.setBiometricEnabled(true, "WrongMasterPassword!")
        assertFalse(wrongBioResult)
        assertFalse(repo.getMetadata()?.isBiometricEnabled == true)

        // Rotating primary password must fail with wrong old password and preserve duress items when succeeding
        assertFalse(repo.changeMasterPassword("WrongMasterPassword!", "NewPrimaryMaster456!"))
        assertTrue(repo.changeMasterPassword("PrimaryMaster123!", "NewPrimaryMaster456!"))

        // Primary record re-encrypted under new password, and Duress partition untouched
        val newPrimaryUnlock = repo.unlockWithPasswordOrDuress("NewPrimaryMaster456!")
        assertNotNull(newPrimaryUnlock)
        assertFalse(newPrimaryUnlock!!.isDuress)
        val primaryItems = repo.getAllDecryptedItems(newPrimaryUnlock.secretKey, VaultPartition.PRIMARY)
        assertEquals(1, primaryItems.size)
        assertEquals("RealPassword!99", primaryItems[0].password)

        val duressUnlock = repo.unlockWithPasswordOrDuress("9999")
        assertNotNull(duressUnlock)
        assertTrue(duressUnlock!!.isDuress)
        val duressItems = repo.getAllDecryptedItems(duressUnlock.secretKey, VaultPartition.DURESS)
        assertEquals(1, duressItems.size)
        assertEquals("DecoyPassword!11", duressItems[0].password)
        assertEquals(1, db.passwordDao().getPasswordCountByPartition(VaultPartition.DURESS))
        assertEquals(1, db.passwordDao().getPasswordCountByPartition(VaultPartition.PRIMARY))
    }

    @Test
    fun `test SecureShareManager burn-after-reading tracking and SnapshotManager metadata roundtrip`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val capsuleId = "burn-cap-${System.currentTimeMillis()}"
        assertFalse(SecureShareManager.isCapsuleBurned(context, capsuleId))
        SecureShareManager.markCapsuleBurned(context, capsuleId)
        assertTrue(SecureShareManager.isCapsuleBurned(context, capsuleId))

        val meta = VaultMetadataEntity(
            id = 1,
            masterSalt = "SALT_B64",
            verifierHash = "HASH_B64"
        )
        val record = com.example.data.VaultRecordEntity(
            id = "snap-rec-1",
            title = "GitLab",
            category = "LOGIN",
            encryptedPayload = "CIPHER",
            initializationVector = "IV",
            isFavorite = true,
            passwordUpdatedAt = 1700000000000L,
            pwnedBreachCount = 3,
            createdAt = 1690000000000L,
            updatedAt = 1700000000000L
        )
        val file = com.example.security.SnapshotManager.createSnapshotFile(context, meta, listOf(record))
        val parsed = com.example.security.SnapshotManager.parseSnapshot(file.readText())
        assertTrue(parsed.isSuccess)
        val restored = parsed.getOrThrow().records.single()
        assertEquals(3, restored.pwnedBreachCount)
        assertEquals(1700000000000L, restored.passwordUpdatedAt)
        file.delete()
    }

    private fun synthesizeVocalSignal(
        fundamentalHz: Double,
        formantHz: List<Double>,
        amplitude: Double = 0.45,
        durationSeconds: Double = 2.0,
        phraseModulationSeed: Int = 1
    ): ShortArray {
        val sampleRate = com.example.security.VoiceBiometricEngine.SAMPLE_RATE
        val totalSamples = (sampleRate * durationSeconds).toInt()
        val out = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            // Speech-like syllable amplitude envelope with brief pauses
            val env = kotlin.math.abs(kotlin.math.sin(2.0 * Math.PI * (1.8 + 0.1 * phraseModulationSeed) * t))
            if (env < 0.15) continue
            // Slight natural vibrato on fundamental
            val f0 = fundamentalHz * (1.0 + 0.015 * kotlin.math.sin(2.0 * Math.PI * 5.0 * t))
            var sample = 0.0
            for (h in 1..14) {
                val freq = f0 * h
                if (freq >= sampleRate / 2.0) break
                // Resonant formant shaping
                var formantGain = 0.15
                for (fCenter in formantHz) {
                    val dist = kotlin.math.abs(freq - fCenter) / 220.0
                    formantGain += kotlin.math.exp(-dist * dist)
                }
                sample += (formantGain / kotlin.math.sqrt(h.toDouble())) * kotlin.math.sin(2.0 * Math.PI * freq * t)
            }
            val scaled = (sample * env * amplitude * 12000.0).coerceIn(-32000.0, 32000.0)
            out[i] = scaled.toInt().toShort()
        }
        return out
    }

    @Test
    fun `test VoiceBiometricEngine speaker verification accepts matching speaker and rejects impostor or silence`() {
        val engine = com.example.security.VoiceBiometricEngine

        // 1. Silence and short audio must be rejected cleanly
        val silence = ShortArray(engine.SAMPLE_RATE * 2)
        assertTrue(engine.extractSpeakerProfile(silence).isFailure)
        val tooShort = ShortArray(engine.SAMPLE_RATE / 2) { 1000 }
        assertTrue(engine.extractSpeakerProfile(tooShort).isFailure)

        // 2. Enroll Speaker A (125 Hz fundamental, distinct vocal tract formants)
        val speakerASamples = listOf(
            synthesizeVocalSignal(125.0, listOf(520.0, 1450.0, 2550.0), amplitude = 0.42, phraseModulationSeed = 1),
            synthesizeVocalSignal(127.0, listOf(530.0, 1470.0, 2580.0), amplitude = 0.50, phraseModulationSeed = 2),
            synthesizeVocalSignal(124.0, listOf(515.0, 1440.0, 2520.0), amplitude = 0.38, phraseModulationSeed = 3)
        )
        val templateResult = engine.buildEnrollmentTemplate(speakerASamples)
        assertTrue("Enrollment should succeed for consistent speaker: ${templateResult.exceptionOrNull()?.message}", templateResult.isSuccess)
        val template = templateResult.getOrThrow()

        // Serialize / deserialize roundtrip
        val restoredTemplate = engine.deserializeTemplate(engine.serializeTemplate(template))
        assertTrue(template.embedding.contentEquals(restoredTemplate.embedding))

        // 3. Verify genuine Speaker A attempt (slightly different volume & modulation)
        val speakerALive = synthesizeVocalSignal(126.0, listOf(525.0, 1460.0, 2560.0), amplitude = 0.62, phraseModulationSeed = 4)
        val genuineResult = engine.verifyAgainstTemplate(speakerALive, restoredTemplate)
        assertTrue("Genuine speaker should pass (${genuineResult.confidencePercent}%): ${genuineResult.qualityMessage}", genuineResult.isVerified)
        assertTrue(genuineResult.similarityScore >= engine.VERIFICATION_THRESHOLD)

        // 4. Verify Speaker B impostor (240 Hz fundamental, shifted vocal tract formants) is rejected
        val speakerBImpostor = synthesizeVocalSignal(245.0, listOf(820.0, 2100.0, 3400.0), amplitude = 0.50, phraseModulationSeed = 1)
        val impostorResult = engine.verifyAgainstTemplate(speakerBImpostor, restoredTemplate)
        assertFalse("Impostor speaker must be rejected (${impostorResult.confidencePercent}%)", impostorResult.isVerified)
    }

    @Test
    fun `test VaultRepository voice biometric enrollment unlock rotation and disable`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = BlackBoxDatabase.getDatabase(context)
        val keyGen = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }
        val testAesKey = keyGen.generateKey()
        val testProtector = object : com.example.data.VoiceDataProtector {
            override fun protect(plaintext: String): String {
                val (ct, iv) = BlackBoxSecurity.encrypt(plaintext, testAesKey)
                return "$iv:$ct"
            }

            override fun unprotect(wrapped: String): String {
                val parts = wrapped.split(":", limit = 2)
                return BlackBoxSecurity.decrypt(parts[1], parts[0], testAesKey)
            }
        }
        val repo = com.example.data.VaultRepository(
            vaultDao = db.vaultDao(),
            passwordDao = db.passwordDao(),
            secureNoteDao = db.secureNoteDao(),
            voiceDataProtector = testProtector
        )
        repo.wipeVault()
        val masterKey = repo.initializeVault("VoiceVaultMaster#2026", enableBiometrics = false)
        repo.saveItem(
            VaultItem(
                id = "voice-item-1",
                title = "Voice Guarded Secret",
                category = VaultCategory.LOGIN,
                username = "speaker_owner",
                password = "UltraSecretValue!88"
            ),
            secretKey = masterKey,
            partitionId = VaultPartition.PRIMARY
        )

        val enrollSamples = listOf(
            synthesizeVocalSignal(130.0, listOf(550.0, 1500.0, 2600.0), phraseModulationSeed = 1),
            synthesizeVocalSignal(132.0, listOf(560.0, 1510.0, 2620.0), phraseModulationSeed = 2),
            synthesizeVocalSignal(129.0, listOf(545.0, 1490.0, 2590.0), phraseModulationSeed = 3)
        )

        // Wrong master password cannot enroll
        assertTrue(repo.enrollVoiceBiometric("WrongPassword!", enrollSamples).isFailure)

        // Valid enrollment succeeds and persists
        val enrollRes = repo.enrollVoiceBiometric("VoiceVaultMaster#2026", enrollSamples)
        assertTrue(enrollRes.isSuccess)
        assertTrue(repo.getMetadata()?.isVoiceBiometricEnabled == true)

        // Impostor attempt is rejected without releasing secret key
        val impostorAudio = synthesizeVocalSignal(260.0, listOf(880.0, 2250.0, 3550.0), phraseModulationSeed = 2)
        val rejectedOutcome = repo.verifyAndUnlockWithVoice(impostorAudio)
        assertTrue(rejectedOutcome is com.example.data.VoiceUnlockOutcome.Rejected)

        // Genuine speaker unlocks and decrypts vault items
        val genuineAudio = synthesizeVocalSignal(131.0, listOf(555.0, 1505.0, 2610.0), phraseModulationSeed = 4)
        val successOutcome = repo.verifyAndUnlockWithVoice(genuineAudio)
        assertTrue(successOutcome is com.example.data.VoiceUnlockOutcome.Success)
        val unlocked = successOutcome as com.example.data.VoiceUnlockOutcome.Success
        val decryptedItems = repo.getAllDecryptedItems(unlocked.secretKey, VaultPartition.PRIMARY)
        assertEquals(1, decryptedItems.size)
        assertEquals("UltraSecretValue!88", decryptedItems[0].password)

        // Master password rotation re-wraps voice credentials seamlessly
        assertTrue(repo.changeMasterPassword("VoiceVaultMaster#2026", "RotatedVoiceMaster#2027"))
        val postRotateUnlock = repo.verifyAndUnlockWithVoice(genuineAudio)
        assertTrue(postRotateUnlock is com.example.data.VoiceUnlockOutcome.Success)
        assertEquals("RotatedVoiceMaster#2027", (postRotateUnlock as com.example.data.VoiceUnlockOutcome.Success).masterPassword)

        // Disable requires password and purges profile
        assertFalse(repo.disableVoiceBiometric("WrongPass!"))
        assertTrue(repo.disableVoiceBiometric("RotatedVoiceMaster#2027"))
        assertFalse(repo.getMetadata()?.isVoiceBiometricEnabled == true)
        assertTrue(repo.verifyAndUnlockWithVoice(genuineAudio) is com.example.data.VoiceUnlockOutcome.Unavailable)
    }

    @Test
    fun `test CsvPasswordImporter parses Google Chrome CSV with RFC-4180 quotes BOM and duplicate detection`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = BlackBoxDatabase.getDatabase(context)
        val repo = com.example.data.VaultRepository(db.vaultDao(), db.passwordDao(), db.secureNoteDao())
        repo.wipeVault()

        val key = repo.initializeVault("CsvMasterPass#2026", enableBiometrics = false)
        val existingGitHub = VaultItem(
            id = "existing-gh-1",
            title = "GitHub",
            category = VaultCategory.LOGIN,
            username = "octocat",
            password = "ExistingPassword!123",
            url = "https://github.com/login"
        )
        repo.saveItem(existingGitHub, key, VaultPartition.PRIMARY)

        // Chrome CSV with UTF-8 BOM, quoted comma, escaped double quotes (""), multiline note, and duplicate
        val chromeCsv = "\uFEFFname,url,username,password,note\r\n" +
            "GitHub,https://github.com,octocat,ExistingPassword!123,\r\n" +
            "\"AWS, Console\",https://console.aws.amazon.com,devops@corp.io,\"Pass\"\"With,Quotes!\",\"Line 1\nLine 2 backup\"\r\n" +
            ",https://accounts.google.com/ServiceLogin,alex@gmail.com,G00glePass#99,Main Google\r\n" +
            ",,,,\r\n"

        val parsedResult = com.example.security.CsvPasswordImporter.parseFromString(
            csvContent = chromeCsv,
            sourceFileName = "Chrome Passwords.csv",
            existingItems = listOf(existingGitHub)
        )
        assertTrue("Expected valid Chrome CSV parse: ${parsedResult.exceptionOrNull()?.message}", parsedResult.isSuccess)
        val preview = parsedResult.getOrThrow()

        assertEquals("Google Chrome CSV", preview.detectedFormat)
        assertEquals(3, preview.parsedItems.size)
        assertEquals(1, preview.skippedRows)
        assertEquals(1, preview.duplicateCount)

        val aws = preview.parsedItems[1]
        assertEquals("AWS, Console", aws.title)
        assertEquals("Pass\"With,Quotes!", aws.password)
        assertEquals("Line 1\nLine 2 backup", aws.notes)

        val google = preview.parsedItems[2]
        assertEquals("accounts.google.com", google.title)
        assertEquals("G00glePass#99", google.password)

        // Import skipping duplicate should save the 2 new records encrypted into PRIMARY partition
        val toImport = preview.parsedItems.filterNot { it.id in preview.duplicateKeys }
        repo.saveItemsBatch(toImport, key, VaultPartition.PRIMARY)

        val allDecrypted = repo.getAllDecryptedItems(key, VaultPartition.PRIMARY)
        assertEquals(3, allDecrypted.size)
        assertTrue(allDecrypted.any { it.title == "AWS, Console" && it.password == "Pass\"With,Quotes!" })
        assertTrue(allDecrypted.any { it.title == "accounts.google.com" && it.username == "alex@gmail.com" })

        // Unrecognized header format must fail cleanly
        val badCsv = "foo,bar,baz\n1,2,3"
        assertTrue(com.example.security.CsvPasswordImporter.parseFromString(badCsv).isFailure)
    }

    @Test
    fun `test BreachChecker k-Anonymity sends only 5-char prefix handles padding and deduplicates batch scans`() = runBlocking {
        // Canonical SHA-1("password") = 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8
        val partition = com.example.security.BreachChecker.partitionForKAnonymity("password")
        assertEquals("5BAA6", partition.prefix)
        assertEquals("1E4C9B93F3F0682250B6CF8331B7EE68FD8", partition.suffix)

        val queriedPrefixes = mutableListOf<String>()
        val mockRangeFetcher = com.example.security.HibpRangeFetcher { prefix ->
            // Assert strictly k-Anonymity: only 5-char uppercase hex prefix is sent externally
            assertEquals(5, prefix.length)
            assertTrue(prefix.all { it in '0'..'9' || it in 'A'..'F' })
            assertFalse(prefix.contains("password", ignoreCase = true))
            queriedPrefixes.add(prefix)

            when (prefix) {
                "5BAA6" -> Result.success(
                    "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n" +
                        "1E4C9B93F3F0682250B6CF8331B7EE68FD8:3861493\r\n" +
                        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:0\r\n"
                )
                else -> {
                    val cleanPartition = com.example.security.BreachChecker.partitionForKAnonymity("UniqueStrongVault#9912!")
                    Result.success("${cleanPartition.suffix}:0\r\nAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:42\r\n")
                }
            }
        }

        // Single leaked password
        val leakedResult = com.example.security.BreachChecker.checkPasswordDetailed("password", mockRangeFetcher)
        assertTrue(leakedResult is com.example.security.BreachCheckResult.Breached)
        assertEquals(3861493, (leakedResult as com.example.security.BreachCheckResult.Breached).count)

        // Single clean password with Add-Padding ":0" entry must report Clean
        val cleanResult = com.example.security.BreachChecker.checkPasswordDetailed("UniqueStrongVault#9912!", mockRangeFetcher)
        assertTrue(cleanResult is com.example.security.BreachCheckResult.Clean)

        // Network failure returns Failure / -1 cleanly
        val failingFetcher = com.example.security.HibpRangeFetcher {
            Result.failure(java.io.IOException("Offline"))
        }
        assertEquals(-1, com.example.security.BreachChecker.checkPassword("password", failingFetcher))

        // Batch audit across duplicate passwords & non-login items reuses transient prefix cache
        queriedPrefixes.clear()
        val items = listOf(
            VaultItem(id = "login-1", title = "Site A", category = VaultCategory.LOGIN, password = "password"),
            VaultItem(id = "login-2", title = "Site B", category = VaultCategory.LOGIN, password = "password"),
            VaultItem(id = "login-3", title = "Site C", category = VaultCategory.LOGIN, password = "UniqueStrongVault#9912!"),
            VaultItem(id = "note-1", title = "Note Only", category = VaultCategory.NOTE, notes = "ignored")
        )
        val summary = com.example.security.BreachChecker.auditPasswords(
            items = items,
            rangeFetcher = mockRangeFetcher,
            rateLimitDelayMs = 0L
        )
        assertEquals(3, summary.checkedCount)
        assertEquals(2, summary.breachedCount)
        assertEquals(0, summary.errorCount)
        assertEquals(2, summary.uniquePrefixQueries)
        assertEquals(1, summary.cacheHits)
        assertEquals(3861493, summary.resultsByRecordId["login-1"])
        assertEquals(3861493, summary.resultsByRecordId["login-2"])
        assertEquals(0, summary.resultsByRecordId["login-3"])
        assertFalse(summary.resultsByRecordId.containsKey("note-1"))
    }

    @Test
    fun `test OfflineHibpStore caches online ranges imports SHA1 dataset and verifies offline`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.security.OfflineHibpStore.clearCache(context)
        assertEquals(0, com.example.security.OfflineHibpStore.getStats(context).cachedPrefixCount)

        // 1. Import an offline HIBP SHA-1 hash dataset (`FULL_SHA1:COUNT`)
        val leakedPwHash = BlackBoxSecurity.sha1Hex("OfflineLeakedPass!77")
        val offlineDataset = "# Offline HIBP subset\n" +
            "$leakedPwHash:942\n" +
            "5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8:3861493\n" +
            "INVALID_LINE\n"
        val importResult = com.example.security.OfflineHibpStore.importFromReader(
            context = context,
            reader = java.io.StringReader(offlineDataset),
            sourceFileName = "hibp_subset.txt"
        )
        assertTrue(importResult.isSuccess)
        val imported = importResult.getOrThrow()
        assertEquals(2, imported.importedEntries)
        assertEquals(1, imported.skippedLines)
        assertTrue(com.example.security.OfflineHibpStore.getStats(context).cachedPrefixCount >= 2)

        // 2. Verify completely offline (fetcher must NOT be called for cached prefixes)
        val forbidNetworkFetcher = com.example.security.HibpRangeFetcher { prefix ->
            org.junit.Assert.fail("Network fetcher must not be invoked for cached prefix $prefix while offline")
            Result.failure(AssertionError("Should not reach"))
        }

        val offlineBreached = com.example.security.BreachChecker.checkPasswordOfflineFirst(
            context = context,
            password = "OfflineLeakedPass!77",
            rangeFetcher = forbidNetworkFetcher,
            isOnline = false
        )
        assertTrue(offlineBreached is com.example.security.BreachCheckResult.Breached)
        assertEquals(942, (offlineBreached as com.example.security.BreachCheckResult.Breached).count)
        assertEquals(com.example.security.BreachVerificationSource.OFFLINE_CACHE, offlineBreached.source)

        // Uncached prefix while offline returns OfflineUnverified cleanly without network call
        val offlineUnverified = com.example.security.BreachChecker.checkPasswordOfflineFirst(
            context = context,
            password = "NeverSyncedBrandNewPassword!#2026",
            rangeFetcher = forbidNetworkFetcher,
            isOnline = false
        )
        assertTrue(offlineUnverified is com.example.security.BreachCheckResult.OfflineUnverified)

        // 3. Online fetch caches new prefix on disk, then works completely offline on subsequent audit
        var onlineCalls = 0
        val onlineFetcher = com.example.security.HibpRangeFetcher {
            onlineCalls++
            Result.success("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:5\n")
        }
        val firstOnline = com.example.security.BreachChecker.checkPasswordOfflineFirst(
            context = context,
            password = "NeverSyncedBrandNewPassword!#2026",
            rangeFetcher = onlineFetcher,
            isOnline = true
        )
        assertTrue(firstOnline is com.example.security.BreachCheckResult.Clean)
        assertEquals(1, onlineCalls)

        val offlineAudit = com.example.security.BreachChecker.auditPasswordsOfflineFirst(
            context = context,
            items = listOf(
                VaultItem(id = "off-1", title = "Cached Breach", category = VaultCategory.LOGIN, password = "OfflineLeakedPass!77"),
                VaultItem(id = "off-2", title = "Synced Clean", category = VaultCategory.LOGIN, password = "NeverSyncedBrandNewPassword!#2026")
            ),
            rangeFetcher = forbidNetworkFetcher,
            isOnline = false,
            rateLimitDelayMs = 0L
        )
        assertEquals(2, offlineAudit.checkedCount)
        assertEquals(1, offlineAudit.breachedCount)
        assertEquals(0, offlineAudit.offlineUnverifiedCount)
        assertEquals(942, offlineAudit.resultsByRecordId["off-1"])
        assertEquals(0, offlineAudit.resultsByRecordId["off-2"])

        com.example.security.OfflineHibpStore.clearCache(context)
    }

    @Test
    fun `test SecurityHealthAnalyzer detects weak entries duplicate clusters breaches and scores proportionally`() {
        val now = 1_800_000_000_000L
        // Empty vault defaults cleanly to 100%
        val emptyReport = com.example.security.SecurityHealthAnalyzer.analyze(emptyList(), now)
        assertEquals(100, emptyReport.overallScore)
        assertEquals(0, emptyReport.totalLogins)

        // All strong unique logins -> 100%
        val strongUnique = listOf(
            VaultItem(id = "s1", title = "Bank", category = VaultCategory.LOGIN, password = "V!p9#K2mQ8@Lp4zR", passwordUpdatedAt = now),
            VaultItem(id = "s2", title = "Broker", category = VaultCategory.LOGIN, password = "X7\$nW2@vP9!qM4tB", passwordUpdatedAt = now)
        )
        val optimalReport = com.example.security.SecurityHealthAnalyzer.analyze(strongUnique, now)
        assertEquals(100, optimalReport.overallScore)
        assertEquals(2, optimalReport.strongCount)
        assertEquals(0, optimalReport.weakCount)
        assertEquals(0, optimalReport.reusedCount)

        // Mixed vault with duplicates, weak password, breached & expired entry, plus non-login note
        val mixedVault = listOf(
            VaultItem(id = "m1", title = "Netflix", category = VaultCategory.LOGIN, password = "SharedPassword#2026", passwordUpdatedAt = now),
            VaultItem(id = "m2", title = "Spotify", category = VaultCategory.LOGIN, password = "SharedPassword#2026", passwordUpdatedAt = now),
            VaultItem(id = "m3", title = "OldForum", category = VaultCategory.LOGIN, password = "12345", passwordUpdatedAt = now - 100L * 86_400_000L, pwnedBreachCount = 45),
            VaultItem(id = "m4", title = "VaultCard", category = VaultCategory.CARD, cardNumber = "4111")
        )
        val mixedReport = com.example.security.SecurityHealthAnalyzer.analyze(mixedVault, now)
        assertEquals(3, mixedReport.totalLogins)
        assertEquals(1, mixedReport.weakCount)
        assertEquals(2, mixedReport.reusedCount)
        assertEquals(1, mixedReport.duplicateGroupCount)
        assertEquals(1, mixedReport.duplicateClusters.size)
        assertEquals(listOf("Netflix", "Spotify"), mixedReport.duplicateClusters.single().items.map { it.title })
        assertEquals(1, mixedReport.breachedCount)
        assertEquals(1, mixedReport.expiredCount)
        assertTrue(mixedReport.overallScore in 1..80)
    }

    @Test
    fun `test UnlockBackoffManager enforces progressive brute-force cooldowns and persists state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_backoff_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        var fakeNow = 1_000_000L
        val clock: () -> Long = { fakeNow }
        val manager = com.example.security.UnlockBackoffManager(prefs, clock)

        // First 4 failures warn without lockout
        for (attempt in 1..4) {
            val state = manager.recordFailure()
            assertEquals(attempt, state.failedAttempts)
            assertFalse(state.isLockedOut)
            assertEquals(0, state.lockoutRemainingSeconds)
        }

        // 5th failure triggers 30s lockout (matching voice lockout)
        val fifth = manager.recordFailure()
        assertEquals(5, fifth.failedAttempts)
        assertTrue(fifth.isLockedOut)
        assertEquals(30, fifth.lockoutRemainingSeconds)

        // While locked out, additional attempt does not advance until cooldown expires, and state persists across instances
        val reloaded = com.example.security.UnlockBackoffManager(prefs, clock)
        assertEquals(30, reloaded.getState().lockoutRemainingSeconds)

        // Advance past 30s -> 6th failure escalates to 60s
        fakeNow += 31_000L
        assertFalse(reloaded.getState().isLockedOut)
        val sixth = reloaded.recordFailure()
        assertEquals(6, sixth.failedAttempts)
        assertEquals(60, sixth.lockoutRemainingSeconds)

        // Advance past 60s -> 7th failure -> 120s; 8th failure -> 300s
        fakeNow += 61_000L
        val seventh = reloaded.recordFailure()
        assertEquals(120, seventh.lockoutRemainingSeconds)
        fakeNow += 121_000L
        val eighth = reloaded.recordFailure()
        assertEquals(300, eighth.lockoutRemainingSeconds)

        // Reset clears counters and lockout immediately
        reloaded.reset()
        val cleared = reloaded.getState()
        assertEquals(0, cleared.failedAttempts)
        assertFalse(cleared.isLockedOut)
    }

    @Test
    fun `test AutofillMatcher and AutofillVaultSession domain matching and RAM purge`() {
        val github = VaultItem(
            id = "af-1",
            title = "GitHub Enterprise",
            category = VaultCategory.LOGIN,
            username = "vip@company.com",
            password = "Secret!Password123",
            url = "https://github.com/login",
            notes = "Private token note"
        )
        val bank = VaultItem(
            id = "af-2",
            title = "Swiss Private Bank",
            category = VaultCategory.LOGIN,
            username = "client_01",
            password = "UltraStrong#BankPass99",
            url = "https://portal.swissbank.ch"
        )
        val note = VaultItem(
            id = "af-3",
            title = "Seed Phrase",
            category = VaultCategory.NOTE,
            notes = "word1 word2 word3"
        )

        com.example.autofill.AutofillVaultSession.updateUnlockedItems(listOf(github, bank, note), isDuress = false)
        assertTrue(com.example.autofill.AutofillVaultSession.isUnlocked())
        // Non-login notes must never enter autofill cache, and notes on logins are stripped
        val cached = com.example.autofill.AutofillVaultSession.getUnlockedLogins()
        assertEquals(2, cached.size)
        assertEquals("", cached.first { it.id == "af-1" }.notes)

        val domainMatches = com.example.autofill.AutofillMatcher.matchLogins(
            items = cached,
            webDomain = "gist.github.com",
            packageName = "com.android.chrome"
        )
        assertEquals(1, domainMatches.size)
        assertEquals("af-1", domainMatches.first().item.id)
        assertTrue(domainMatches.first().isDirectMatch)

        val pkgMatches = com.example.autofill.AutofillMatcher.matchLogins(
            items = cached,
            webDomain = null,
            packageName = "ch.swissbank.mobile"
        )
        assertEquals("af-2", pkgMatches.first().item.id)
        assertTrue(pkgMatches.first().isDirectMatch)

        // Locking clears all cached credentials from RAM
        com.example.autofill.AutofillVaultSession.clear()
        assertFalse(com.example.autofill.AutofillVaultSession.isUnlocked())
        assertTrue(com.example.autofill.AutofillVaultSession.getUnlockedLogins().isEmpty())
    }

    @Test
    fun `test branded resources and privacy policy strings exist`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("The Black-Box", context.getString(R.string.app_name))
        assertTrue(context.getString(R.string.privacy_policy_url).startsWith("https://"))
        assertTrue(context.getString(R.string.privacy_voice_body).contains("RAM"))
        assertTrue(context.getString(R.string.privacy_hibp_body).contains("5 characters"))
        assertEquals("The Black-Box Autofill", context.getString(R.string.autofill_service_label))
    }

    @Test
    fun `test UnlockBackoffManager resists wall-clock forward bypass and backward rollback trap`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_clock_tamper_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        var wallNow = 10_000_000L
        var elapsedNow = 500_000L
        val manager = com.example.security.UnlockBackoffManager(
            prefs = prefs,
            clock = { wallNow },
            elapsedClock = { elapsedNow }
        )

        repeat(5) { manager.recordFailure() }
        assertEquals(30, manager.getState().lockoutRemainingSeconds)

        // Attack 1: Roll wall clock forward by 1 hour without advancing monotonic clock -> lockout remains enforced
        wallNow += 3_600_000L
        val afterForwardTamper = manager.getState()
        assertTrue(afterForwardTamper.isLockedOut)
        assertEquals(30, afterForwardTamper.lockoutRemainingSeconds)

        // Attack 2: Roll wall clock backward by 1 year -> remaining seconds must stay clamped to <= 30s
        wallNow = 1_000L
        val afterBackwardTamper = manager.getState()
        assertTrue(afterBackwardTamper.isLockedOut)
        assertEquals(30, afterBackwardTamper.lockoutRemainingSeconds)

        // Advance monotonic clock past 30s -> lockout expires deterministically
        elapsedNow += 31_000L
        assertFalse(manager.getState().isLockedOut)
    }

    @Test
    fun `test AutofillVaultSession enforces defeasible idle TTL and strips TOTP secrets`() {
        val prevClock = com.example.autofill.AutofillVaultSession.clockProvider
        try {
            var fakeElapsed = 1_000_000L
            com.example.autofill.AutofillVaultSession.clockProvider = { fakeElapsed }
            com.example.autofill.AutofillVaultSession.clear()

            val login = VaultItem(
                id = "ttl-1",
                title = "Corporate SSO",
                category = VaultCategory.LOGIN,
                username = "exec@corp.com",
                password = "CorpPassword!99",
                totpSecret = "JBSWY3DPEHPK3PXP",
                notes = "Sensitive notes"
            )
            com.example.autofill.AutofillVaultSession.updateUnlockedItems(
                items = listOf(login),
                isDuress = false,
                autoLockMinutes = 2
            )
            assertTrue(com.example.autofill.AutofillVaultSession.isUnlocked())
            val cached = com.example.autofill.AutofillVaultSession.getUnlockedLogins().single()
            assertEquals("", cached.totpSecret)
            assertEquals("", cached.notes)

            // Touching before expiry extends the session
            fakeElapsed += 90_000L
            assertTrue(com.example.autofill.AutofillVaultSession.isUnlocked())
            com.example.autofill.AutofillVaultSession.touchSession()

            // Exceeding 2-minute TTL auto-defeats trust and clears RAM
            fakeElapsed += 121_000L
            assertFalse(com.example.autofill.AutofillVaultSession.isUnlocked())
            assertTrue(com.example.autofill.AutofillVaultSession.getUnlockedLogins().isEmpty())
        } finally {
            com.example.autofill.AutofillVaultSession.clear()
            com.example.autofill.AutofillVaultSession.clockProvider = prevClock
        }
    }

    @Test
    fun `test BlackBoxSecurity fail-closed GCM bounds and AttachmentStorageManager path confinement`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = BlackBoxSecurity.deriveMasterKey("BoundsTest!2026".toCharArray(), BlackBoxSecurity.generateRandomSalt())

        // Short IV or ciphertext must fail closed before Cipher.doFinal
        var caughtInvalidIv = false
        try {
            BlackBoxSecurity.decryptFileBytes(ByteArray(32), ByteArray(4), key)
        } catch (_: IllegalArgumentException) {
            caughtInvalidIv = true
        }
        assertTrue(caughtInvalidIv)

        // Path traversal must be rejected
        val traversalRead = com.example.security.AttachmentStorageManager.readDecryptedFile(
            context = context,
            relPath = "../shared_prefs/blackbox_auth_guard.xml",
            secretKey = key
        )
        assertEquals(null, traversalRead)

        // Room DB stays at version 6 cleanly
        val db = BlackBoxDatabase.getDatabase(context)
        assertEquals(6, db.openHelper.readableDatabase.version)
    }

    @Test
    fun `test VaultViewModel monitorAppLifecycle locks vault on background and requires biometric reauth on resume`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = BlackBoxDatabase.getDatabase(app)
        db.vaultDao().deleteAllRecords()
        db.metadataDao().deleteMetadata()

        // Seed initialized vault with biometrics enabled in metadata (Robolectric JVM has no AndroidKeyStore)
        val repo = com.example.data.VaultRepository(db.vaultDao(), db.passwordDao(), db.secureNoteDao())
        repo.initializeVault("LifecycleBio#2026", enableBiometrics = false)
        val seededMeta = db.metadataDao().getMetadata()!!.copy(isBiometricEnabled = true)
        db.metadataDao().insertMetadata(seededMeta)

        val viewModel = com.example.ui.VaultViewModel(app)
        val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())

        repeat(40) {
            mainLooper.idle()
            if (viewModel.uiState.value.isInitialized && !viewModel.uiState.value.isLoading) return@repeat
            kotlinx.coroutines.delay(25)
        }
        assertTrue(viewModel.uiState.value.isInitialized)
        assertTrue(viewModel.uiState.value.isBiometricEnabled)

        viewModel.unlockWithPassword("LifecycleBio#2026")
        repeat(40) {
            mainLooper.idle()
            if (viewModel.uiState.value.isUnlocked) return@repeat
            kotlinx.coroutines.delay(25)
        }
        assertTrue("Vault should be unlocked before background transition", viewModel.uiState.value.isUnlocked)
        assertFalse(viewModel.uiState.value.requiresBiometricReauth)

        var reauthPromptCount = 0
        var cancelPromptCount = 0
        val owner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry(this)
            override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        }

        viewModel.monitorAppLifecycle(
            lifecycle = owner.lifecycle,
            biometricManager = null,
            onRequireBiometricReauth = { reauthPromptCount++ },
            onCancelBiometricPrompt = { cancelPromptCount++ }
        )

        // Move through foreground -> pause -> stop (background)
        owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
        owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
        owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
        assertEquals(0, reauthPromptCount) // Already unlocked, no reauth needed

        owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
        assertTrue(viewModel.uiState.value.isSensitiveContentHidden)
        assertEquals(1, cancelPromptCount)

        owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)
        assertFalse(viewModel.uiState.value.isUnlocked)
        assertTrue(viewModel.uiState.value.isAppInBackground)
        assertTrue(viewModel.uiState.value.requiresBiometricReauth)
        assertTrue(viewModel.uiState.value.decryptedItems.isEmpty())
        assertFalse(com.example.autofill.AutofillVaultSession.isUnlocked())

        // Return to foreground -> requires biometric re-authentication
        owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
        owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
        assertFalse(viewModel.uiState.value.isAppInBackground)
        assertEquals(1, reauthPromptCount)
        assertTrue(viewModel.uiState.value.requiresBiometricReauth)

        // Complete unlock -> clears biometric reauth flag
        viewModel.unlockWithPassword("LifecycleBio#2026")
        repeat(40) {
            mainLooper.idle()
            if (viewModel.uiState.value.isUnlocked) return@repeat
            kotlinx.coroutines.delay(25)
        }
        assertTrue(viewModel.uiState.value.isUnlocked)
        assertFalse(viewModel.uiState.value.requiresBiometricReauth)

        owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_DESTROY)
    }

    @Test
    fun `test AesGcmCryptoEngine binds record metadata with AAD and blocks tampering or downgrade`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = BlackBoxDatabase.getDatabase(context)
        val repo = com.example.data.VaultRepository(db.vaultDao(), db.passwordDao(), db.secureNoteDao())
        repo.wipeVault()

        val key = repo.initializeVault("AesGcmAtRest!2026", enableBiometrics = false)
        val item = VaultItem(
            id = "aead-rec-1",
            title = "Private Swiss Custodian",
            category = VaultCategory.LOGIN,
            username = "custodian_vip",
            password = "EntropyPassword#2026!",
            url = "https://custodian.example.ch",
            partitionId = VaultPartition.PRIMARY
        )

        repo.saveItem(item, key, VaultPartition.PRIMARY)
        val stored = db.vaultDao().getRecordById("aead-rec-1", VaultPartition.PRIMARY)!!
        assertTrue(stored.encryptedPayload.startsWith(com.example.security.AesGcmCryptoEngine.ENVELOPE_V2_PREFIX))

        // 1. Valid decrypt succeeds
        val decrypted = BlackBoxSecurity.decryptRecordPayload(
            id = stored.id,
            title = stored.title,
            category = VaultCategory.LOGIN,
            partitionId = stored.partitionId,
            encryptedPayload = stored.encryptedPayload,
            initializationVector = stored.initializationVector,
            isFavorite = stored.isFavorite,
            createdAt = stored.createdAt,
            updatedAt = stored.updatedAt,
            secretKey = key
        )
        assertEquals("EntropyPassword#2026!", decrypted.password)

        // 2. Partition / ID / Category tampering MUST fail AEAD verification
        var partitionTamperRejected = false
        try {
            BlackBoxSecurity.decryptRecordPayload(
                id = stored.id,
                title = stored.title,
                category = VaultCategory.LOGIN,
                partitionId = VaultPartition.DURESS,
                encryptedPayload = stored.encryptedPayload,
                initializationVector = stored.initializationVector,
                isFavorite = stored.isFavorite,
                createdAt = stored.createdAt,
                updatedAt = stored.updatedAt,
                secretKey = key
            )
        } catch (_: javax.crypto.AEADBadTagException) {
            partitionTamperRejected = true
        }
        assertTrue(partitionTamperRejected)

        // 3. Stripping v2: prefix (downgrade attempt) MUST also fail AEAD tag check
        var downgradeRejected = false
        try {
            BlackBoxSecurity.decryptRecordPayload(
                id = stored.id,
                title = stored.title,
                category = VaultCategory.LOGIN,
                partitionId = stored.partitionId,
                encryptedPayload = stored.encryptedPayload.removePrefix("v2:"),
                initializationVector = stored.initializationVector,
                isFavorite = stored.isFavorite,
                createdAt = stored.createdAt,
                updatedAt = stored.updatedAt,
                secretKey = key
            )
        } catch (_: javax.crypto.AEADBadTagException) {
            downgradeRejected = true
        }
        assertTrue(downgradeRejected)

        // 4. Legacy v1 unprefixed record still decrypts cleanly for backward compatibility
        val legacyJson = BlackBoxSecurity.serializeItemPayload(item)
        val (legacyCt, legacyIv) = BlackBoxSecurity.encrypt(legacyJson, key)
        assertFalse(legacyCt.startsWith("v2:"))
        val legacyRestored = BlackBoxSecurity.decryptRecordPayload(
            id = item.id,
            title = item.title,
            category = item.category,
            partitionId = VaultPartition.PRIMARY,
            encryptedPayload = legacyCt,
            initializationVector = legacyIv,
            isFavorite = false,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt,
            secretKey = key
        )
        assertEquals("EntropyPassword#2026!", legacyRestored.password)
    }

    @Test
    fun `test biometric CryptoObject fail-closed and enrollment invalidation cleanup`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = BlackBoxDatabase.getDatabase(context)
        val repo = com.example.data.VaultRepository(db.vaultDao(), db.passwordDao(), db.secureNoteDao())
        repo.wipeVault()

        repo.initializeVault("StrongBoxTest#2026", enableBiometrics = false)

        // Seed a legacy unauthenticated blob in metadata to simulate pre-CryptoObject or invalidated state
        val meta = db.metadataDao().getMetadata()!!
        db.metadataDao().insertMetadata(
            meta.copy(
                isBiometricEnabled = true,
                biometricWrappedPassword = "legacy_unauthenticated_blob"
            )
        )

        // 1. Null cipher MUST fail closed
        val nullCipherUnlock = repo.unlockWithBiometrics(authenticatedCipher = null)
        assertEquals(null, nullCipherUnlock)

        var securityThrown = false
        try {
            BlackBoxSecurity.unwrapPasswordFromBiometrics("bio_v2:AAAA", authenticatedCipher = null)
        } catch (_: SecurityException) {
            securityThrown = true
        }
        assertTrue(securityThrown)

        // 2. prepareBiometricUnlockCipher detects legacy/invalidated key and purges enrollment metadata
        val initResult = repo.prepareBiometricUnlockCipher()
        assertTrue(initResult is com.example.security.BiometricCipherInitResult.Invalidated)
        val updatedMeta = db.metadataDao().getMetadata()!!
        assertFalse(updatedMeta.isBiometricEnabled)
        assertEquals(null, updatedMeta.biometricWrappedPassword)

        // 3. Explicit RSA-OAEP Cipher roundtrip matches BlackBoxSecurity unwrapping contract
        val kpg = java.security.KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val oaepSpec = javax.crypto.spec.OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            java.security.spec.MGF1ParameterSpec.SHA1,
            javax.crypto.spec.PSource.PSpecified.DEFAULT
        )
        val encCipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        encCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, kp.public, oaepSpec)
        val sealed = "bio_v2:" + android.util.Base64.encodeToString(
            encCipher.doFinal("StrongBoxTest#2026".toByteArray(Charsets.UTF_8)),
            android.util.Base64.NO_WRAP
        )
        val decCipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        decCipher.init(javax.crypto.Cipher.DECRYPT_MODE, kp.private, oaepSpec)
        val unwrapped = BlackBoxSecurity.unwrapPasswordFromBiometrics(sealed, decCipher)
        assertEquals("StrongBoxTest#2026", unwrapped)
    }

    @Test
    fun `test ExportFileCleaner purges exports, shares, and snapshots while preserving active share and attachments`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exportDir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        val shareDir = java.io.File(context.cacheDir, "shares").apply { mkdirs() }
        val snapshotDir = java.io.File(context.filesDir, "snapshots").apply { mkdirs() }
        val attachmentDir = java.io.File(context.filesDir, "vault_attachments").apply { mkdirs() }

        val activeHtml = java.io.File(exportDir, "blackbox_vault_active.html").apply {
            writeText("<html>secret</html>")
        }
        val staleHtml = java.io.File(exportDir, "blackbox_vault_stale.html").apply {
            writeText("<html>stale</html>")
        }
        val staleShare = java.io.File(shareDir, "blackbox_secure_share_old.html").apply {
            writeText("<html>share</html>")
        }
        val staleSnapshot = java.io.File(snapshotDir, "blackbox_snapshot_old.blackbox").apply {
            writeText("{}")
        }
        val encryptedAttachment = java.io.File(attachmentDir, "keep-attachment.enc").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        // While share chooser is active, activeHtml is preserved and stale files are wiped
        com.example.security.ExportFileCleaner.markShareStarted(activeHtml)
        val sweptDuringShare = com.example.security.ExportFileCleaner.cleanupIdleExports(context)
        assertTrue(sweptDuringShare >= 3)
        assertTrue(activeHtml.exists())
        assertFalse(staleHtml.exists())
        assertFalse(staleShare.exists())
        assertFalse(staleSnapshot.exists())
        assertTrue("Vault attachments must never be deleted by export cleaner", encryptedAttachment.exists())

        // Once share completes, unconditional cleanup wipes remaining export file
        com.example.security.ExportFileCleaner.markShareFinished()
        com.example.security.ExportFileCleaner.cleanupAllExportsUnconditionally(context)
        assertFalse(activeHtml.exists())
        assertTrue(encryptedAttachment.exists())
        encryptedAttachment.delete()
    }

    @Test
    fun `test IncognitoKeyboard enforces IME_FLAG_NO_PERSONALIZED_LEARNING`() {
        val editorInfo = android.view.inputmethod.EditorInfo().apply {
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        com.example.ui.components.applyNoPersonalizedLearningFlag(editorInfo)

        assertTrue(
            (editorInfo.imeOptions and android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        )
        assertTrue(
            (editorInfo.imeOptions and android.view.inputmethod.EditorInfo.IME_ACTION_DONE) != 0
        )
    }

    @Test
    fun `test encrypted vault storage and Google Authenticator TOTP validation roundtrip`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = BlackBoxDatabase.getDatabase(context)
        val repo = com.example.data.VaultRepository(db.vaultDao(), db.passwordDao(), db.secureNoteDao())
        repo.wipeVault()

        val key = repo.initializeVault("VaultTotpMaster#2026", enableBiometrics = false)
        val otpAuthUri = "otpauth://totp/AWS:admin@company.io?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=AWS&algorithm=SHA256&digits=8&period=60"
        val validated = com.example.security.TotpAuthenticatorService.validateSeedInput(otpAuthUri)
        assertTrue(validated is com.example.security.TotpSeedValidationResult.Valid)
        val storable = (validated as com.example.security.TotpSeedValidationResult.Valid).storableValue

        val item = VaultItem(
            id = "aws-totp-1",
            title = "AWS Root",
            category = VaultCategory.LOGIN,
            username = "admin@company.io",
            password = "StrongAwsPassword#2026",
            url = "https://console.aws.amazon.com",
            totpSecret = storable
        )
        repo.saveItem(item, key, VaultPartition.PRIMARY)

        val decryptedItems = repo.getAllDecryptedItems(key, VaultPartition.PRIMARY)
        val restored = decryptedItems.firstOrNull { it.id == "aws-totp-1" }
        assertNotNull(restored)
        assertEquals(storable, restored?.totpSecret)

        val token = com.example.security.TotpAuthenticatorService.generateToken(restored!!.totpSecret, timeMillis = 1_700_000_060_000L)
        assertNotNull(token)
        assertEquals(8, token?.code?.length)
        assertEquals(60, token?.periodSeconds)
        assertEquals(com.example.security.TotpAlgorithm.SHA256, token?.config?.algorithm)

        val verification = com.example.security.TotpAuthenticatorService.verifyCode(
            storedSeedOrUri = restored.totpSecret,
            candidateCode = token!!.code,
            timeMillis = 1_700_000_060_000L
        )
        assertTrue(verification.isValid)
        assertEquals(0, verification.matchedStepOffset)

        // Autofill RAM cache must still strip totpSecret
        com.example.autofill.AutofillVaultSession.updateUnlockedItems(decryptedItems, isDuress = false)
        val cached = com.example.autofill.AutofillVaultSession.getUnlockedLogins().firstOrNull { it.id == "aws-totp-1" }
        assertNotNull(cached)
        assertEquals("", cached?.totpSecret)
        com.example.autofill.AutofillVaultSession.clear()
    }

    @Test
    fun `test ConfidentialNotesMemoryCache duress isolation and ConfidentialNotesWidgetProvider states`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = BlackBoxDatabase.getDatabase(context)
        val repo = com.example.data.VaultRepository(db.vaultDao(), db.passwordDao(), db.secureNoteDao())
        repo.wipeVault()

        val key = repo.initializeVault("WidgetMaster#2026", enableBiometrics = false)
        val secretNote = VaultItem(
            id = "widget-note-1",
            title = "Cold Wallet Seed Backup",
            category = VaultCategory.NOTE,
            notes = "word1 word2 word3 ultra secret phrase",
            isFavorite = true
        )
        val regularLogin = VaultItem(
            id = "widget-login-1",
            title = "GitHub",
            category = VaultCategory.LOGIN,
            username = "octo",
            password = "pw"
        )
        repo.saveItem(secretNote, key, VaultPartition.PRIMARY)
        repo.saveItem(regularLogin, key, VaultPartition.PRIMARY)

        // Room counts only PRIMARY NOTE rows when vault is locked
        val sealedCount = com.example.widget.ConfidentialNotesWidgetProvider.querySealedNoteCount(context)
        assertEquals(1, sealedCount)

        // Locked RemoteViews shows sealed count and unlock action
        com.example.widget.ConfidentialNotesMemoryCache.clear()
        val lockedViews = com.example.widget.ConfidentialNotesWidgetProvider.buildRemoteViews(context, sealedCount)
        assertNotNull(lockedViews)
        assertFalse(com.example.widget.ConfidentialNotesMemoryCache.isUnlocked())

        // Duress partition MUST never populate widget cache
        com.example.widget.ConfidentialNotesMemoryCache.updateFromVaultItems(listOf(secretNote), isDuress = true)
        assertFalse(com.example.widget.ConfidentialNotesMemoryCache.isUnlocked())
        assertEquals(null, com.example.widget.ConfidentialNotesMemoryCache.getUnlockedNotes())

        // Primary unlock populates ONLY NOTE category items in volatile RAM cache
        com.example.widget.ConfidentialNotesMemoryCache.updateFromVaultItems(listOf(secretNote, regularLogin), isDuress = false)
        assertTrue(com.example.widget.ConfidentialNotesMemoryCache.isUnlocked())
        val cachedNotes = com.example.widget.ConfidentialNotesMemoryCache.getUnlockedNotes()!!
        assertEquals(1, cachedNotes.size)
        assertEquals("Cold Wallet Seed Backup", cachedNotes[0].title)
        assertFalse(com.example.widget.ConfidentialNotesMemoryCache.isPeekRevealed())
        assertTrue(com.example.widget.ConfidentialNotesMemoryCache.togglePeek())

        val unlockedViews = com.example.widget.ConfidentialNotesWidgetProvider.buildRemoteViews(context, sealedCount)
        assertNotNull(unlockedViews)

        com.example.widget.ConfidentialNotesMemoryCache.clear()
        assertFalse(com.example.widget.ConfidentialNotesMemoryCache.isUnlocked())
    }

    @Test
    @Suppress("DEPRECATION")
    fun `test BlackBoxBackgroundScheduler silent maintenance sweep and BlackBoxApplication memory trim`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hibpDir = java.io.File(context.filesDir, "hibp_cache").apply { mkdirs() }
        val stalePrefix = java.io.File(hibpDir, "AA001.range").apply {
            writeText("0000:1")
            setLastModified(System.currentTimeMillis() - 40L * 24L * 60L * 60L * 1000L)
        }
        val freshPrefix = java.io.File(hibpDir, "BB002.range").apply {
            writeText("0000:1")
            setLastModified(System.currentTimeMillis())
        }
        val exportDir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        val tempExport = java.io.File(exportDir, "orphan_export.html").apply {
            writeText("<html>temp</html>")
        }

        // Seed volatile caches; AutofillVaultSession locked/empty triggers widget cache purge during sweep
        com.example.autofill.AutofillVaultSession.clear()
        com.example.widget.ConfidentialNotesMemoryCache.updateFromVaultItems(
            listOf(VaultItem(id = "n1", title = "Secret", category = VaultCategory.NOTE, notes = "Body")),
            isDuress = false
        )

        val report = com.example.security.BlackBoxBackgroundScheduler.executeMaintenancePass(context)
        assertTrue(report.purgedExportsCount >= 1)
        assertEquals(1, report.prunedHibpFilesCount)
        assertTrue(report.sessionExpired)
        assertFalse(tempExport.exists())
        assertFalse(stalePrefix.exists())
        assertTrue(freshPrefix.exists())
        assertFalse(com.example.widget.ConfidentialNotesMemoryCache.isUnlocked())

        val status = com.example.security.BlackBoxBackgroundScheduler.getStatus(context)
        assertNotNull(status.lastRunAtMillis)

        // Verify OS memory trim purges active unlocked RAM caches
        com.example.autofill.AutofillVaultSession.updateUnlockedItems(
            listOf(VaultItem(id = "l1", title = "Bank", category = VaultCategory.LOGIN, username = "u", password = "p")),
            isDuress = false
        )
        com.example.widget.ConfidentialNotesMemoryCache.updateFromVaultItems(
            listOf(VaultItem(id = "n2", title = "Tax PIN", category = VaultCategory.NOTE, notes = "1234")),
            isDuress = false
        )
        val app = BlackBoxApplication()
        val attachField = android.app.Application::class.java.getDeclaredMethod("attach", Context::class.java)
        attachField.isAccessible = true
        attachField.invoke(app, context)
        app.handleMemoryPressure(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)

        assertFalse(com.example.widget.ConfidentialNotesMemoryCache.isUnlocked())
        assertFalse(com.example.autofill.AutofillVaultSession.isUnlocked())

        freshPrefix.delete()
    }

    @Test
    fun `test TaskAnalyticsEngine tracks started, completed, stopped tasks, OS job stop reasons, and ring buffer cap`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.security.TaskAnalyticsEngine.clearAnalytics(context)

        // 1. Track task start -> active
        com.example.security.TaskAnalyticsEngine.recordTaskStarted(
            context = context,
            taskType = com.example.security.TrackedTaskType.BREACH_AUDIT_SCAN,
            taskName = "HIBP Breach Audit Scan"
        )
        val startedSummary = com.example.security.TaskAnalyticsEngine.getSummary(context)
        assertEquals(1, startedSummary.activeTasksCount)
        assertTrue(com.example.security.TrackedTaskType.BREACH_AUDIT_SCAN in startedSummary.activeTaskTypes)
        assertEquals(0, startedSummary.stoppedTasksCount)

        // 2. Track task stopped -> no longer active, stopped count incremented, stop reason captured
        val (preemptCode, preemptLabel) = com.example.security.TaskAnalyticsEngine.mapJobStopReason(
            android.app.job.JobParameters.STOP_REASON_PREEMPT
        )
        assertEquals(android.app.job.JobParameters.STOP_REASON_PREEMPT, preemptCode)
        assertTrue(preemptLabel.contains("Preempted", ignoreCase = true))

        com.example.security.TaskAnalyticsEngine.recordTaskStopped(
            context = context,
            taskType = com.example.security.TrackedTaskType.BREACH_AUDIT_SCAN,
            taskName = "HIBP Breach Audit Scan",
            stopReasonCode = preemptCode,
            stopReasonLabel = "Breach scan stopped when vault locked",
            state = com.example.security.TaskLifecycleState.STOPPED,
            durationMs = 142L
        )
        val stoppedSummary = com.example.security.TaskAnalyticsEngine.getSummary(context)
        assertEquals(0, stoppedSummary.activeTasksCount)
        assertEquals(1, stoppedSummary.stoppedTasksCount)
        assertTrue(com.example.security.TrackedTaskType.BREACH_AUDIT_SCAN in stoppedSummary.stoppedTaskTypes)
        assertEquals("Breach scan stopped when vault locked", stoppedSummary.lastStoppedEvent?.stopReasonLabel)
        assertEquals(142L, stoppedSummary.lastStoppedEvent?.durationMs)

        // 3. Verify ring buffer caps recentEvents at 40 entries while counters keep accurate totals
        for (i in 1..50) {
            com.example.security.TaskAnalyticsEngine.recordTaskCompleted(
                context = context,
                taskType = com.example.security.TrackedTaskType.WIDGET_REFRESH,
                taskName = "Notes Widget Sync #$i",
                durationMs = 5L
            )
        }
        val cappedSummary = com.example.security.TaskAnalyticsEngine.getSummary(context)
        assertEquals(40, cappedSummary.recentEvents.size)
        assertEquals(50, cappedSummary.completedTasksCount)
        assertEquals(1, cappedSummary.stoppedTasksCount)

        // 4. Verify zero-knowledge invariant (no secret keys or passwords stored)
        val rawJson = context.getSharedPreferences("blackbox_task_analytics", Context.MODE_PRIVATE)
            .getString("task_events_json", "") ?: ""
        assertFalse(rawJson.contains("password", ignoreCase = true))

        val cleared = com.example.security.TaskAnalyticsEngine.clearAnalytics(context)
        assertEquals(0, cleared.totalTrackedRuns)
        assertEquals(0, cleared.stoppedTasksCount)
        assertTrue(cleared.recentEvents.isEmpty())
    }

    @Test
    fun `test TaskAnalyticsEngine integration with BackgroundScheduler, InactivityLockManager, and MemoryPressure`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.security.TaskAnalyticsEngine.clearAnalytics(context)

        // 1. Background maintenance sweep + cancelScheduled
        com.example.security.BlackBoxBackgroundScheduler.executeMaintenancePass(context)
        com.example.security.BlackBoxBackgroundScheduler.cancelScheduled(context)

        val afterBgCancel = com.example.security.TaskAnalyticsEngine.getSummary(context)
        assertEquals(1, afterBgCancel.completedTasksCount)
        assertEquals(1, afterBgCancel.stoppedTasksCount)
        assertEquals(
            com.example.security.TaskLifecycleState.CANCELLED,
            afterBgCancel.taskStatusByType[com.example.security.TrackedTaskType.BACKGROUND_MAINTENANCE_JOB]
        )

        // 2. InactivityLockManager start -> stopMonitoring records stopped timer task
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val lockManager = com.example.security.InactivityLockManager(scope, context) {}
        lockManager.startMonitoring()
        lockManager.stopMonitoring("Test vault locked")

        val afterIdleStop = com.example.security.TaskAnalyticsEngine.getSummary(context)
        assertEquals(2, afterIdleStop.stoppedTasksCount)
        assertEquals(
            com.example.security.TaskLifecycleState.STOPPED,
            afterIdleStop.taskStatusByType[com.example.security.TrackedTaskType.INACTIVITY_TIMER]
        )
        assertEquals("Test vault locked", afterIdleStop.lastStoppedEvent?.stopReasonLabel)

        // 3. OS Memory Trim stopping active RAM caches records APP_PROCESS_TASK stop
        com.example.widget.ConfidentialNotesMemoryCache.updateFromVaultItems(
            listOf(VaultItem(id = "n-mem", title = "Secret", category = VaultCategory.NOTE, notes = "Body")),
            isDuress = false
        )
        val app = BlackBoxApplication()
        val attachField = android.app.Application::class.java.getDeclaredMethod("attach", Context::class.java)
        attachField.isAccessible = true
        attachField.invoke(app, context)
        app.handleMemoryPressure(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)

        val afterTrim = com.example.security.TaskAnalyticsEngine.getSummary(context)
        assertEquals(3, afterTrim.stoppedTasksCount)
        assertEquals(
            com.example.security.TaskLifecycleState.STOPPED,
            afterTrim.taskStatusByType[com.example.security.TrackedTaskType.APP_PROCESS_TASK]
        )
        assertTrue(afterTrim.lastStoppedEvent?.stopReasonLabel?.contains("trimMemory") == true)
    }

    @Test
    fun `test Pay with NFC network detection, card payload serialization, duress rejection, and TTL lifecycle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.nfc.NfcPaymentSession.clear()
        com.example.security.TaskAnalyticsEngine.clearAnalytics(context)

        // 1. Network detection & last-4 formatting
        assertEquals(com.example.nfc.NfcCardNetwork.VISA, com.example.nfc.NfcPaymentSession.detectCardNetwork("4111 2222 3333 4444"))
        assertEquals(com.example.nfc.NfcCardNetwork.MASTERCARD, com.example.nfc.NfcPaymentSession.detectCardNetwork("5412 7512 3412 3456"))
        assertEquals(com.example.nfc.NfcCardNetwork.MASTERCARD, com.example.nfc.NfcPaymentSession.detectCardNetwork("2221 0000 0000 1234"))
        assertEquals(com.example.nfc.NfcCardNetwork.AMEX, com.example.nfc.NfcPaymentSession.detectCardNetwork("3782 822463 10005"))
        assertEquals(com.example.nfc.NfcCardNetwork.DISCOVER, com.example.nfc.NfcPaymentSession.detectCardNetwork("6011 1111 1111 1117"))
        assertEquals("•••• 4444", com.example.nfc.NfcPaymentSession.formatMaskedLast4("4111-2222-3333-4444"))

        // 2. Key strings explicitly communicate Pay with NFC and contactless advantage
        assertEquals("Pay with NFC", context.getString(R.string.nfc_pay_cta))
        assertTrue(context.getString(R.string.nfc_pay_advantage_summary).contains("pay contactlessly", ignoreCase = true))

        // 3. VaultItem isNfcPayEnabled encryption/decryption roundtrip
        val salt = BlackBoxSecurity.generateRandomSalt()
        val key = BlackBoxSecurity.deriveMasterKey("CardMasterKey99!".toCharArray(), salt)
        val nfcDisabledCard = VaultItem(
            id = "card-disabled",
            title = "Corporate Debit",
            category = VaultCategory.CARD,
            cardHolder = "Alex Mercer",
            cardNumber = "4111222233334444",
            cardExpiry = "12/29",
            cardCvv = "888",
            isNfcPayEnabled = false
        )
        val payloadJson = BlackBoxSecurity.serializeItemPayload(nfcDisabledCard)
        val (cipher, iv) = BlackBoxSecurity.encrypt(payloadJson, key)
        val decryptedJson = BlackBoxSecurity.decrypt(cipher, iv, key)
        val restored = BlackBoxSecurity.deserializeItemPayload(
            id = nfcDisabledCard.id,
            title = nfcDisabledCard.title,
            category = nfcDisabledCard.category,
            isFavorite = false,
            createdAt = 1L,
            updatedAt = 2L,
            jsonString = decryptedJson
        )
        assertFalse(restored.isNfcPayEnabled)

        // Backward-compatibility check: legacy card payload without isNfcPayEnabled defaults to true
        val legacyCardRestored = BlackBoxSecurity.deserializeItemPayload(
            id = "legacy-card",
            title = "Legacy Card",
            category = VaultCategory.CARD,
            isFavorite = false,
            createdAt = 1L,
            updatedAt = 2L,
            jsonString = """{"cardHolder":"Alex","cardNumber":"4111222233334444","cardExpiry":"12/29","cardCvv":"888"}"""
        )
        assertTrue(legacyCardRestored.isNfcPayEnabled)

        // 4. Fail-closed checks: duress mode & disabled card must NEVER arm NFC
        val nfcEnabledCard = nfcDisabledCard.copy(id = "card-visa", isNfcPayEnabled = true)
        assertNull(com.example.nfc.NfcPaymentSession.arm(context, nfcEnabledCard, isDuress = true))
        assertNull(com.example.nfc.NfcPaymentSession.arm(context, nfcDisabledCard, isDuress = false))

        // 5. Arm card and verify TTL expiry + TaskAnalyticsEngine tracking
        val t0 = 1_000_000L
        val session = com.example.nfc.NfcPaymentSession.arm(
            context = context,
            item = nfcEnabledCard,
            isDuress = false,
            ttlMillis = 60_000L,
            nowMillis = t0
        )
        assertNotNull(session)
        assertEquals(com.example.nfc.NfcCardNetwork.VISA, session!!.network)
        assertEquals("•••• 4444", session.maskedLast4)
        assertEquals(60, session.remainingSeconds(t0))
        assertNotNull(com.example.nfc.NfcPaymentSession.getActiveSession(t0 + 59_000L))
        assertNull(com.example.nfc.NfcPaymentSession.getActiveSession(t0 + 60_001L))

        val startedSummary = com.example.security.TaskAnalyticsEngine.getSummary(context)
        assertEquals(1, startedSummary.activeTasksCount)
        assertTrue(com.example.security.TrackedTaskType.NFC_PAYMENT_SESSION in startedSummary.activeTaskTypes)

        com.example.nfc.NfcPaymentSession.arm(context, nfcEnabledCard, isDuress = false, nowMillis = t0)
        com.example.nfc.NfcPaymentSession.disarm(context, reason = "User disarmed contactless card")
        assertNull(com.example.nfc.NfcPaymentSession.getActiveSession(t0))
        val stoppedSummary = com.example.security.TaskAnalyticsEngine.getSummary(context)
        assertEquals(1, stoppedSummary.stoppedTasksCount)
        assertEquals(
            com.example.security.TaskLifecycleState.STOPPED,
            stoppedSummary.taskStatusByType[com.example.security.TrackedTaskType.NFC_PAYMENT_SESSION]
        )
    }

    @Test
    fun `test BlackBoxHceService APDU protocol returns 6985 when disarmed and 9000 token when armed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.nfc.NfcPaymentSession.clear()
        com.example.security.TaskAnalyticsEngine.clearAnalytics(context)

        val hceService = Robolectric.setupService(com.example.nfc.BlackBoxHceService::class.java)
        val selectPpseApdu = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E,
            0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31, 0x00
        )

        // When disarmed / locked, HCE MUST fail closed with 69 85 (Conditions not satisfied)
        val lockedResp = hceService.processCommandApdu(selectPpseApdu, null)
        assertArrayEquals(com.example.nfc.BlackBoxHceService.STATUS_CONDITIONS_NOT_SATISFIED, lockedResp)

        // Arm a Mastercard record
        val mastercard = VaultItem(
            id = "card-mc-1",
            title = "Travel Mastercard",
            category = VaultCategory.CARD,
            cardHolder = "Alex Mercer",
            cardNumber = "5412751234129876",
            cardExpiry = "08/28",
            cardCvv = "123",
            isNfcPayEnabled = true
        )
        val armed = com.example.nfc.NfcPaymentSession.arm(context, mastercard, isDuress = false)!!

        // SELECT APDU succeeds with tokenized contactless payload + 90 00
        val okResp = hceService.processCommandApdu(selectPpseApdu, null)
        assertTrue(okResp.size > 2)
        assertEquals(0x90.toByte(), okResp[okResp.size - 2])
        assertEquals(0x00.toByte(), okResp[okResp.size - 1])
        val payload = String(okResp.copyOfRange(0, okResp.size - 2), Charsets.US_ASCII)
        assertEquals("BBX-MASTERCARD-${armed.tokenReference}", payload)
        assertEquals(1, com.example.nfc.NfcPaymentSession.getActiveSession()?.tapExchangeCount)

        // Unsupported INS returns 6D 00 while armed
        val unsupportedInsApdu = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00)
        assertArrayEquals(
            com.example.nfc.BlackBoxHceService.STATUS_INS_NOT_SUPPORTED,
            hceService.processCommandApdu(unsupportedInsApdu, null)
        )

        // Deactivating after tap disarms RAM session cleanly
        hceService.onDeactivated(android.nfc.cardemulation.HostApduService.DEACTIVATION_LINK_LOSS)
        assertNull(com.example.nfc.NfcPaymentSession.getActiveSession())
    }
}
