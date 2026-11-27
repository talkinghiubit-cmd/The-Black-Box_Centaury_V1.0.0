package com.example.security

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

enum class TotpAlgorithm(
    val uriParam: String,
    val hmacName: String,
    val displayName: String
) {
    SHA1("SHA1", "HmacSHA1", "SHA-1"),
    SHA256("SHA256", "HmacSHA256", "SHA-256"),
    SHA512("SHA512", "HmacSHA512", "SHA-512");

    companion object {
        fun fromParam(raw: String?): TotpAlgorithm? {
            if (raw.isNullOrBlank()) return SHA1
            return when (raw.trim().uppercase(Locale.US).replace("-", "")) {
                "SHA1", "HMACSHA1" -> SHA1
                "SHA256", "HMACSHA256" -> SHA256
                "SHA512", "HMACSHA512" -> SHA512
                else -> null
            }
        }
    }
}

data class TotpConfig(
    val secret: String,
    val issuer: String = "",
    val accountName: String = "",
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = 6,
    val periodSeconds: Int = 30
) {
    val hasExtendedMetadata: Boolean
        get() = issuer.isNotBlank() ||
            accountName.isNotBlank() ||
            algorithm != TotpAlgorithm.SHA1 ||
            digits != 6 ||
            periodSeconds != 30
}

sealed class TotpSeedValidationResult {
    data class Valid(
        val config: TotpConfig,
        val normalizedSeed: String,
        val storableValue: String,
        val isUri: Boolean
    ) : TotpSeedValidationResult()

    data class Invalid(val reason: String) : TotpSeedValidationResult()
}

data class TotpToken(
    val code: String,
    val formattedCode: String,
    val remainingSeconds: Int,
    val periodSeconds: Int,
    val validUntilEpochMillis: Long,
    val config: TotpConfig
)

data class TotpValidationResult(
    val isValid: Boolean,
    val matchedStepOffset: Int? = null,
    val matchedCode: String? = null,
    val message: String
)

/**
 * Full Google Authenticator Key Uri (`otpauth://totp/...`) and RFC 6238 / RFC 4226 TOTP engine.
 *
 * Supports:
 * - Strict RFC 4648 Base32 normalization, validation, and decoding
 * - HmacSHA1 (default), HmacSHA256, and HmacSHA512
 * - 6-digit (default) and 8-digit OTP lengths
 * - Configurable time step periods (default 30s)
 * - Standard `otpauth://totp/` URI parsing and building
 * - Constant-time verification with configurable clock-drift tolerance (±1 step default)
 */
object TotpAuthenticatorService {

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    const val DEFAULT_DIGITS = 6
    const val DEFAULT_PERIOD_SECONDS = 30
    const val MIN_SECRET_BYTES = 10 // 80-bit minimum (e.g. 16 Base32 chars like JBSWY3DPEHPK3PXP)
    const val MAX_DRIFT_STEPS = 2

    private val secureRandom = SecureRandom()

    fun normalizeBase32(raw: String): String {
        return raw.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("\n", "")
            .replace("\r", "")
            .uppercase(Locale.US)
            .trimEnd('=')
    }

    /**
     * Strictly decodes an RFC 4648 Base32 string.
     * Returns null if any non-Base32 characters (including embedded `=`) are present or if length is invalid.
     */
    fun decodeBase32Strict(base32: String, minBytes: Int = MIN_SECRET_BYTES): ByteArray? {
        val cleaned = base32.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("\n", "")
            .replace("\r", "")
            .uppercase(Locale.US)

        if (cleaned.isEmpty()) return null

        // Only trailing '=' padding is valid in RFC 4648 Base32
        val unpadded = cleaned.trimEnd('=')
        if (unpadded.isEmpty() || unpadded.contains('=')) return null

        // RFC 4648 impossible unpadded lengths mod 8: 1 (5 bits), 3 (15 bits), 6 (30 bits)
        if (unpadded.length % 8 in setOf(1, 3, 6)) return null

        val output = ByteArray((unpadded.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0
        var outIndex = 0

        for (c in unpadded) {
            val value = BASE32_ALPHABET.indexOf(c)
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                output[outIndex++] = ((buffer shr (bitsLeft - 8)) and 0xFF).toByte()
                bitsLeft -= 8
            }
        }

        if (output.size < minBytes) return null
        return output
    }

    fun encodeBase32(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder(((bytes.size * 8) + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                sb.append(BASE32_ALPHABET[index])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            sb.append(BASE32_ALPHABET[index])
        }
        return sb.toString()
    }

    /**
     * Generates a cryptographically random Base32 TOTP secret (default 20 bytes / 160 bits).
     */
    fun generateRandomSecret(numBytes: Int = 20): String {
        val bytes = ByteArray(numBytes.coerceAtLeast(MIN_SECRET_BYTES))
        secureRandom.nextBytes(bytes)
        return encodeBase32(bytes)
    }

    /**
     * Parses a Google Authenticator `otpauth://totp/...` Key URI into a validated [TotpConfig].
     */
    fun parseOtpAuthUri(uriString: String): Result<TotpConfig> {
        val trimmed = uriString.trim()
        if (!trimmed.startsWith("otpauth://", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("URI must begin with otpauth://"))
        }

        val afterScheme = trimmed.substring("otpauth://".length)
        val typePart = afterScheme.substringBefore("/").substringBefore("?").lowercase(Locale.US)
        if (typePart == "hotp") {
            return Result.failure(IllegalArgumentException("HOTP counter-based URIs are not supported; use otpauth://totp/"))
        }
        if (typePart != "totp") {
            return Result.failure(IllegalArgumentException("Unsupported otpauth type '$typePart'; expected 'totp'"))
        }

        val pathAndQuery = afterScheme.substringAfter("totp", "")
        if (!pathAndQuery.startsWith("/") && !pathAndQuery.startsWith("?")) {
            return Result.failure(IllegalArgumentException("Malformed otpauth://totp URI"))
        }

        val rawPath = pathAndQuery.removePrefix("/").substringBefore("?")
        val decodedLabel = percentDecode(rawPath)
        val labelIssuer: String
        val accountName: String
        val colonIdx = decodedLabel.indexOf(':')
        if (colonIdx >= 0) {
            labelIssuer = decodedLabel.substring(0, colonIdx).trim()
            accountName = decodedLabel.substring(colonIdx + 1).trim()
        } else {
            labelIssuer = ""
            accountName = decodedLabel.trim()
        }

        val queryPart = pathAndQuery.substringAfter("?", "")
        if (queryPart.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing query parameters in otpauth URI"))
        }

        val params = LinkedHashMap<String, String>()
        for (pair in queryPart.split("&")) {
            if (pair.isBlank()) continue
            val eqIdx = pair.indexOf('=')
            if (eqIdx <= 0) continue
            val key = percentDecode(pair.substring(0, eqIdx)).lowercase(Locale.US)
            val value = percentDecode(pair.substring(eqIdx + 1))
            params[key] = value
        }

        val rawSecret = params["secret"]
            ?: return Result.failure(IllegalArgumentException("Missing required 'secret' parameter"))
        val normalizedSecret = normalizeBase32(rawSecret)
        if (decodeBase32Strict(normalizedSecret) == null) {
            return Result.failure(IllegalArgumentException("Invalid Base32 'secret' parameter in otpauth URI"))
        }

        val algorithmParam = params["algorithm"]
        val algorithm = TotpAlgorithm.fromParam(algorithmParam)
            ?: return Result.failure(IllegalArgumentException("Unsupported algorithm '$algorithmParam'. Supported: SHA1, SHA256, SHA512"))

        val digits = params["digits"]?.let { rawDigits ->
            val parsed = rawDigits.toIntOrNull()
            if (parsed != 6 && parsed != 8) {
                return Result.failure(IllegalArgumentException("Unsupported digits '$rawDigits'. Google Authenticator supports 6 or 8 digits"))
            }
            parsed
        } ?: DEFAULT_DIGITS

        val periodSeconds = params["period"]?.let { rawPeriod ->
            val parsed = rawPeriod.toIntOrNull()
            if (parsed == null || parsed !in 1..300) {
                return Result.failure(IllegalArgumentException("Invalid period '$rawPeriod'. Expected 1–300 seconds"))
            }
            parsed
        } ?: DEFAULT_PERIOD_SECONDS

        val paramIssuer = params["issuer"]?.trim().orEmpty()
        val effectiveIssuer = paramIssuer.ifBlank { labelIssuer }

        return Result.success(
            TotpConfig(
                secret = normalizedSecret,
                issuer = effectiveIssuer,
                accountName = accountName,
                algorithm = algorithm,
                digits = digits,
                periodSeconds = periodSeconds
            )
        )
    }

    /**
     * Builds a canonical Google Authenticator `otpauth://totp/...` URI from [TotpConfig].
     */
    fun buildOtpAuthUri(config: TotpConfig): String {
        val cleanSecret = normalizeBase32(config.secret)
        val cleanIssuer = config.issuer.trim()
        val cleanAccount = config.accountName.trim()

        val label = when {
            cleanIssuer.isNotEmpty() && cleanAccount.isNotEmpty() ->
                "${percentEncode(cleanIssuer)}:${percentEncode(cleanAccount)}"
            cleanAccount.isNotEmpty() -> percentEncode(cleanAccount)
            cleanIssuer.isNotEmpty() -> percentEncode(cleanIssuer)
            else -> "Account"
        }

        val queryParts = mutableListOf("secret=${percentEncode(cleanSecret)}")
        if (cleanIssuer.isNotEmpty()) {
            queryParts.add("issuer=${percentEncode(cleanIssuer)}")
        }
        queryParts.add("algorithm=${config.algorithm.uriParam}")
        queryParts.add("digits=${config.digits}")
        queryParts.add("period=${config.periodSeconds}")

        return "otpauth://totp/$label?${queryParts.joinToString("&")}"
    }

    /**
     * Parses either an `otpauth://totp/...` URI or a raw Base32 secret string.
     */
    fun parseStoredSeedOrUri(input: String): Result<TotpConfig> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("2FA seed is empty"))
        }
        if (trimmed.startsWith("otpauth://", ignoreCase = true)) {
            return parseOtpAuthUri(trimmed)
        }
        val keyBytes = decodeBase32Strict(trimmed)
            ?: return Result.failure(
                IllegalArgumentException("Invalid Base32 seed (use A–Z, 2–7, at least 16 characters)")
            )
        if (keyBytes.size < MIN_SECRET_BYTES) {
            return Result.failure(
                IllegalArgumentException("2FA seed is too short (minimum 80 bits / 16 Base32 characters)")
            )
        }
        return Result.success(TotpConfig(secret = normalizeBase32(trimmed)))
    }

    /**
     * Validates user input (Base32 or `otpauth://totp/...`) and produces a canonical storable string.
     * - Plain Base32 secrets store as normalized uppercase Base32.
     * - Google Authenticator `otpauth://` URIs preserve issuer, account, algorithm, digits, and period.
     */
    fun validateSeedInput(input: String): TotpSeedValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return TotpSeedValidationResult.Invalid("2FA seed is empty")
        }
        val isUri = trimmed.startsWith("otpauth://", ignoreCase = true)
        return parseStoredSeedOrUri(trimmed).fold(
            onSuccess = { config ->
                val storable = if (isUri || config.hasExtendedMetadata) {
                    buildOtpAuthUri(config)
                } else {
                    config.secret
                }
                TotpSeedValidationResult.Valid(
                    config = config,
                    normalizedSeed = config.secret,
                    storableValue = storable,
                    isUri = isUri
                )
            },
            onFailure = { error ->
                TotpSeedValidationResult.Invalid(error.message ?: "Invalid 2FA TOTP configuration")
            }
        )
    }

    fun toStorableValue(input: String): Result<String> {
        return when (val validation = validateSeedInput(input)) {
            is TotpSeedValidationResult.Valid -> Result.success(validation.storableValue)
            is TotpSeedValidationResult.Invalid -> Result.failure(IllegalArgumentException(validation.reason))
        }
    }

    /**
     * Generates a formatted + raw [TotpToken] for any stored Base32 seed or `otpauth://totp/...` URI.
     */
    fun generateToken(
        storedSeedOrUri: String,
        timeMillis: Long = System.currentTimeMillis()
    ): TotpToken? {
        if (storedSeedOrUri.isBlank()) return null
        val config = parseStoredSeedOrUri(storedSeedOrUri).getOrNull() ?: return null
        val code = generateCode(config, timeMillis) ?: return null
        val remaining = getRemainingSeconds(config.periodSeconds, timeMillis)
        val epochSec = Math.floorDiv(timeMillis, 1000L)
        val step = Math.floorDiv(epochSec, config.periodSeconds.toLong())
        val validUntilMillis = (step + 1L) * config.periodSeconds.toLong() * 1000L

        return TotpToken(
            code = code,
            formattedCode = formatCode(code),
            remainingSeconds = remaining,
            periodSeconds = config.periodSeconds,
            validUntilEpochMillis = validUntilMillis,
            config = config
        )
    }

    fun generateCode(
        storedSeedOrUri: String,
        timeMillis: Long = System.currentTimeMillis(),
        digitsOverride: Int? = null,
        periodSecondsOverride: Int? = null,
        algorithmOverride: TotpAlgorithm? = null
    ): String? {
        if (storedSeedOrUri.isBlank()) return null
        val config = parseStoredSeedOrUri(storedSeedOrUri).getOrNull() ?: return null
        val effectiveConfig = config.copy(
            digits = digitsOverride ?: config.digits,
            periodSeconds = periodSecondsOverride ?: config.periodSeconds,
            algorithm = algorithmOverride ?: config.algorithm
        )
        return generateCode(effectiveConfig, timeMillis)
    }

    fun generateCode(
        config: TotpConfig,
        timeMillis: Long = System.currentTimeMillis()
    ): String? {
        val keyBytes = decodeBase32Strict(config.secret) ?: return null
        return generateCodeForKeyBytes(
            keyBytes = keyBytes,
            algorithm = config.algorithm,
            timeMillis = timeMillis,
            digits = config.digits,
            periodSeconds = config.periodSeconds
        )
    }

    /**
     * Core RFC 6238 / RFC 4226 HOTP-over-time-step HMAC truncation algorithm operating on raw key bytes.
     */
    fun generateCodeForKeyBytes(
        keyBytes: ByteArray,
        algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
        timeMillis: Long = System.currentTimeMillis(),
        digits: Int = DEFAULT_DIGITS,
        periodSeconds: Int = DEFAULT_PERIOD_SECONDS
    ): String? {
        if (keyBytes.isEmpty() || periodSeconds <= 0 || digits !in 6..8) return null
        return try {
            val epochSeconds = Math.floorDiv(timeMillis, 1000L)
            val timeStep = Math.floorDiv(epochSeconds, periodSeconds.toLong())
            val counterBytes = ByteBuffer.allocate(8).putLong(timeStep).array()

            val mac = Mac.getInstance(algorithm.hmacName)
            mac.init(SecretKeySpec(keyBytes, algorithm.hmacName))
            val hmac = mac.doFinal(counterBytes)

            val offset = hmac[hmac.size - 1].toInt() and 0x0F
            val binary = ((hmac[offset].toInt() and 0x7F) shl 24) or
                ((hmac[offset + 1].toInt() and 0xFF) shl 16) or
                ((hmac[offset + 2].toInt() and 0xFF) shl 8) or
                (hmac[offset + 3].toInt() and 0xFF)

            val divisor = 10.0.pow(digits.toDouble()).toInt()
            val otp = binary % divisor
            String.format(Locale.US, "%0${digits}d", otp)
        } catch (_: Exception) {
            null
        }
    }

    fun getRemainingSeconds(
        periodSeconds: Int = DEFAULT_PERIOD_SECONDS,
        timeMillis: Long = System.currentTimeMillis()
    ): Int {
        if (periodSeconds <= 0) return DEFAULT_PERIOD_SECONDS
        val epochSec = Math.floorDiv(timeMillis, 1000L)
        val elapsed = Math.floorMod(epochSec, periodSeconds.toLong()).toInt()
        return periodSeconds - elapsed
    }

    fun formatCode(code: String): String {
        return when (code.length) {
            6 -> "${code.substring(0, 3)} ${code.substring(3)}"
            8 -> "${code.substring(0, 4)} ${code.substring(4)}"
            else -> code
        }
    }

    /**
     * Verifies a candidate TOTP code against [storedSeedOrUri] using constant-time comparison
     * across `[-allowedDriftSteps .. +allowedDriftSteps]` (default `±1` step per RFC 6238 §5.2).
     */
    fun verifyCode(
        storedSeedOrUri: String,
        candidateCode: String,
        timeMillis: Long = System.currentTimeMillis(),
        allowedDriftSteps: Int = 1
    ): TotpValidationResult {
        val config = parseStoredSeedOrUri(storedSeedOrUri).getOrElse { error ->
            return TotpValidationResult(
                isValid = false,
                message = error.message ?: "Invalid 2FA configuration"
            )
        }

        val normalizedCandidate = candidateCode.trim().replace(" ", "").replace("-", "")
        if (normalizedCandidate.length != config.digits || !normalizedCandidate.all { it.isDigit() }) {
            return TotpValidationResult(
                isValid = false,
                message = "Enter a ${config.digits}-digit numeric verification code"
            )
        }

        val clampedDrift = allowedDriftSteps.coerceIn(0, MAX_DRIFT_STEPS)
        val offsets = buildList {
            add(0)
            for (step in 1..clampedDrift) {
                add(-step)
                add(step)
            }
        }

        val candidateBytes = normalizedCandidate.toByteArray(Charsets.US_ASCII)
        val stepMillis = config.periodSeconds.toLong() * 1000L

        for (offset in offsets) {
            val expected = generateCode(config, timeMillis + offset * stepMillis) ?: continue
            val expectedBytes = expected.toByteArray(Charsets.US_ASCII)
            if (MessageDigest.isEqual(expectedBytes, candidateBytes)) {
                val windowMsg = when {
                    offset == 0 -> "Code verified (current ${config.periodSeconds}s window)"
                    offset < 0 -> "Code verified (${offset} step clock drift)"
                    else -> "Code verified (+$offset step clock drift)"
                }
                return TotpValidationResult(
                    isValid = true,
                    matchedStepOffset = offset,
                    matchedCode = expected,
                    message = windowMsg
                )
            }
        }

        return TotpValidationResult(
            isValid = false,
            message = "Verification failed: code does not match ${config.algorithm.displayName} ${config.periodSeconds}s window"
        )
    }

    private fun percentDecode(value: String): String {
        if (!value.contains('%')) return value
        val out = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length) {
                val hi = Character.digit(value[i + 1], 16)
                val lo = Character.digit(value[i + 2], 16)
                if (hi >= 0 && lo >= 0) {
                    out.add(((hi shl 4) or lo).toByte())
                    i += 3
                    continue
                }
            }
            val utf8 = c.toString().toByteArray(Charsets.UTF_8)
            for (b in utf8) out.add(b)
            i++
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private fun percentEncode(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val unsigned = b.toInt() and 0xFF
            val ch = unsigned.toChar()
            val isUnreserved = ch in 'A'..'Z' ||
                ch in 'a'..'z' ||
                ch in '0'..'9' ||
                ch == '-' || ch == '_' || ch == '.' || ch == '~'
            if (isUnreserved) {
                sb.append(ch)
            } else {
                sb.append('%')
                sb.append(Character.forDigit((unsigned shr 4) and 0xF, 16).uppercaseChar())
                sb.append(Character.forDigit(unsigned and 0xF, 16).uppercaseChar())
            }
        }
        return sb.toString()
    }
}
