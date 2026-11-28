package com.example.data

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Base64
import com.example.model.VaultAttachment
import com.example.model.VaultCategory
import com.example.model.VaultItem
import com.example.security.BiometricCipherInitResult
import com.example.security.BlackBoxSecurity
import com.example.security.VoiceBiometricEngine
import com.example.security.VoiceVerificationResult
import com.example.security.VoiceprintTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.crypto.SecretKey

data class UnlockResult(
    val secretKey: SecretKey,
    val partitionId: Int, // 0 = Primary, 1 = Duress
    val isDuress: Boolean
)

sealed class VoiceUnlockOutcome {
    data class Success(
        val secretKey: SecretKey,
        val masterPassword: String,
        val verification: VoiceVerificationResult
    ) : VoiceUnlockOutcome()

    data class Rejected(val verification: VoiceVerificationResult) : VoiceUnlockOutcome()
    data class Unavailable(val reason: String) : VoiceUnlockOutcome()
}

interface VoiceDataProtector {
    fun protect(plaintext: String): String
    fun unprotect(wrapped: String): String
}

object KeystoreVoiceDataProtector : VoiceDataProtector {
    override fun protect(plaintext: String): String = BlackBoxSecurity.wrapVoiceData(plaintext)
    override fun unprotect(wrapped: String): String = BlackBoxSecurity.unwrapVoiceData(wrapped)
}

class VaultRepository(
    private val vaultDao: VaultDao,
    private val passwordDao: PasswordDao? = null,
    private val secureNoteDao: SecureNoteDao? = null,
    private val voiceDataProtector: VoiceDataProtector = KeystoreVoiceDataProtector
) {

    val metadataFlow: Flow<VaultMetadataEntity?> = vaultDao.getMetadataFlow()

    suspend fun getMetadata(): VaultMetadataEntity? = withContext(Dispatchers.IO) {
        vaultDao.getMetadata()
    }

    suspend fun isVaultInitialized(): Boolean = withContext(Dispatchers.IO) {
        val meta = vaultDao.getMetadata()
        meta != null && meta.isVaultInitialized
    }

    suspend fun initializeVault(masterPassword: String, enableBiometrics: Boolean): SecretKey =
        withContext(Dispatchers.IO) {
            val salt = BlackBoxSecurity.generateRandomSalt()
            val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            val verifierHash = BlackBoxSecurity.computeVerificationHash(masterPassword.toCharArray(), salt)

            val biometricWrapped = if (enableBiometrics) {
                try {
                    BlackBoxSecurity.wrapPasswordForBiometrics(masterPassword)
                } catch (e: Exception) {
                    null
                }
            } else null

            val metadata = VaultMetadataEntity(
                id = 1,
                masterSalt = saltBase64,
                verifierHash = verifierHash,
                isBiometricEnabled = enableBiometrics && biometricWrapped != null,
                biometricWrappedPassword = biometricWrapped,
                autoLockMinutes = 5,
                isVaultInitialized = true,
                hasDuressPin = false
            )
            vaultDao.insertMetadata(metadata)

            BlackBoxSecurity.deriveMasterKey(masterPassword.toCharArray(), salt)
        }

    suspend fun unlockWithPasswordOrDuress(input: String): UnlockResult? = withContext(Dispatchers.IO) {
        val meta = vaultDao.getMetadata() ?: return@withContext null

        // 1. Try primary master password
        val primarySalt = Base64.decode(meta.masterSalt, Base64.NO_WRAP)
        if (BlackBoxSecurity.verifyPassword(input.toCharArray(), primarySalt, meta.verifierHash)) {
            val key = BlackBoxSecurity.deriveMasterKey(input.toCharArray(), primarySalt)
            return@withContext UnlockResult(secretKey = key, partitionId = 0, isDuress = false)
        }

        // 2. Try Duress PIN if configured
        if (meta.hasDuressPin && !meta.duressSalt.isNullOrEmpty() && !meta.duressVerifierHash.isNullOrEmpty()) {
            val duressSalt = Base64.decode(meta.duressSalt, Base64.NO_WRAP)
            if (BlackBoxSecurity.verifyPassword(input.toCharArray(), duressSalt, meta.duressVerifierHash)) {
                val key = BlackBoxSecurity.deriveMasterKey(input.toCharArray(), duressSalt)
                return@withContext UnlockResult(secretKey = key, partitionId = 1, isDuress = true)
            }
        }

        null
    }

    suspend fun prepareBiometricUnlockCipher(): BiometricCipherInitResult = withContext(Dispatchers.IO) {
        val meta = vaultDao.getMetadata()
            ?: return@withContext BiometricCipherInitResult.Unavailable("Vault metadata unavailable.")
        if (!meta.isBiometricEnabled || meta.biometricWrappedPassword.isNullOrBlank()) {
            return@withContext BiometricCipherInitResult.Unavailable("Biometric unlock is not enabled.")
        }
        val result = BlackBoxSecurity.prepareBiometricDecryptCipher(meta.biometricWrappedPassword)
        if (result is BiometricCipherInitResult.Invalidated) {
            invalidateBiometricEnrollment()
        }
        result
    }

    suspend fun invalidateBiometricEnrollment() = withContext(Dispatchers.IO) {
        BlackBoxSecurity.deleteBiometricKeystoreKey()
        val meta = vaultDao.getMetadata() ?: return@withContext
        if (meta.isBiometricEnabled || meta.biometricWrappedPassword != null) {
            vaultDao.insertMetadata(
                meta.copy(
                    isBiometricEnabled = false,
                    biometricWrappedPassword = null
                )
            )
        }
    }

    suspend fun unlockWithBiometrics(authenticatedCipher: Cipher? = null): Pair<SecretKey, String>? = withContext(Dispatchers.IO) {
        if (authenticatedCipher == null) return@withContext null
        val meta = vaultDao.getMetadata() ?: return@withContext null
        if (!meta.isBiometricEnabled || meta.biometricWrappedPassword == null) return@withContext null

        try {
            val masterPassword = BlackBoxSecurity.unwrapPasswordFromBiometrics(
                wrappedBase64 = meta.biometricWrappedPassword,
                authenticatedCipher = authenticatedCipher
            )
            val salt = Base64.decode(meta.masterSalt, Base64.NO_WRAP)
            if (BlackBoxSecurity.verifyPassword(masterPassword.toCharArray(), salt, meta.verifierHash)) {
                val key = BlackBoxSecurity.deriveMasterKey(masterPassword.toCharArray(), salt)
                Pair(key, masterPassword)
            } else {
                null
            }
        } catch (_: KeyPermanentlyInvalidatedException) {
            invalidateBiometricEnrollment()
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun setupDuressPin(duressPin: String): SecretKey = withContext(Dispatchers.IO) {
        val meta = vaultDao.getMetadata() ?: error("Vault not initialized")
        val salt = BlackBoxSecurity.generateRandomSalt()
        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val verifier = BlackBoxSecurity.computeVerificationHash(duressPin.toCharArray(), salt)

        val updatedMeta = meta.copy(
            hasDuressPin = true,
            duressSalt = saltBase64,
            duressVerifierHash = verifier
        )
        vaultDao.insertMetadata(updatedMeta)
        BlackBoxSecurity.deriveMasterKey(duressPin.toCharArray(), salt)
    }

    suspend fun removeDuressPin() = withContext(Dispatchers.IO) {
        val meta = vaultDao.getMetadata() ?: return@withContext
        vaultDao.insertMetadata(
            meta.copy(
                hasDuressPin = false,
                duressSalt = null,
                duressVerifierHash = null
            )
        )
        vaultDao.deleteAllRecordsInPartition(VaultPartition.DURESS)
        vaultDao.deleteAllAttachmentsInPartition(VaultPartition.DURESS)
        passwordDao?.deletePasswordsByPartition(VaultPartition.DURESS)
        secureNoteDao?.deleteNotesByPartition(VaultPartition.DURESS)
    }

    suspend fun populateDecoyVault(decoyKey: SecretKey) = withContext(Dispatchers.IO) {
        val decoys = BlackBoxSecurity.generateRealisticDecoyItems()
        for (item in decoys) {
            saveItem(item, decoyKey, partitionId = 1)
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean, currentPassword: String): Boolean = withContext(Dispatchers.IO) {
        val meta = vaultDao.getMetadata() ?: return@withContext false
        val salt = Base64.decode(meta.masterSalt, Base64.NO_WRAP)
        if (!BlackBoxSecurity.verifyPassword(currentPassword.toCharArray(), salt, meta.verifierHash)) {
            return@withContext false
        }
        try {
            BlackBoxSecurity.deleteBiometricKeystoreKey()
            val wrapped = if (enabled) {
                BlackBoxSecurity.wrapPasswordForBiometrics(currentPassword)
            } else null

            vaultDao.insertMetadata(
                meta.copy(
                    isBiometricEnabled = enabled && wrapped != null,
                    biometricWrappedPassword = wrapped
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun enrollVoiceBiometric(
        currentPassword: String,
        audioSamples: List<ShortArray>
    ): Result<VoiceprintTemplate> = withContext(Dispatchers.IO) {
        val meta = vaultDao.getMetadata()
            ?: return@withContext Result.failure(IllegalStateException("Vault is not initialized."))
        val salt = Base64.decode(meta.masterSalt, Base64.NO_WRAP)
        if (!BlackBoxSecurity.verifyPassword(currentPassword.toCharArray(), salt, meta.verifierHash)) {
            return@withContext Result.failure(SecurityException("Master Password verification failed."))
        }

        val templateResult = VoiceBiometricEngine.buildEnrollmentTemplate(audioSamples)
        if (templateResult.isFailure) {
            return@withContext templateResult
        }

        val template = templateResult.getOrThrow()
        try {
            val serializedTemplate = VoiceBiometricEngine.serializeTemplate(template)
            val encryptedProfile = voiceDataProtector.protect(serializedTemplate)
            val wrappedPassword = voiceDataProtector.protect(currentPassword)

            vaultDao.insertMetadata(
                meta.copy(
                    isVoiceBiometricEnabled = true,
                    encryptedVoiceProfile = encryptedProfile,
                    voiceWrappedPassword = wrappedPassword
                )
            )
            Result.success(template)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Hardware Keystore failed to seal voiceprint: ${e.message}", e))
        }
    }

    suspend fun verifyAndUnlockWithVoice(audioSample: ShortArray): VoiceUnlockOutcome =
        withContext(Dispatchers.Default) {
            val meta = withContext(Dispatchers.IO) { vaultDao.getMetadata() }
                ?: return@withContext VoiceUnlockOutcome.Unavailable("Vault metadata unavailable.")

            if (!meta.isVoiceBiometricEnabled ||
                meta.encryptedVoiceProfile.isNullOrEmpty() ||
                meta.voiceWrappedPassword.isNullOrEmpty()
            ) {
                return@withContext VoiceUnlockOutcome.Unavailable("Voice biometric profile is not enrolled.")
            }

            val template = try {
                val json = voiceDataProtector.unprotect(meta.encryptedVoiceProfile)
                VoiceBiometricEngine.deserializeTemplate(json)
            } catch (e: Exception) {
                return@withContext VoiceUnlockOutcome.Unavailable("Unable to decrypt sealed voiceprint profile.")
            }

            val verification = VoiceBiometricEngine.verifyAgainstTemplate(audioSample, template)
            if (!verification.isVerified) {
                return@withContext VoiceUnlockOutcome.Rejected(verification)
            }

            return@withContext try {
                val masterPassword = voiceDataProtector.unprotect(meta.voiceWrappedPassword)
                val salt = Base64.decode(meta.masterSalt, Base64.NO_WRAP)
                if (BlackBoxSecurity.verifyPassword(masterPassword.toCharArray(), salt, meta.verifierHash)) {
                    val key = BlackBoxSecurity.deriveMasterKey(masterPassword.toCharArray(), salt)
                    VoiceUnlockOutcome.Success(key, masterPassword, verification)
                } else {
                    VoiceUnlockOutcome.Unavailable("Voice credentials out of sync with Master Password. Use password.")
                }
            } catch (e: Exception) {
                VoiceUnlockOutcome.Unavailable("Keystore failed to unwrap vault credentials: ${e.message}")
            }
        }

    suspend fun disableVoiceBiometric(currentPassword: String): Boolean = withContext(Dispatchers.IO) {
        val meta = vaultDao.getMetadata() ?: return@withContext false
        val salt = Base64.decode(meta.masterSalt, Base64.NO_WRAP)
        if (!BlackBoxSecurity.verifyPassword(currentPassword.toCharArray(), salt, meta.verifierHash)) {
            return@withContext false
        }
        vaultDao.insertMetadata(
            meta.copy(
                isVoiceBiometricEnabled = false,
                encryptedVoiceProfile = null,
                voiceWrappedPassword = null
            )
        )
        true
    }

    suspend fun updateAutoLockMinutes(minutes: Int) = withContext(Dispatchers.IO) {
        val meta = vaultDao.getMetadata() ?: return@withContext
        vaultDao.insertMetadata(meta.copy(autoLockMinutes = minutes))
    }

    suspend fun changeMasterPassword(oldPassword: String, newPassword: String): Boolean = withContext(Dispatchers.IO) {
        val unlockResult = unlockWithPasswordOrDuress(oldPassword)
        if (unlockResult == null || unlockResult.isDuress) return@withContext false
        val oldKey = unlockResult.secretKey
        val allDecrypted = getAllDecryptedItems(oldKey, partitionId = 0)

        // Generate new salt and new key for Primary partition
        val newSalt = BlackBoxSecurity.generateRandomSalt()
        val newSaltBase64 = Base64.encodeToString(newSalt, Base64.NO_WRAP)
        val newVerifier = BlackBoxSecurity.computeVerificationHash(newPassword.toCharArray(), newSalt)
        val newKey = BlackBoxSecurity.deriveMasterKey(newPassword.toCharArray(), newSalt)

        val meta = vaultDao.getMetadata() ?: return@withContext false
        val newBiometricWrapped = if (meta.isBiometricEnabled) {
            try {
                BlackBoxSecurity.wrapPasswordForBiometrics(newPassword)
            } catch (e: Exception) {
                null
            }
        } else null

        val newVoiceWrapped = if (meta.isVoiceBiometricEnabled && !meta.encryptedVoiceProfile.isNullOrEmpty()) {
            try {
                voiceDataProtector.protect(newPassword)
            } catch (e: Exception) {
                null
            }
        } else null

        vaultDao.insertMetadata(
            meta.copy(
                masterSalt = newSaltBase64,
                verifierHash = newVerifier,
                biometricWrappedPassword = newBiometricWrapped,
                isBiometricEnabled = meta.isBiometricEnabled && newBiometricWrapped != null,
                voiceWrappedPassword = newVoiceWrapped,
                isVoiceBiometricEnabled = meta.isVoiceBiometricEnabled && newVoiceWrapped != null,
                encryptedVoiceProfile = if (newVoiceWrapped != null) meta.encryptedVoiceProfile else null
            )
        )

        // Re-encrypt primary records with the new key (v2 AAD-authenticated AES-256-GCM)
        val reencryptedEntities = allDecrypted.map { item ->
            val (ciphertext, iv) = BlackBoxSecurity.encryptRecordPayload(item, newKey, partitionId = 0)
            VaultRecordEntity(
                id = item.id,
                title = item.title,
                category = item.category.name,
                encryptedPayload = ciphertext,
                initializationVector = iv,
                isFavorite = item.isFavorite,
                partitionId = 0,
                passwordUpdatedAt = item.passwordUpdatedAt,
                pwnedBreachCount = item.pwnedBreachCount,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt
            )
        }
        vaultDao.insertRecords(reencryptedEntities)

        // Sync legacy DAOs for Primary partition only (never touch Duress partition)
        passwordDao?.let { pDao ->
            pDao.deletePasswordsByPartition(VaultPartition.PRIMARY)
            allDecrypted.filter { it.category == VaultCategory.LOGIN }.forEach { login ->
                val (pwCipher, pwIv) = BlackBoxSecurity.encrypt(login.password, newKey)
                val pwField = EncryptedField(pwCipher, pwIv)
                val notesField = if (login.notes.isNotEmpty()) {
                    val (nCipher, nIv) = BlackBoxSecurity.encrypt(login.notes, newKey)
                    EncryptedField(nCipher, nIv)
                } else null
                pDao.insertPassword(
                    PasswordEntity(
                        id = login.id,
                        title = login.title,
                        username = login.username,
                        encryptedPassword = pwField,
                        url = login.url,
                        encryptedNotes = notesField,
                        encryptedPasswordBlob = pwField.toEncryptedBlob(),
                        encryptedNotesBlob = notesField?.toEncryptedBlob(),
                        isFavorite = login.isFavorite,
                        partitionId = VaultPartition.PRIMARY,
                        createdAt = login.createdAt,
                        updatedAt = login.updatedAt
                    )
                )
            }
        }

        secureNoteDao?.let { nDao ->
            nDao.deleteNotesByPartition(VaultPartition.PRIMARY)
            allDecrypted.filter { it.category == VaultCategory.NOTE }.forEach { note ->
                val (nCipher, nIv) = BlackBoxSecurity.encrypt(note.notes, newKey)
                val contentField = EncryptedField(nCipher, nIv)
                nDao.insertNote(
                    SecureNoteEntity(
                        id = note.id,
                        title = note.title,
                        encryptedContent = contentField,
                        encryptedContentBlob = contentField.toEncryptedBlob(),
                        folder = "General",
                        isFavorite = note.isFavorite,
                        partitionId = VaultPartition.PRIMARY,
                        createdAt = note.createdAt,
                        updatedAt = note.updatedAt
                    )
                )
            }
        }

        true
    }

    suspend fun saveItem(item: VaultItem, secretKey: SecretKey, partitionId: Int = 0) = withContext(Dispatchers.IO) {
        val (ciphertext, iv) = BlackBoxSecurity.encryptRecordPayload(item, secretKey, partitionId)
        val entity = VaultRecordEntity(
            id = item.id,
            title = item.title,
            category = item.category.name,
            encryptedPayload = ciphertext,
            initializationVector = iv,
            isFavorite = item.isFavorite,
            partitionId = partitionId,
            passwordUpdatedAt = item.passwordUpdatedAt,
            pwnedBreachCount = item.pwnedBreachCount,
            createdAt = item.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        vaultDao.insertRecord(entity)

        when (item.category) {
            VaultCategory.LOGIN -> {
                passwordDao?.let { pDao ->
                    val (pwCipher, pwIv) = BlackBoxSecurity.encrypt(item.password, secretKey)
                    val pwField = EncryptedField(pwCipher, pwIv)
                    val notesEncrypted = if (item.notes.isNotEmpty()) {
                        val (nCipher, nIv) = BlackBoxSecurity.encrypt(item.notes, secretKey)
                        EncryptedField(nCipher, nIv)
                    } else null

                    pDao.insertPassword(
                        PasswordEntity(
                            id = item.id,
                            title = item.title,
                            username = item.username,
                            encryptedPassword = pwField,
                            url = item.url,
                            encryptedNotes = notesEncrypted,
                            encryptedPasswordBlob = pwField.toEncryptedBlob(),
                            encryptedNotesBlob = notesEncrypted?.toEncryptedBlob(),
                            isFavorite = item.isFavorite,
                            partitionId = partitionId,
                            createdAt = item.createdAt,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            VaultCategory.NOTE -> {
                secureNoteDao?.let { nDao ->
                    val (contentCipher, contentIv) = BlackBoxSecurity.encrypt(item.notes, secretKey)
                    val contentField = EncryptedField(contentCipher, contentIv)
                    nDao.insertNote(
                        SecureNoteEntity(
                            id = item.id,
                            title = item.title,
                            encryptedContent = contentField,
                            encryptedContentBlob = contentField.toEncryptedBlob(),
                            folder = "General",
                            isFavorite = item.isFavorite,
                            partitionId = partitionId,
                            createdAt = item.createdAt,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            else -> {}
        }
    }

    suspend fun saveItemsBatch(items: List<VaultItem>, secretKey: SecretKey, partitionId: Int = 0) =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext
            val now = System.currentTimeMillis()
            val recordEntities = items.map { item ->
                val (ciphertext, iv) = BlackBoxSecurity.encryptRecordPayload(item, secretKey, partitionId)
                VaultRecordEntity(
                    id = item.id,
                    title = item.title,
                    category = item.category.name,
                    encryptedPayload = ciphertext,
                    initializationVector = iv,
                    isFavorite = item.isFavorite,
                    partitionId = partitionId,
                    passwordUpdatedAt = item.passwordUpdatedAt,
                    pwnedBreachCount = item.pwnedBreachCount,
                    createdAt = item.createdAt,
                    updatedAt = now
                )
            }
            vaultDao.insertRecords(recordEntities)

            passwordDao?.let { pDao ->
                val pwEntities = items.filter { it.category == VaultCategory.LOGIN }.map { item ->
                    val (pwCipher, pwIv) = BlackBoxSecurity.encrypt(item.password, secretKey)
                    val pwField = EncryptedField(pwCipher, pwIv)
                    val notesEncrypted = if (item.notes.isNotEmpty()) {
                        val (nCipher, nIv) = BlackBoxSecurity.encrypt(item.notes, secretKey)
                        EncryptedField(nCipher, nIv)
                    } else null
                    PasswordEntity(
                        id = item.id,
                        title = item.title,
                        username = item.username,
                        encryptedPassword = pwField,
                        url = item.url,
                        encryptedNotes = notesEncrypted,
                        encryptedPasswordBlob = pwField.toEncryptedBlob(),
                        encryptedNotesBlob = notesEncrypted?.toEncryptedBlob(),
                        isFavorite = item.isFavorite,
                        partitionId = partitionId,
                        createdAt = item.createdAt,
                        updatedAt = now
                    )
                }
                if (pwEntities.isNotEmpty()) pDao.insertPasswords(pwEntities)
            }

            secureNoteDao?.let { nDao ->
                val noteEntities = items.filter { it.category == VaultCategory.NOTE }.map { item ->
                    val (contentCipher, contentIv) = BlackBoxSecurity.encrypt(item.notes, secretKey)
                    val contentField = EncryptedField(contentCipher, contentIv)
                    SecureNoteEntity(
                        id = item.id,
                        title = item.title,
                        encryptedContent = contentField,
                        encryptedContentBlob = contentField.toEncryptedBlob(),
                        folder = "Imported",
                        isFavorite = item.isFavorite,
                        partitionId = partitionId,
                        createdAt = item.createdAt,
                        updatedAt = now
                    )
                }
                if (noteEntities.isNotEmpty()) nDao.insertNotes(noteEntities)
            }
        }

    suspend fun updateBreachCount(id: String, count: Int) = withContext(Dispatchers.IO) {
        vaultDao.updateBreachCount(id, count)
    }

    suspend fun deleteItem(id: String, partitionId: Int) = withContext(Dispatchers.IO) {
        vaultDao.deleteRecordById(id, partitionId)
        vaultDao.deleteAttachmentsForRecord(id)
        passwordDao?.deletePasswordByIdAndPartition(id, partitionId)
        secureNoteDao?.deleteNoteByIdAndPartition(id, partitionId)
    }

    suspend fun toggleFavorite(id: String, partitionId: Int) = withContext(Dispatchers.IO) {
        val entity = vaultDao.getRecordById(id, partitionId) ?: return@withContext
        val newFav = !entity.isFavorite
        vaultDao.insertRecord(entity.copy(isFavorite = newFav))

        if (partitionId == 0) {
            passwordDao?.getPasswordById(id)?.let {
                passwordDao.insertPassword(it.copy(isFavorite = newFav))
            }
            secureNoteDao?.getNoteById(id)?.let {
                secureNoteDao.insertNote(it.copy(isFavorite = newFav))
            }
        }
    }

    suspend fun getAllDecryptedItems(secretKey: SecretKey, partitionId: Int = 0): List<VaultItem> = withContext(Dispatchers.IO) {
        val entities = vaultDao.getAllRecords(partitionId)
        val allAttachments = vaultDao.getAllAttachmentsForPartition(partitionId)
        val attachmentMap = allAttachments.groupBy { it.recordId }

        entities.mapNotNull { entity ->
            try {
                val item = BlackBoxSecurity.decryptRecordPayload(
                    id = entity.id,
                    title = entity.title,
                    category = VaultCategory.fromString(entity.category),
                    partitionId = entity.partitionId,
                    encryptedPayload = entity.encryptedPayload,
                    initializationVector = entity.initializationVector,
                    isFavorite = entity.isFavorite,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    secretKey = secretKey
                )
                val itemAttachments = (attachmentMap[entity.id] ?: emptyList()).map {
                    VaultAttachment(
                        id = it.id,
                        recordId = it.recordId,
                        partitionId = it.partitionId,
                        fileName = it.fileName,
                        mimeType = it.mimeType,
                        fileSizeBytes = it.fileSizeBytes,
                        encryptedStoragePath = it.encryptedStoragePath,
                        createdAt = it.createdAt
                    )
                }
                item.copy(
                    partitionId = partitionId,
                    pwnedBreachCount = entity.pwnedBreachCount,
                    passwordUpdatedAt = if (entity.passwordUpdatedAt > 0) entity.passwordUpdatedAt else entity.updatedAt,
                    attachments = itemAttachments
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun searchDecryptedItems(
        partitionId: Int,
        query: String?,
        category: VaultCategory?,
        secretKey: SecretKey
    ): List<VaultItem> = withContext(Dispatchers.IO) {
        val categoryName = category?.name
        val entities = vaultDao.searchRecords(partitionId, query?.takeIf { it.isNotBlank() }, categoryName)
        val allAttachments = vaultDao.getAllAttachmentsForPartition(partitionId)
        val attachmentMap = allAttachments.groupBy { it.recordId }

        entities.mapNotNull { entity ->
            try {
                val item = BlackBoxSecurity.decryptRecordPayload(
                    id = entity.id,
                    title = entity.title,
                    category = VaultCategory.fromString(entity.category),
                    partitionId = entity.partitionId,
                    encryptedPayload = entity.encryptedPayload,
                    initializationVector = entity.initializationVector,
                    isFavorite = entity.isFavorite,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    secretKey = secretKey
                )
                val itemAttachments = (attachmentMap[entity.id] ?: emptyList()).map {
                    VaultAttachment(
                        id = it.id,
                        recordId = it.recordId,
                        partitionId = it.partitionId,
                        fileName = it.fileName,
                        mimeType = it.mimeType,
                        fileSizeBytes = it.fileSizeBytes,
                        encryptedStoragePath = it.encryptedStoragePath,
                        createdAt = it.createdAt
                    )
                }
                item.copy(
                    partitionId = partitionId,
                    pwnedBreachCount = entity.pwnedBreachCount,
                    passwordUpdatedAt = if (entity.passwordUpdatedAt > 0) entity.passwordUpdatedAt else entity.updatedAt,
                    attachments = itemAttachments
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun saveAttachment(attachment: VaultAttachment) = withContext(Dispatchers.IO) {
        vaultDao.insertAttachment(
            VaultAttachmentEntity(
                id = attachment.id,
                recordId = attachment.recordId,
                partitionId = attachment.partitionId,
                fileName = attachment.fileName,
                mimeType = attachment.mimeType,
                fileSizeBytes = attachment.fileSizeBytes,
                encryptedStoragePath = attachment.encryptedStoragePath,
                createdAt = attachment.createdAt
            )
        )
    }

    suspend fun deleteAttachment(id: String) = withContext(Dispatchers.IO) {
        vaultDao.deleteAttachmentById(id)
    }

    suspend fun getRawEntities(partitionId: Int = 0): List<VaultRecordEntity> = withContext(Dispatchers.IO) {
        vaultDao.getAllRecords(partitionId)
    }

    suspend fun replaceAllEntities(entities: List<VaultRecordEntity>, partitionId: Int = 0) = withContext(Dispatchers.IO) {
        vaultDao.deleteAllRecordsInPartition(partitionId)
        vaultDao.insertRecords(entities.map { it.copy(partitionId = partitionId) })
    }

    suspend fun mergeEntities(entities: List<VaultRecordEntity>, partitionId: Int = 0) = withContext(Dispatchers.IO) {
        vaultDao.insertRecords(entities.map { it.copy(partitionId = partitionId) })
    }

    suspend fun wipeVault() = withContext(Dispatchers.IO) {
        BlackBoxSecurity.deleteBiometricKeystoreKey()
        BlackBoxSecurity.deleteVoiceBiometricKeystoreKey()
        vaultDao.deleteAllRecords()
        vaultDao.deleteAllAttachments()
        passwordDao?.deleteAllPasswords()
        secureNoteDao?.deleteAllNotes()
        vaultDao.deleteMetadata()
    }
}
