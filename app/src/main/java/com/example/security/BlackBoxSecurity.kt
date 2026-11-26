package com.example.security

import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.model.PasswordStrength
import com.example.model.VaultCategory
import com.example.model.VaultItem
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object BlackBoxSecurity {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val SALT_LENGTH_BYTES = 16

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS_BIOMETRIC = "BlackBoxBiometricKey"
    private const val KEYSTORE_ALIAS_VOICE_BIOMETRIC = "BlackBoxVoiceBiometricKey"

    private val secureRandom = SecureRandom()

    fun generateRandomSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun generateRandomIv(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        return iv
    }

    fun deriveMasterKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun computeVerificationHash(password: CharArray, salt: ByteArray): String {
        val spec = PBEKeySpec(password, salt, 50_000, 256)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val hashBytes = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }

    fun verifyPassword(password: CharArray, salt: ByteArray, expectedHash: String): Boolean {
        val calculated = computeVerificationHash(password, salt)
        return MessageDigest.isEqual(calculated.toByteArray(Charsets.UTF_8), expectedHash.toByteArray(Charsets.UTF_8))
    }

    /**
     * Encrypts plaintext string using AES-256-GCM.
     * Returns Pair of (Base64 Ciphertext, Base64 IV).
     */
    fun encrypt(plaintext: String, secretKey: SecretKey): Pair<String, String> {
        val iv = generateRandomIv()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Pair(
            Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    /**
     * Decrypts Base64 ciphertext with Base64 IV using AES-256-GCM.
     */
    fun decrypt(base64Ciphertext: String, base64Iv: String, secretKey: SecretKey): String {
        val ciphertext = Base64.decode(base64Ciphertext, Base64.NO_WRAP)
        val iv = Base64.decode(base64Iv, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Raw byte encryption for files and attachments using AES-256-GCM.
     */
    fun encryptFileBytes(bytes: ByteArray, secretKey: SecretKey): Pair<ByteArray, ByteArray> {
        val iv = generateRandomIv()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val ciphertext = cipher.doFinal(bytes)
        return Pair(ciphertext, iv)
    }

    /**
     * Raw byte decryption for files and attachments using AES-256-GCM.
     */
    fun decryptFileBytes(ciphertext: ByteArray, iv: ByteArray, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertext)
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

    // Hardware Keystore support for biometric master-key wrapping
    private fun getOrCreateBiometricKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEYSTORE_ALIAS_BIOMETRIC)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS_BIOMETRIC,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
        return (keyStore.getEntry(KEYSTORE_ALIAS_BIOMETRIC, null) as KeyStore.SecretKeyEntry).secretKey
    }

    fun wrapPasswordForBiometrics(masterPassword: String): String {
        val key = getOrCreateBiometricKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(masterPassword.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun unwrapPasswordFromBiometrics(wrappedBase64: String): String {
        val combined = Base64.decode(wrappedBase64, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        val key = getOrCreateBiometricKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
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
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        val key = getOrCreateVoiceBiometricKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }

    // =========================================================================
    // RFC 6238 TOTP 2FA Authenticator Engine
    // =========================================================================

    private val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decodeBase32(base32: String): ByteArray {
        val clean = base32.trim().replace(" ", "").replace("-", "").uppercase()
        val bytes = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0

        for (c in clean) {
            val value = BASE32_CHARS.indexOf(c)
            if (value < 0) continue // Skip invalid or padding chars '='
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bytes.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return bytes.toByteArray()
    }

    fun generateTotpCode(
        secretBase32: String,
        timeMillis: Long = System.currentTimeMillis(),
        digits: Int = 6,
        periodSeconds: Int = 30
    ): String? {
        if (secretBase32.isBlank()) return null
        return try {
            val keyBytes = decodeBase32(secretBase32)
            if (keyBytes.isEmpty()) return null

            val timeStep = timeMillis / 1000L / periodSeconds

            // 8-byte big-endian time step counter
            val data = ByteArray(8)
            var temp = timeStep
            for (i in 7 downTo 0) {
                data[i] = (temp and 0xFF).toByte()
                temp = temp shr 8
            }

            val mac = Mac.getInstance("HmacSHA1")
            val keySpec = SecretKeySpec(keyBytes, "RAW")
            mac.init(keySpec)
            val hmac = mac.doFinal(data)

            // Dynamic truncation (RFC 4226)
            val offset = (hmac[hmac.size - 1].toInt() and 0x0F)
            val binary = ((hmac[offset].toInt() and 0x7F) shl 24) or
                    ((hmac[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hmac[offset + 2].toInt() and 0xFF) shl 8) or
                    (hmac[offset + 3].toInt() and 0xFF)

            val divisor = 10.0.pow(digits.toDouble()).toInt()
            val otp = binary % divisor
            String.format("%0${digits}d", otp)
        } catch (e: Exception) {
            null
        }
    }

    fun getTotpRemainingSeconds(periodSeconds: Int = 30): Int {
        val currentSec = (System.currentTimeMillis() / 1000L).toInt()
        val elapsed = currentSec % periodSeconds
        return periodSeconds - elapsed
    }

    fun parseTotpUri(uriString: String): Pair<String, String>? {
        val trimmed = uriString.trim()
        if (!trimmed.startsWith("otpauth://", ignoreCase = true)) return null
        return try {
            val queryPart = trimmed.substringAfter("?", "")
            val params = queryPart.split("&").mapNotNull {
                val idx = it.indexOf("=")
                if (idx > 0) it.substring(0, idx) to it.substring(idx + 1) else null
            }.toMap()

            val secret = params["secret"] ?: return null
            val rawLabel = trimmed.substringAfter("otpauth://totp/").substringBefore("?")
            val issuer = params["issuer"] ?: rawLabel
            Pair(secret, issuer)
        } catch (e: Exception) {
            null
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
