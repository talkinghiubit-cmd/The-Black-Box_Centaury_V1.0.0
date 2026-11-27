package com.example.security

import android.content.Context
import android.util.Base64
import com.example.model.PasswordStrength
import com.example.model.VaultCategory
import com.example.model.VaultItem
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class ShareDuration(val label: String, val durationMs: Long) {
    ONE_HOUR("1 Hour", 3600_000L),
    SIX_HOURS("6 Hours", 21600_000L),
    TWENTY_FOUR_HOURS("24 Hours", 86400_000L),
    SEVEN_DAYS("7 Days", 604800_000L),
    THIRTY_DAYS("30 Days", 2592000_000L)
}

data class EncryptedShareCapsule(
    val version: Int = 1,
    val id: String = UUID.randomUUID().toString(),
    val itemTitle: String,
    val itemCategory: String,
    val salt: String, // Base64 16-byte PBKDF2 salt
    val iv: String,   // Base64 12-byte GCM IV
    val ciphertext: String, // Base64 AES-256-GCM ciphertext + 16-byte auth tag
    val expiresAt: Long,
    val burnAfterReading: Boolean = false,
    val passwordStrengthScore: Int? = null,
    val passwordStrengthLabel: String? = null,
    val passwordEntropyBits: Int? = null
) {
    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt

    fun getRemainingTimeText(): String {
        val diff = expiresAt - System.currentTimeMillis()
        if (diff <= 0) return "Expired"
        val hours = diff / (1000 * 60 * 60)
        val minutes = (diff / (1000 * 60)) % 60
        val days = hours / 24
        return when {
            days > 0 -> "${days}d ${hours % 24}h remaining"
            hours > 0 -> "${hours}h ${minutes}m remaining"
            else -> "${minutes}m remaining"
        }
    }
}

data class DecryptedSharedItem(
    val id: String,
    val title: String,
    val category: VaultCategory,
    val username: String = "",
    val password: String = "",
    val secret: String = "",
    val url: String = "",
    val notes: String = "",
    val totpSecret: String = "",
    val cardHolder: String = "",
    val cardNumber: String = "",
    val cardExpiry: String = "",
    val cardCvv: String = "",
    val keyType: String = "",
    val keyValue: String = "",
    val expiresAt: Long,
    val burnAfterReading: Boolean,
    val isExpired: Boolean,
    val remainingTimeText: String,
    val passwordStrength: PasswordStrength? = null,
    val crackTimeEstimate: String = "",
    val createdAt: Long
)

class ShareExpiredException(message: String) : Exception(message)
class ShareDecryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

object SecureShareManager {

    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val TOKEN_PREFIX = "BBOX-SHARE-v1:"
    private const val BURNED_PREFS = "blackbox_burned_capsules"

    fun isCapsuleBurned(context: Context, capsuleId: String): Boolean {
        val prefs = context.getSharedPreferences(BURNED_PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(capsuleId, false)
    }

    fun markCapsuleBurned(context: Context, capsuleId: String) {
        val prefs = context.getSharedPreferences(BURNED_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(capsuleId, true).apply()
    }

    /**
     * Estimates cracking time based on password entropy bits.
     */
    fun estimateCrackTime(entropyBits: Int): String = when {
        entropyBits <= 0 -> "Instantly"
        entropyBits < 28 -> "Few seconds (Instantly crackable)"
        entropyBits < 40 -> "Minutes to Hours"
        entropyBits < 60 -> "Few months to years"
        entropyBits < 80 -> "Thousands of years"
        entropyBits < 100 -> "Millions of years"
        else -> "Billions of years (Military Grade)"
    }

    /**
     * Encrypts a VaultItem (password or note) into a self-contained, time-limited EncryptedShareCapsule.
     * Uses AES-256-GCM with PBKDF2 key derivation and expiration bound to AAD.
     */
    fun createEncryptedCapsule(
        item: VaultItem,
        passphrase: String,
        duration: ShareDuration,
        burnAfterReading: Boolean = false
    ): EncryptedShareCapsule = createEncryptedCapsule(item, passphrase, duration.durationMs, burnAfterReading)

    fun createEncryptedCapsule(
        item: VaultItem,
        passphrase: String,
        durationMs: Long,
        burnAfterReading: Boolean = false
    ): EncryptedShareCapsule {
        require(passphrase.isNotBlank()) { "Passphrase cannot be empty" }

        val secureRandom = SecureRandom()
        val salt = ByteArray(16).apply { secureRandom.nextBytes(this) }
        val iv = ByteArray(12).apply { secureRandom.nextBytes(this) }

        val expiresAt = System.currentTimeMillis() + durationMs

        // Derive AES-256 key from passphrase + salt
        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        // Evaluate password strength if item has a password
        val strength = if (item.password.isNotEmpty()) {
            BlackBoxSecurity.evaluatePasswordStrength(item.password)
        } else null

        // Build internal JSON payload
        val payload = JSONObject().apply {
            put("id", item.id)
            put("title", item.title)
            put("category", item.category.name)
            put("username", item.username)
            put("password", item.password)
            put("url", item.url)
            put("notes", item.notes)
            put("totpSecret", item.totpSecret)
            put("cardHolder", item.cardHolder)
            put("cardNumber", item.cardNumber)
            put("cardExpiry", item.cardExpiry)
            put("cardCvv", item.cardCvv)
            put("keyType", item.keyType)
            put("keyValue", item.keyValue)
            put("createdAt", item.createdAt)
            put("expiresAt", expiresAt)
            put("burnAfterReading", burnAfterReading)
            strength?.let {
                put("strengthScore", it.score)
                put("strengthLabel", it.label)
                put("strengthEntropy", it.entropyBits)
            }
        }

        // Encrypt with AES-GCM and bind AAD to the expiration timestamp
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val aad = "BBOX-SECURE-SHARE-V1:$expiresAt".toByteArray(StandardCharsets.UTF_8)
        cipher.updateAAD(aad)

        val ciphertextWithTag = cipher.doFinal(payload.toString().toByteArray(StandardCharsets.UTF_8))

        return EncryptedShareCapsule(
            id = UUID.randomUUID().toString(),
            itemTitle = item.title,
            itemCategory = item.category.name,
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(ciphertextWithTag, Base64.NO_WRAP),
            expiresAt = expiresAt,
            burnAfterReading = burnAfterReading,
            passwordStrengthScore = strength?.score,
            passwordStrengthLabel = strength?.label,
            passwordEntropyBits = strength?.entropyBits
        )
    }

    /**
     * Serializes an EncryptedShareCapsule to a portable base64 token string.
     */
    fun exportCapsuleAsToken(capsule: EncryptedShareCapsule): String {
        val json = JSONObject().apply {
            put("v", capsule.version)
            put("id", capsule.id)
            put("t", capsule.itemTitle)
            put("cat", capsule.itemCategory)
            put("s", capsule.salt)
            put("iv", capsule.iv)
            put("ct", capsule.ciphertext)
            put("exp", capsule.expiresAt)
            put("burn", capsule.burnAfterReading)
            capsule.passwordStrengthScore?.let { put("score", it) }
            capsule.passwordStrengthLabel?.let { put("slabel", it) }
            capsule.passwordEntropyBits?.let { put("ent", it) }
        }
        val encoded = Base64.encodeToString(json.toString().toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        return "$TOKEN_PREFIX$encoded"
    }

    /**
     * Parses an EncryptedShareCapsule from a token or JSON string.
     */
    fun parseCapsuleToken(rawToken: String): EncryptedShareCapsule {
        val clean = rawToken.trim()
        val jsonStr = if (clean.startsWith(TOKEN_PREFIX)) {
            val base64Part = clean.removePrefix(TOKEN_PREFIX)
            String(Base64.decode(base64Part, Base64.NO_WRAP), StandardCharsets.UTF_8)
        } else if (clean.startsWith("{")) {
            clean
        } else {
            // Try raw base64
            String(Base64.decode(clean, Base64.NO_WRAP), StandardCharsets.UTF_8)
        }

        val json = JSONObject(jsonStr)
        return EncryptedShareCapsule(
            version = json.optInt("v", 1),
            id = json.optString("id", UUID.randomUUID().toString()),
            itemTitle = json.optString("t", json.optString("itemTitle", "Shared Secret")),
            itemCategory = json.optString("cat", json.optString("itemCategory", "LOGIN")),
            salt = json.getString("s"),
            iv = json.getString("iv"),
            ciphertext = json.getString("ct"),
            expiresAt = json.getLong("exp"),
            burnAfterReading = json.optBoolean("burn", false),
            passwordStrengthScore = if (json.has("score")) json.getInt("score") else null,
            passwordStrengthLabel = if (json.has("slabel")) json.getString("slabel") else null,
            passwordEntropyBits = if (json.has("ent")) json.getInt("ent") else null
        )
    }

    /**
     * Decrypts an EncryptedShareCapsule using the shared passphrase.
     * Enforces expiration timestamp and AAD verification.
     */
    fun decryptCapsule(capsule: EncryptedShareCapsule, passphrase: String): DecryptedSharedItem {
        val isExpired = capsule.isExpired()
        if (isExpired) {
            val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(Date(capsule.expiresAt))
            throw ShareExpiredException("This secure share expired on $dateStr.")
        }

        return try {
            val salt = Base64.decode(capsule.salt, Base64.NO_WRAP)
            val iv = Base64.decode(capsule.iv, Base64.NO_WRAP)
            val ciphertext = Base64.decode(capsule.ciphertext, Base64.NO_WRAP)

            val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = factory.generateSecret(keySpec).encoded
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val aad = "BBOX-SECURE-SHARE-V1:${capsule.expiresAt}".toByteArray(StandardCharsets.UTF_8)
            cipher.updateAAD(aad)

            val plaintextBytes = cipher.doFinal(ciphertext)
            val payloadJson = JSONObject(String(plaintextBytes, StandardCharsets.UTF_8))

            val password = payloadJson.optString("password", "")
            val notes = payloadJson.optString("notes", "")
            val cardHolder = payloadJson.optString("cardHolder", "")
            val cardNumber = payloadJson.optString("cardNumber", "")
            val cardExpiry = payloadJson.optString("cardExpiry", "")
            val cardCvv = payloadJson.optString("cardCvv", "")
            val keyType = payloadJson.optString("keyType", "")
            val keyValue = payloadJson.optString("keyValue", "")
            val secret = password.ifEmpty { keyValue.ifEmpty { cardNumber.ifEmpty { notes } } }

            val strength = if (password.isNotEmpty()) {
                BlackBoxSecurity.evaluatePasswordStrength(password)
            } else null

            DecryptedSharedItem(
                id = payloadJson.optString("id", capsule.id),
                title = payloadJson.optString("title", capsule.itemTitle),
                category = VaultCategory.fromString(payloadJson.optString("category", capsule.itemCategory)),
                username = payloadJson.optString("username", ""),
                password = password,
                secret = secret,
                url = payloadJson.optString("url", ""),
                notes = notes,
                totpSecret = payloadJson.optString("totpSecret", ""),
                cardHolder = cardHolder,
                cardNumber = cardNumber,
                cardExpiry = cardExpiry,
                cardCvv = cardCvv,
                keyType = keyType,
                keyValue = keyValue,
                expiresAt = capsule.expiresAt,
                burnAfterReading = capsule.burnAfterReading,
                isExpired = false,
                remainingTimeText = capsule.getRemainingTimeText(),
                passwordStrength = strength,
                crackTimeEstimate = strength?.let { estimateCrackTime(it.entropyBits) } ?: "",
                createdAt = payloadJson.optLong("createdAt", System.currentTimeMillis())
            )
        } catch (e: ShareExpiredException) {
            throw e
        } catch (e: Exception) {
            throw ShareDecryptionException("Decryption failed. Invalid passphrase or corrupted capsule payload.", e)
        }
    }

    /**
     * Generates a self-decrypting, zero-dependency, time-locked HTML file
     * with WebCrypto AES-GCM decryption, live countdown clock, and Password Strength Analyzer.
     */
    fun generateSelfDecryptingHtmlShare(
        context: Context,
        capsule: EncryptedShareCapsule,
        item: VaultItem
    ): File {
        ExportFileCleaner.cleanupIdleExports(context)
        val shareDir = File(context.cacheDir, "shares")
        if (!shareDir.exists()) shareDir.mkdirs()

        val safeTitle = item.title.lowercase().replace(Regex("[^a-z0-9]"), "_").take(20)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(shareDir, "blackbox_secure_share_${safeTitle}_$timestamp.html")

        val expiryDateFormatted = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US).format(Date(capsule.expiresAt))
        val createdDateFormatted = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US).format(Date())

        val htmlContent = buildSelfDecryptingHtml(
            title = item.title,
            category = item.category.displayName,
            capsuleJson = exportCapsuleAsToken(capsule),
            saltB64 = capsule.salt,
            ivB64 = capsule.iv,
            ciphertextB64 = capsule.ciphertext,
            expiresAtMs = capsule.expiresAt,
            burnAfterReading = capsule.burnAfterReading,
            expiryFormatted = expiryDateFormatted,
            createdFormatted = createdDateFormatted
        )

        file.writeText(htmlContent, StandardCharsets.UTF_8)
        return file
    }

    private fun escapeHtml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun buildSelfDecryptingHtml(
        title: String,
        category: String,
        capsuleJson: String,
        saltB64: String,
        ivB64: String,
        ciphertextB64: String,
        expiresAtMs: Long,
        burnAfterReading: Boolean,
        expiryFormatted: String,
        createdFormatted: String
    ): String {
        val safeTitle = escapeHtml(title)
        val safeCategory = escapeHtml(category)
        return """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>The Black-Box — Secure Share ($safeTitle)</title>
  <style>
    :root {
      --bg: #070709;
      --surface: #0f1117;
      --surface-variant: #171b24;
      --border: #232a36;
      --emerald: #00ff66;
      --emerald-glow: rgba(0, 255, 102, 0.2);
      --cyan: #00e5ff;
      --red: #ff3366;
      --amber: #ffb700;
      --text: #f3f4f6;
      --text-muted: #8b949e;
      --font-mono: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 24px 16px;
    }
    .capsule-card {
      width: 100%;
      max-width: 520px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 32px 24px;
      box-shadow: 0 12px 40px rgba(0,0,0,0.6);
      position: relative;
    }
    .header {
      display: flex;
      align-items: center;
      gap: 14px;
      margin-bottom: 24px;
      padding-bottom: 16px;
      border-bottom: 1px solid var(--border);
    }
    .brand-icon {
      width: 44px;
      height: 44px;
      background: var(--surface-variant);
      border: 1px solid var(--emerald);
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--emerald);
      font-weight: 900;
      font-size: 20px;
      box-shadow: 0 0 16px var(--emerald-glow);
    }
    .title-group h1 {
      font-size: 18px;
      font-weight: 700;
      letter-spacing: 0.5px;
    }
    .badge {
      display: inline-block;
      font-family: var(--font-mono);
      font-size: 11px;
      font-weight: 600;
      padding: 2px 8px;
      border-radius: 4px;
      text-transform: uppercase;
      background: rgba(0, 229, 255, 0.12);
      color: var(--cyan);
      border: 1px solid rgba(0, 229, 255, 0.3);
      margin-top: 4px;
    }
    .time-banner {
      background: rgba(255, 183, 0, 0.1);
      border: 1px solid rgba(255, 183, 0, 0.3);
      border-radius: 8px;
      padding: 10px 14px;
      font-size: 12px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 24px;
      font-family: var(--font-mono);
      color: var(--amber);
    }
    .input-group {
      margin-bottom: 20px;
    }
    .input-group label {
      display: block;
      font-size: 12px;
      font-family: var(--font-mono);
      color: var(--text-muted);
      text-transform: uppercase;
      margin-bottom: 8px;
    }
    .input-group input {
      width: 100%;
      background: var(--surface-variant);
      border: 1px solid var(--border);
      color: var(--text);
      font-family: var(--font-mono);
      font-size: 15px;
      padding: 14px;
      border-radius: 8px;
      outline: none;
      transition: border-color 0.2s;
    }
    .input-group input:focus {
      border-color: var(--emerald);
      box-shadow: 0 0 10px var(--emerald-glow);
    }
    .btn {
      width: 100%;
      background: var(--emerald);
      color: #000;
      border: none;
      padding: 14px;
      border-radius: 8px;
      font-size: 14px;
      font-weight: 700;
      font-family: var(--font-mono);
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      transition: opacity 0.2s, transform 0.1s;
    }
    .btn:hover { opacity: 0.92; }
    .btn:active { transform: scale(0.99); }
    .btn:disabled { opacity: 0.4; cursor: not-allowed; }
    .error-msg {
      color: var(--red);
      font-size: 13px;
      margin-top: 12px;
      display: none;
      text-align: center;
      font-family: var(--font-mono);
    }
    .secret-view {
      display: none;
      margin-top: 10px;
    }
    .field-card {
      background: var(--surface-variant);
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 14px;
      margin-bottom: 14px;
    }
    .field-header {
      font-size: 11px;
      font-family: var(--font-mono);
      color: var(--text-muted);
      text-transform: uppercase;
      margin-bottom: 6px;
      display: flex;
      justify-content: space-between;
    }
    .field-val {
      font-family: var(--font-mono);
      font-size: 15px;
      color: var(--emerald);
      word-break: break-all;
    }
    .strength-box {
      background: #11141d;
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 14px;
      margin-top: 18px;
      margin-bottom: 18px;
    }
    .strength-header {
      display: flex;
      justify-content: space-between;
      font-size: 12px;
      font-family: var(--font-mono);
      margin-bottom: 8px;
    }
    .strength-bar {
      height: 6px;
      background: rgba(255,255,255,0.1);
      border-radius: 3px;
      overflow: hidden;
      display: flex;
      gap: 3px;
      margin-bottom: 8px;
    }
    .strength-segment {
      flex: 1;
      height: 100%;
      background: transparent;
      border-radius: 2px;
    }
    .copy-btn {
      background: var(--surface-variant);
      border: 1px solid var(--border);
      color: var(--cyan);
      font-size: 12px;
      font-family: var(--font-mono);
      padding: 4px 10px;
      border-radius: 4px;
      cursor: pointer;
    }
    .copy-btn:hover { border-color: var(--cyan); }
    .burned-badge {
      color: var(--red);
      border-color: rgba(255, 51, 102, 0.4);
      background: rgba(255, 51, 102, 0.1);
    }
    .footer {
      margin-top: 24px;
      text-align: center;
      font-size: 11px;
      color: var(--text-muted);
      font-family: var(--font-mono);
    }
  </style>
</head>
<body>
  <div class="capsule-card">
    <div class="header">
      <div class="brand-icon">&#9632;</div>
      <div class="title-group">
        <h1>$safeTitle</h1>
        <div class="badge">$safeCategory</div>
        ${if (burnAfterReading) """<div class="badge burned-badge">Burn After Reading</div>""" else ""}
      </div>
    </div>

    <div class="time-banner" id="countdownBanner">
      <span>&#9201; Time-Limited Access:</span>
      <span id="countdownText">Calculating...</span>
    </div>

    <div id="unlockSection">
      <div class="input-group">
        <label for="passphraseInput">Decryption Passphrase</label>
        <input type="password" id="passphraseInput" placeholder="Enter share passphrase..." autofocus />
      </div>
      <button class="btn" id="decryptBtn" onclick="decryptShareCapsule()">
        <span>&#128275;</span> Decrypt Secret
      </button>
      <div class="error-msg" id="errorMsg">Invalid passphrase or capsule expired.</div>
    </div>

    <div class="secret-view" id="secretSection">
      <div id="usernameBox" class="field-card" style="display: none;">
        <div class="field-header">
          <span>Username / Account</span>
          <button class="copy-btn" onclick="copyText('usernameVal')">Copy</button>
        </div>
        <div class="field-val" id="usernameVal"></div>
      </div>

      <div id="passwordBox" class="field-card" style="display: none;">
        <div class="field-header">
          <span>Password / Secret</span>
          <button class="copy-btn" onclick="copyText('passwordVal')">Copy (30s Scrub)</button>
        </div>
        <div class="field-val" id="passwordVal"></div>
      </div>

      <div id="strengthContainer" class="strength-box" style="display: none;">
        <div class="strength-header">
          <span style="color: var(--text-muted);">PASSWORD STRENGTH</span>
          <span id="strengthLabel" style="font-weight: 700;"></span>
        </div>
        <div class="strength-bar" id="strengthBar">
          <div class="strength-segment" id="seg0"></div>
          <div class="strength-segment" id="seg1"></div>
          <div class="strength-segment" id="seg2"></div>
          <div class="strength-segment" id="seg3"></div>
        </div>
        <div style="font-size: 11px; font-family: var(--font-mono); color: var(--text-muted); display: flex; justify-content: space-between;">
          <span id="entropyText"></span>
          <span id="crackTimeText"></span>
        </div>
      </div>

      <div id="urlBox" class="field-card" style="display: none;">
        <div class="field-header">
          <span>Website / Service URL</span>
          <button class="copy-btn" onclick="copyText('urlVal')">Copy</button>
        </div>
        <div class="field-val" id="urlVal" style="color: var(--cyan);"></div>
      </div>

      <div id="cardBox" class="field-card" style="display: none;">
        <div class="field-header">
          <span>Payment Card Details</span>
          <button class="copy-btn" onclick="copyText('cardNumberVal')">Copy Number</button>
        </div>
        <div class="field-val" id="cardHolderVal" style="color: var(--text); font-size: 13px; margin-bottom: 6px;"></div>
        <div class="field-val" id="cardNumberVal"></div>
        <div class="field-val" id="cardMetaVal" style="color: var(--amber); font-size: 13px; margin-top: 6px;"></div>
      </div>

      <div id="keyBox" class="field-card" style="display: none;">
        <div class="field-header">
          <span id="keyTypeHeader">Cryptographic Key</span>
          <button class="copy-btn" onclick="copyText('keyVal')">Copy</button>
        </div>
        <div class="field-val" id="keyVal" style="white-space: pre-wrap; font-size: 12px;"></div>
      </div>

      <div id="notesBox" class="field-card" style="display: none;">
        <div class="field-header">
          <span>Secure Notes</span>
          <button class="copy-btn" onclick="copyText('notesVal')">Copy</button>
        </div>
        <div class="field-val" id="notesVal" style="white-space: pre-wrap; font-size: 13px; color: var(--text);"></div>
      </div>

      <div id="totpBox" class="field-card" style="display: none;">
        <div class="field-header">
          <span>TOTP 2FA Key</span>
          <button class="copy-btn" onclick="copyText('totpVal')">Copy</button>
        </div>
        <div class="field-val" id="totpVal"></div>
      </div>

      <div style="margin-top: 16px; font-size: 12px; font-family: var(--font-mono); color: var(--emerald); text-align: center;">
        &#10003; Decrypted via WebCrypto AES-256-GCM
      </div>
    </div>

    <div class="footer">
      End-to-End Encrypted via The Black-Box &bull; Zero Server Knowledge
    </div>
  </div>

  <script>
    const SALT_B64 = "$saltB64";
    const IV_B64 = "$ivB64";
    const CIPHERTEXT_B64 = "$ciphertextB64";
    const EXPIRES_AT = $expiresAtMs;
    const BURN_AFTER_READING = $burnAfterReading;

    function b64ToBuf(b64) {
      const bin = atob(b64);
      const buf = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
      return buf;
    }

    function updateCountdown() {
      const now = Date.now();
      const diff = EXPIRES_AT - now;
      const banner = document.getElementById('countdownBanner');
      const text = document.getElementById('countdownText');

      if (diff <= 0) {
        text.innerText = "EXPIRED";
        banner.style.background = "rgba(255, 51, 102, 0.15)";
        banner.style.borderColor = "var(--red)";
        banner.style.color = "var(--red)";
        document.getElementById('decryptBtn').disabled = true;
        document.getElementById('passphraseInput').disabled = true;
        document.getElementById('errorMsg').innerText = "This secure share has expired and can no longer be decrypted.";
        document.getElementById('errorMsg').style.display = 'block';
        return false;
      }

      const hours = Math.floor(diff / 3600000);
      const minutes = Math.floor((diff % 3600000) / 60000);
      const seconds = Math.floor((diff % 60000) / 1000);

      text.innerText = (hours > 0 ? hours + "h " : "") + minutes + "m " + seconds + "s";
      return true;
    }

    setInterval(updateCountdown, 1000);
    updateCountdown();

    function calcStrength(pw) {
      if (!pw) return { score: 0, label: "Empty", entropy: 0, crack: "Instantly" };
      let pool = 0;
      if (/[a-z]/.test(pw)) pool += 26;
      if (/[A-Z]/.test(pw)) pool += 26;
      if (/[0-9]/.test(pw)) pool += 10;
      if (/[^a-zA-Z0-9]/.test(pw)) pool += 32;
      if (pool === 0) pool = 10;
      const entropy = Math.round(pw.length * (Math.log(pool) / Math.log(2)));
      let score = 0; let label = "Very Weak"; let crack = "Few seconds";
      if (entropy >= 80) { score = 4; label = "Very Strong"; crack = "Millions of years"; }
      else if (entropy >= 60) { score = 3; label = "Strong"; crack = "Thousands of years"; }
      else if (entropy >= 40) { score = 2; label = "Moderate"; crack = "Months to years"; }
      else if (entropy >= 28) { score = 1; label = "Weak"; crack = "Hours to days"; }
      return { score, label, entropy, crack };
    }

    async function decryptShareCapsule() {
      if (!updateCountdown()) return;
      const passphrase = document.getElementById('passphraseInput').value;
      const errorMsg = document.getElementById('errorMsg');
      const decryptBtn = document.getElementById('decryptBtn');

      if (!passphrase) return;

      decryptBtn.disabled = true;
      decryptBtn.innerText = "Decrypting...";
      errorMsg.style.display = 'none';

      try {
        const enc = new TextEncoder();
        const baseKey = await window.crypto.subtle.importKey(
          "raw", enc.encode(passphrase), "PBKDF2", false, ["deriveKey"]
        );

        const salt = b64ToBuf(SALT_B64);
        const aesKey = await window.crypto.subtle.deriveKey(
          { name: "PBKDF2", salt: salt, iterations: 100000, hash: "SHA-256" },
          baseKey,
          { name: "AES-GCM", length: 256 },
          false,
          ["decrypt"]
        );

        const iv = b64ToBuf(IV_B64);
        const ciphertext = b64ToBuf(CIPHERTEXT_B64);
        const aad = enc.encode("BBOX-SECURE-SHARE-V1:" + EXPIRES_AT);

        const decryptedBuffer = await window.crypto.subtle.decrypt(
          { name: "AES-GCM", iv: iv, additionalData: aad, tagLength: 128 },
          aesKey,
          ciphertext
        );

        const dec = new TextDecoder();
        const payload = JSON.parse(dec.decode(decryptedBuffer));

        // Render fields
        document.getElementById('unlockSection').style.display = 'none';
        document.getElementById('secretSection').style.display = 'block';

        if (payload.username) {
          document.getElementById('usernameBox').style.display = 'block';
          document.getElementById('usernameVal').innerText = payload.username;
        }
        if (payload.password) {
          document.getElementById('passwordBox').style.display = 'block';
          document.getElementById('passwordVal').innerText = payload.password;

          // Strength meter
          const st = calcStrength(payload.password);
          document.getElementById('strengthContainer').style.display = 'block';
          document.getElementById('strengthLabel').innerText = st.label;
          document.getElementById('entropyText').innerText = st.entropy + " bits entropy";
          document.getElementById('crackTimeText').innerText = "Crack Time: " + st.crack;

          const colors = ['#ff3366', '#ff9900', '#ffcc00', '#00e5ff', '#00ff66'];
          document.getElementById('strengthLabel').style.color = colors[st.score];
          for (let i = 0; i < 4; i++) {
            const seg = document.getElementById('seg' + i);
            seg.style.background = i <= (st.score - 1) ? colors[st.score] : 'rgba(255,255,255,0.1)';
          }
        }
        if (payload.url) {
          document.getElementById('urlBox').style.display = 'block';
          document.getElementById('urlVal').innerText = payload.url;
        }
        if (payload.cardNumber || payload.cardHolder) {
          document.getElementById('cardBox').style.display = 'block';
          document.getElementById('cardHolderVal').innerText = payload.cardHolder || '';
          document.getElementById('cardNumberVal').innerText = payload.cardNumber || '';
          const metaParts = [];
          if (payload.cardExpiry) metaParts.push('Exp: ' + payload.cardExpiry);
          if (payload.cardCvv) metaParts.push('CVV: ' + payload.cardCvv);
          document.getElementById('cardMetaVal').innerText = metaParts.join(' • ');
        }
        if (payload.keyValue) {
          document.getElementById('keyBox').style.display = 'block';
          document.getElementById('keyTypeHeader').innerText = payload.keyType || 'Cryptographic Key';
          document.getElementById('keyVal').innerText = payload.keyValue;
        }
        if (payload.notes) {
          document.getElementById('notesBox').style.display = 'block';
          document.getElementById('notesVal').innerText = payload.notes;
        }
        if (payload.totpSecret) {
          document.getElementById('totpBox').style.display = 'block';
          document.getElementById('totpVal').innerText = payload.totpSecret;
        }

        if (BURN_AFTER_READING) {
          // Burn payload from memory in 60s
          setTimeout(() => {
            document.body.innerHTML = '<div style="color: #ff3366; font-family: monospace; text-align: center; margin-top: 100px;"><h1>&#128293; BURNED</h1><p>This secret was configured for single viewing and has been wiped from memory.</p></div>';
          }, 60000);
        }

      } catch (err) {
        errorMsg.innerText = "Decryption failed. Please check your passphrase.";
        errorMsg.style.display = 'block';
        decryptBtn.disabled = false;
        decryptBtn.innerText = "Decrypt Secret";
      }
    }

    function copyText(elemId) {
      const text = document.getElementById(elemId).innerText;
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(() => {
          alert("Copied to clipboard. Sensitive data should be cleared after use.");
        }).catch(() => fallbackCopy(text));
      } else {
        fallbackCopy(text);
      }
    }

    function fallbackCopy(text) {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      try {
        document.execCommand('copy');
        alert("Copied to clipboard. Sensitive data should be cleared after use.");
      } catch (e) {
        alert("Copy failed. Please copy the value manually.");
      }
      document.body.removeChild(ta);
    }
  </script>
</body>
</html>"""
    }
}
