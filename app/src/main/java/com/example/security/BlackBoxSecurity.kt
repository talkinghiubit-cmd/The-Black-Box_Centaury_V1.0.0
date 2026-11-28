package com.example.security

import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import com.example.model.PasswordStrength
import com.example.model.VaultCategory
import com.example.model.VaultItem
import org.json.JSONObject
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.ProviderException
import java.security.PublicKey
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

sealed class BiometricCipherInitResult {
    data class Ready(
        val cipher: Cipher,
        val isStrongBoxBacked: Boolean,
        val isHardwareBacked: Boolean
    ) : BiometricCipherInitResult()

    data class Invalidated(val reason: String) : BiometricCipherInitResult()
    data class Unavailable(val reason: String) : BiometricCipherInitResult()
}

object BlackBoxSecurity {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val SALT_LENGTH_BYTES = 16

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS_BIOMETRIC = "BlackBoxBiometricKey"
    private const val KEYSTORE_ALIAS_BIOMETRIC_V2 = "BlackBoxBiometricStrongBoxV2"
    private const val BIOMETRIC_BLOB_V2_PREFIX = "bio_v2:"
    private const val RSA_OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private const val KEYSTORE_ALIAS_VOICE_BIOMETRIC = "BlackBoxVoiceBiometricKey"

    private val biometricOaepSpec = OAEPParameterSpec(
        "SHA-256",
        "MGF1",
        MGF1ParameterSpec.SHA1,
        PSource.PSpecified.DEFAULT
    )

    private val secureRandom = SecureRandom()

    fun generateRandomSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun generateRandomIv(): ByteArray = AesGcmCryptoEngine.generateIv()

    fun deriveMasterKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            val keyBytes = factory.generateSecret(spec).encoded
            SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun computeVerificationHash(password: CharArray, salt: ByteArray): String {
        val spec = PBEKeySpec(password, salt, 50_000, 256)
        return try {
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            val hashBytes = factory.generateSecret(spec).encoded
            Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
        }
    }

    fun verifyPassword(password: CharArray, salt: ByteArray, expectedHash: String): Boolean {
        val calculated = computeVerificationHash(password, salt)
        return MessageDigest.isEqual(calculated.toByteArray(Charsets.UTF_8), expectedHash.toByteArray(Charsets.UTF_8))
    }

    /**
     * Encrypts plaintext string using AES-256-GCM.
     * Returns Pair of (Base64 Ciphertext or v2 envelope, Base64 IV).
     */
    fun encrypt(
        plaintext: String,
        secretKey: SecretKey,
        associatedData: ByteArray? = null
    ): Pair<String, String> {
        val encrypted = AesGcmCryptoEngine.encryptString(plaintext, secretKey, associatedData)
        return Pair(encrypted.ciphertext, encrypted.iv)
    }

    /**
     * Decrypts Base64 ciphertext (or v2 AAD envelope) with Base64 IV using AES-256-GCM.
     */
    fun decrypt(
        base64Ciphertext: String,
        base64Iv: String,
        secretKey: SecretKey,
        associatedData: ByteArray? = null
    ): String {
        return AesGcmCryptoEngine.decryptString(base64Ciphertext, base64Iv, secretKey, associatedData)
    }

    /**
     * Encrypts a [VaultItem] at rest using AES-256-GCM with canonical AAD binding
     * (record ID, partition ID, and category).
     */
    fun encryptRecordPayload(
        item: VaultItem,
        secretKey: SecretKey,
        partitionId: Int = item.partitionId
    ): Pair<String, String> {
        val payload = serializeItemPayload(item.copy(partitionId = partitionId))
        val aad = AesGcmCryptoEngine.buildRecordAad(item.id, partitionId, item.category.name)
        return encrypt(payload, secretKey, aad)
    }

    /**
     * Decrypts a stored vault record payload and verifies canonical AAD & partition isolation.
     */
    fun decryptRecordPayload(
        id: String,
        title: String,
        category: VaultCategory,
        partitionId: Int,
        encryptedPayload: String,
        initializationVector: String,
        isFavorite: Boolean,
        createdAt: Long,
        updatedAt: Long,
        secretKey: SecretKey
    ): VaultItem {
        val aad = AesGcmCryptoEngine.buildRecordAad(id, partitionId, category.name)
        val decryptedJson = decrypt(encryptedPayload, initializationVector, secretKey, aad)
        val item = deserializeItemPayload(
            id = id,
            title = title,
            category = category,
            isFavorite = isFavorite,
            createdAt = createdAt,
            updatedAt = updatedAt,
            jsonString = decryptedJson
        )
        require(item.partitionId == partitionId) {
            "Partition isolation violation detected for record $id"
        }
        return item
    }

    /**
     * Raw byte encryption for files and attachments using AES-256-GCM.
     */
    fun encryptFileBytes(
        bytes: ByteArray,
        secretKey: SecretKey,
        associatedData: ByteArray? = null
    ): Pair<ByteArray, ByteArray> {
        return AesGcmCryptoEngine.encryptBytes(bytes, secretKey, associatedData)
    }

    /**
     * Raw byte decryption for files and attachments using AES-256-GCM.
     */
    fun decryptFileBytes(
        ciphertext: ByteArray,
        iv: ByteArray,
        secretKey: SecretKey,
        associatedData: ByteArray? = null
    ): ByteArray {
        return AesGcmCryptoEngine.decryptBytes(ciphertext, iv, secretKey, associatedData)
    }

    // Serializes sensitive fields of VaultItem to JSON string for encryption
    fun serializeItemPayload(item: VaultItem): String {
        val json = JSONObject()
        json.put("username", item.username)
        json.put("password", item.password)
        json.put("url", item.url)
        json.put("notes", item.notes)
        json.put("cardHolder", item.cardHolder)
        json.put("cardNumber", item.cardNumber)
        json.put("cardExpiry", item.cardExpiry)
        json.put("cardCvv", item.cardCvv)
        json.put("isNfcPayEnabled", item.isNfcPayEnabled)
        json.put("keyType", item.keyType)
        json.put("keyValue", item.keyValue)
        json.put("totpSecret", item.totpSecret)
        json.put("passwordUpdatedAt", item.passwordUpdatedAt)
        json.put("pwnedBreachCount", item.pwnedBreachCount)
        json.put("partitionId", item.partitionId)
        return json.toString()
    }

    // Deserializes sensitive fields of VaultItem from JSON string after decryption
    fun deserializeItemPayload(
        id: String,
        title: String,
        category: VaultCategory,
        isFavorite: Boolean,
        createdAt: Long,
        updatedAt: Long,
        jsonString: String
    ): VaultItem {
        val json = JSONObject(jsonString)
        return VaultItem(
            id = id,
            title = title,
            category = category,
            username = json.optString("username", ""),
            password = json.optString("password", ""),
            url = json.optString("url", ""),
            notes = json.optString("notes", ""),
            cardHolder = json.optString("cardHolder", ""),
            cardNumber = json.optString("cardNumber", ""),
            cardExpiry = json.optString("cardExpiry", ""),
            cardCvv = json.optString("cardCvv", ""),
            isNfcPayEnabled = json.optBoolean("isNfcPayEnabled", true),
            keyType = json.optString("keyType", ""),
            keyValue = json.optString("keyValue", ""),
            totpSecret = json.optString("totpSecret", ""),
            passwordUpdatedAt = json.optLong("passwordUpdatedAt", updatedAt),
            pwnedBreachCount = json.optInt("pwnedBreachCount", 0),
            partitionId = json.optInt("partitionId", 0),
            isFavorite = isFavorite,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun computeSha256(content: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(content.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes uppercase SHA-1 Hex hash for HaveIBeenPwned k-anonymity checking.
     */
    fun sha1Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02X".format(it) }
    }

    // Calculates real-time password strength and entropy
    fun evaluatePasswordStrength(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength(0, "Empty", 0)

        var poolSize = 0
        if (password.any { it.isLowerCase() }) poolSize += 26
        if (password.any { it.isUpperCase() }) poolSize += 26
        if (password.any { it.isDigit() }) poolSize += 10
        if (password.any { !it.isLetterOrDigit() }) poolSize += 32

        if (poolSize == 0) poolSize = 10

        val entropy = (password.length * (Math.log(poolSize.toDouble()) / Math.log(2.0))).toInt()

        val (score, label) = when {
            entropy < 28 -> 0 to "Very Weak"
            entropy < 40 -> 1 to "Weak"
            entropy < 60 -> 2 to "Moderate"
            entropy < 80 -> 3 to "Strong"
            else -> 4 to "Very Strong"
        }

        return PasswordStrength(score, label, entropy)
    }

    // Generates high-entropy passwords with custom constraints
    fun generatePassword(
        length: Int = 20,
        includeUppercase: Boolean = true,
        includeLowercase: Boolean = true,
        includeNumbers: Boolean = true,
        includeSymbols: Boolean = true,
        excludeAmbiguous: Boolean = true
    ): String {
        val uppercase = if (excludeAmbiguous) "ABCDEFGHJKLMNPQRSTUVWXYZ" else "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lowercase = if (excludeAmbiguous) "abcdefghijkmnopqrstuvwxyz" else "abcdefghijklmnopqrstuvwxyz"
        val numbers = if (excludeAmbiguous) "23456789" else "0123456789"
        val symbols = "!@#$%^&*()-_=+[]{}<>~"

        val charPool = StringBuilder()
        val guaranteedChars = mutableListOf<Char>()

        if (includeUppercase) {
            charPool.append(uppercase)
            guaranteedChars.add(uppercase[secureRandom.nextInt(uppercase.length)])
        }
        if (includeLowercase) {
            charPool.append(lowercase)
            guaranteedChars.add(lowercase[secureRandom.nextInt(lowercase.length)])
        }
        if (includeNumbers) {
            charPool.append(numbers)
            guaranteedChars.add(numbers[secureRandom.nextInt(numbers.length)])
        }
        if (includeSymbols) {
            charPool.append(symbols)
            guaranteedChars.add(symbols[secureRandom.nextInt(symbols.length)])
        }

        if (charPool.isEmpty()) {
            charPool.append(lowercase).append(numbers)
        }

        val result = mutableListOf<Char>()
        result.addAll(guaranteedChars)

        while (result.size < length) {
            val randomChar = charPool[secureRandom.nextInt(charPool.length)]
            result.add(randomChar)
        }

        result.shuffle(secureRandom)
        return result.take(length).joinToString("")
    }

    // Hardware Keystore (StrongBox + TEE fallback) with biometric enrollment invalidation
    // and strict per-operation BiometricPrompt.CryptoObject enforcement.
    private fun buildBiometricKeyGenSpec(useStrongBox: Boolean): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS_BIOMETRIC_V2,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        return builder.build()
    }

    private fun isBiometricKeyStillValid(privateKey: PrivateKey): Boolean {
        return try {
            val probe = Cipher.getInstance(RSA_OAEP_TRANSFORMATION)
            probe.init(Cipher.DECRYPT_MODE, privateKey, biometricOaepSpec)
            true
        } catch (_: KeyPermanentlyInvalidatedException) {
            false
        } catch (_: UnrecoverableKeyException) {
            false
        } catch (_: InvalidKeyException) {
            false
        }
    }

    private fun getOrCreateBiometricKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEYSTORE_ALIAS_BIOMETRIC_V2)) {
            val entry = runCatching {
                keyStore.getEntry(KEYSTORE_ALIAS_BIOMETRIC_V2, null) as? KeyStore.PrivateKeyEntry
            }.getOrNull()
            if (entry != null && isBiometricKeyStillValid(entry.privateKey)) {
                return KeyPair(entry.certificate.publicKey, entry.privateKey)
            }
            runCatching { keyStore.deleteEntry(KEYSTORE_ALIAS_BIOMETRIC_V2) }
        }

        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                kpg.initialize(buildBiometricKeyGenSpec(useStrongBox = true))
                return kpg.generateKeyPair()
            } catch (_: StrongBoxUnavailableException) {
                // Device lacks StrongBox HSM; gracefully fall back to TEE hardware Keystore
            } catch (_: ProviderException) {
                // Some OEMs wrap StrongBox unavailability inside ProviderException
            }
        }
        kpg.initialize(buildBiometricKeyGenSpec(useStrongBox = false))
        return kpg.generateKeyPair()
    }

    fun deleteBiometricKeystoreKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEYSTORE_ALIAS_BIOMETRIC_V2)) {
                keyStore.deleteEntry(KEYSTORE_ALIAS_BIOMETRIC_V2)
            }
            if (keyStore.containsAlias(KEYSTORE_ALIAS_BIOMETRIC)) {
                keyStore.deleteEntry(KEYSTORE_ALIAS_BIOMETRIC)
            }
        }
    }

    fun isBiometricKeyStrongBoxBacked(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val key = keyStore.getKey(KEYSTORE_ALIAS_BIOMETRIC_V2, null) as? PrivateKey ?: return false
            val keyFactory = KeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            val keyInfo = keyFactory.getKeySpec(key, KeyInfo::class.java)
            keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
        }.getOrDefault(false)
    }

    fun wrapPasswordForBiometrics(masterPassword: String): String {
        val publicKey: PublicKey = getOrCreateBiometricKeyPair().public
        val cipher = Cipher.getInstance(RSA_OAEP_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, biometricOaepSpec)
        val passwordBytes = masterPassword.toByteArray(Charsets.UTF_8)
        return try {
            val encrypted = cipher.doFinal(passwordBytes)
            "$BIOMETRIC_BLOB_V2_PREFIX${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"
        } finally {
            passwordBytes.fill(0)
        }
    }

    fun prepareBiometricDecryptCipher(wrappedBase64: String?): BiometricCipherInitResult {
        if (wrappedBase64.isNullOrBlank()) {
            return BiometricCipherInitResult.Unavailable("No biometric credential wrapped.")
        }
        if (!wrappedBase64.startsWith(BIOMETRIC_BLOB_V2_PREFIX)) {
            return BiometricCipherInitResult.Invalidated(
                "Biometric vault key upgraded to StrongBox CryptoObject. Unlock with Master Password and re-enable biometrics."
            )
        }

        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val privateKey = keyStore.getKey(KEYSTORE_ALIAS_BIOMETRIC_V2, null) as? PrivateKey
                ?: return BiometricCipherInitResult.Invalidated(
                    "Hardware biometric key missing or purged. Master Password verification required."
                )

            var isStrongBoxBacked = false
            var isHardwareBacked = false
            runCatching {
                val keyFactory = KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEYSTORE)
                val keyInfo = keyFactory.getKeySpec(privateKey, KeyInfo::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    isStrongBoxBacked = keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
                    isHardwareBacked = isStrongBoxBacked ||
                        keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT
                } else {
                    @Suppress("DEPRECATION")
                    isHardwareBacked = keyInfo.isInsideSecureHardware
                }
            }

            val cipher = Cipher.getInstance(RSA_OAEP_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, privateKey, biometricOaepSpec)
            BiometricCipherInitResult.Ready(
                cipher = cipher,
                isStrongBoxBacked = isStrongBoxBacked,
                isHardwareBacked = isHardwareBacked
            )
        } catch (_: KeyPermanentlyInvalidatedException) {
            BiometricCipherInitResult.Invalidated(
                "New biometric enrollment detected. Hardware key invalidated for your protection."
            )
        } catch (e: UnrecoverableKeyException) {
            BiometricCipherInitResult.Invalidated(
                "Biometric key invalidated (${e.message ?: "enrollment changed"}). Master Password required."
            )
        } catch (e: InvalidKeyException) {
            BiometricCipherInitResult.Invalidated(
                "Biometric key invalidated (${e.message ?: "key mismatch"}). Master Password required."
            )
        } catch (e: KeyStoreException) {
            BiometricCipherInitResult.Unavailable(
                "Hardware Keystore unavailable: ${e.message ?: "unknown error"}"
            )
        } catch (e: Exception) {
            BiometricCipherInitResult.Unavailable(
                "Unable to initialize biometric Cipher: ${e.message ?: "unknown error"}"
            )
        }
    }

    fun unwrapPasswordFromBiometrics(
        wrappedBase64: String,
        authenticatedCipher: Cipher?
    ): String {
        if (authenticatedCipher == null) {
            throw SecurityException("Authenticated BiometricPrompt.CryptoObject Cipher is required.")
        }
        if (!wrappedBase64.startsWith(BIOMETRIC_BLOB_V2_PREFIX)) {
            throw SecurityException("Legacy unauthenticated biometric blob rejected.")
        }
        val encrypted = Base64.decode(
            wrappedBase64.removePrefix(BIOMETRIC_BLOB_V2_PREFIX),
            Base64.NO_WRAP
        )
        require(encrypted.isNotEmpty()) { "Malformed biometric wrapped blob" }
        val decrypted = authenticatedCipher.doFinal(encrypted)
        return try {
            String(decrypted, Charsets.UTF_8)
        } finally {
            decrypted.fill(0)
        }
    }

    // Hardware Keystore support for Voice Biometric template & credential wrapping
    private fun getOrCreateVoiceBiometricKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEYSTORE_ALIAS_VOICE_BIOMETRIC)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS_VOICE_BIOMETRIC,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
        return (keyStore.getEntry(KEYSTORE_ALIAS_VOICE_BIOMETRIC, null) as KeyStore.SecretKeyEntry).secretKey
    }

    fun wrapVoiceData(plaintext: String): String {
        val key = getOrCreateVoiceBiometricKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun unwrapVoiceData(wrappedBase64: String): String {
        val combined = Base64.decode(wrappedBase64, Base64.NO_WRAP)
        require(combined.size >= GCM_IV_LENGTH_BYTES + GCM_TAG_LENGTH_BITS / 8) { "Malformed voice wrapped blob" }
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
        val key = getOrCreateVoiceBiometricKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }

    fun deleteVoiceBiometricKeystoreKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEYSTORE_ALIAS_VOICE_BIOMETRIC)) {
                keyStore.deleteEntry(KEYSTORE_ALIAS_VOICE_BIOMETRIC)
            }
        }
    }

    // =========================================================================
    // RFC 6238 TOTP 2FA Authenticator Engine (delegates to TotpAuthenticatorService)
    // =========================================================================

    fun decodeBase32(base32: String): ByteArray {
        return TotpAuthenticatorService.decodeBase32Strict(base32, minBytes = 1) ?: ByteArray(0)
    }

    fun generateTotpCode(
        secretBase32: String,
        timeMillis: Long = System.currentTimeMillis(),
        digits: Int = 6,
        periodSeconds: Int = 30
    ): String? {
        if (secretBase32.isBlank()) return null
        return if (secretBase32.trim().startsWith("otpauth://", ignoreCase = true)) {
            TotpAuthenticatorService.generateCode(secretBase32, timeMillis)
        } else {
            TotpAuthenticatorService.generateCode(
                storedSeedOrUri = secretBase32,
                timeMillis = timeMillis,
                digitsOverride = digits,
                periodSecondsOverride = periodSeconds
            )
        }
    }

    fun getTotpRemainingSeconds(periodSeconds: Int = 30): Int {
        return TotpAuthenticatorService.getRemainingSeconds(periodSeconds)
    }

    fun parseTotpUri(uriString: String): Pair<String, String>? {
        return TotpAuthenticatorService.parseOtpAuthUri(uriString).getOrNull()?.let { config ->
            Pair(config.secret, config.issuer.ifBlank { config.accountName })
        }
    }

    // =========================================================================
    // Believable Decoy Account Generator for Duress Vault
    // =========================================================================

    fun generateRealisticDecoyItems(): List<VaultItem> {
        val now = System.currentTimeMillis()
        return listOf(
            VaultItem(
                id = java.util.UUID.randomUUID().toString(),
                title = "Netflix",
                category = VaultCategory.LOGIN,
                username = "alex.morris89@gmail.com",
                password = "StreamWinter#2024",
                url = "https://netflix.com",
                notes = "Standard HD Plan - Family account",
                partitionId = 1,
                passwordUpdatedAt = now - 86400000L * 45,
                createdAt = now - 86400000L * 45,
                updatedAt = now - 86400000L * 45
            ),
            VaultItem(
                id = java.util.UUID.randomUUID().toString(),
                title = "Spotify Premium",
                category = VaultCategory.LOGIN,
                username = "alex.morris89@gmail.com",
                password = "MelodyTrack99!",
                url = "https://spotify.com",
                notes = "Duo subscription with Sam",
                partitionId = 1,
                passwordUpdatedAt = now - 86400000L * 30,
                createdAt = now - 86400000L * 30,
                updatedAt = now - 86400000L * 30
            ),
            VaultItem(
                id = java.util.UUID.randomUUID().toString(),
                title = "Apartment Wi-Fi",
                category = VaultCategory.NOTE,
                notes = "SSID: OakRidge-5G\nWPA3 Key: JuniperForest7742\nGuest SSID: OakRidge-Guest",
                partitionId = 1,
                passwordUpdatedAt = now - 86400000L * 60,
                createdAt = now - 86400000L * 60,
                updatedAt = now - 86400000L * 60
            ),
            VaultItem(
                id = java.util.UUID.randomUUID().toString(),
                title = "Everyday Debit Card",
                category = VaultCategory.CARD,
                cardHolder = "Alex Morris",
                cardNumber = "4532 8812 9904 3145",
                cardExpiry = "09/27",
                cardCvv = "619",
                notes = "Checking card for groceries and utilities",
                partitionId = 1,
                passwordUpdatedAt = now - 86400000L * 20,
                createdAt = now - 86400000L * 20,
                updatedAt = now - 86400000L * 20
            ),
            VaultItem(
                id = java.util.UUID.randomUUID().toString(),
                title = "University Webmail",
                category = VaultCategory.LOGIN,
                username = "amorris@alumni.state.edu",
                password = "BlueSkyGrad#2021",
                url = "https://mail.state.edu",
                notes = "Alumni email redirection",
                partitionId = 1,
                passwordUpdatedAt = now - 86400000L * 95,
                createdAt = now - 86400000L * 95,
                updatedAt = now - 86400000L * 95
            )
        )
    }
}
