package com.example.security

import com.example.model.PasswordStrength
import java.security.SecureRandom
import kotlin.math.log2

/**
 * Configuration options for the cryptographically secure password generator.
 */
data class PasswordOptions(
    val length: Int = 20,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeNumbers: Boolean = true,
    val includeSymbols: Boolean = true,
    val customSymbols: String = DEFAULT_SYMBOLS,
    val excludeAmbiguous: Boolean = true,
    val excludeSimilar: Boolean = false,
    val minUppercase: Int = 1,
    val minLowercase: Int = 1,
    val minNumbers: Int = 1,
    val minSymbols: Int = 1
) {
    companion object {
        const val DEFAULT_SYMBOLS = "!@#$%^&*()-_=+[]{}<>~"
        const val EXTENDED_SYMBOLS = "!@#$%^&*()-_=+[]{}:;\"',.<>?/\\|~`"
    }
}

/**
 * Detailed analysis of password complexity and cryptographic entropy.
 */
data class EntropyAnalysis(
    val entropyBits: Int,
    val poolSize: Int,
    val score: Int, // 0 to 4
    val strengthLabel: String,
    val estimatedCrackTime: String
)

/**
 * Cryptographically strong random password generator utility.
 * Powered by [SecureRandom] with guaranteed character distribution,
 * customizable character sets, symbol sets, and Shannon entropy analysis.
 */
object SecurePasswordGenerator {

    private val secureRandom = SecureRandom()

    const val UPPERCASE_STANDARD = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val LOWERCASE_STANDARD = "abcdefghijklmnopqrstuvwxyz"
    const val NUMBERS_STANDARD = "0123456789"
    const val AMBIGUOUS_CHARS = "0O1lI|B8"
    const val SIMILAR_CHARS = "il1Lo0O"

    // Curated high-entropy wordlist for memorable multi-word passphrase generation
    private val PASSPHRASE_WORDS = listOf(
        "abyss", "access", "anchor", "anthem", "apex", "arcade", "argon", "armor",
        "astral", "atomic", "aurora", "beacon", "binary", "blaze", "bolt", "breeze",
        "canyon", "carbon", "cipher", "citadel", "clover", "cobalt", "comet", "cosmic",
        "crater", "crypto", "crystal", "delta", "drift", "dynamo", "echo", "eclipse",
        "ember", "energy", "epoch", "falcon", "flame", "flux", "forge", "frost",
        "galaxy", "glacier", "gravity", "halo", "harbor", "haven", "helix", "horizon",
        "impact", "iron", "jade", "jupiter", "kinetic", "laser", "legacy", "lunar",
        "matrix", "meteor", "nebula", "nexus", "nova", "oasis", "omega", "orbit",
        "origin", "phantom", "phoenix", "photon", "plasma", "prism", "pulsar", "quantum",
        "radar", "radius", "raven", "relic", "rift", "ripple", "rover", "saber",
        "saturn", "shadow", "shield", "siren", "solar", "spark", "spectrum", "sphere",
        "strata", "summit", "titan", "torrent", "tracer", "vector", "vertex", "vortex",
        "zenith", "zephyr"
    )

    /**
     * Generates a cryptographically secure random password based on the supplied options.
     */
    fun generatePassword(options: PasswordOptions = PasswordOptions()): String {
        val uppercaseSet = filterCharacters(
            UPPERCASE_STANDARD,
            options.excludeAmbiguous,
            options.excludeSimilar
        )
        val lowercaseSet = filterCharacters(
            LOWERCASE_STANDARD,
            options.excludeAmbiguous,
            options.excludeSimilar
        )
        val numbersSet = filterCharacters(
            NUMBERS_STANDARD,
            options.excludeAmbiguous,
            options.excludeSimilar
        )
        val symbolsSet = if (options.customSymbols.isNotEmpty()) {
            filterCharacters(
                options.customSymbols,
                options.excludeAmbiguous,
                options.excludeSimilar
            )
        } else {
            filterCharacters(
                PasswordOptions.DEFAULT_SYMBOLS,
                options.excludeAmbiguous,
                options.excludeSimilar
            )
        }

        val pool = StringBuilder()
        val guaranteedChars = mutableListOf<Char>()

        if (options.includeUppercase && uppercaseSet.isNotEmpty()) {
            pool.append(uppercaseSet)
            repeat(options.minUppercase.coerceAtLeast(1)) {
                guaranteedChars.add(randomCharFrom(uppercaseSet))
            }
        }

        if (options.includeLowercase && lowercaseSet.isNotEmpty()) {
            pool.append(lowercaseSet)
            repeat(options.minLowercase.coerceAtLeast(1)) {
                guaranteedChars.add(randomCharFrom(lowercaseSet))
            }
        }

        if (options.includeNumbers && numbersSet.isNotEmpty()) {
            pool.append(numbersSet)
            repeat(options.minNumbers.coerceAtLeast(1)) {
                guaranteedChars.add(randomCharFrom(numbersSet))
            }
        }

        if (options.includeSymbols && symbolsSet.isNotEmpty()) {
            pool.append(symbolsSet)
            repeat(options.minSymbols.coerceAtLeast(1)) {
                guaranteedChars.add(randomCharFrom(symbolsSet))
            }
        }

        // Fallback safety if user disabled all options
        if (pool.isEmpty()) {
            val fallback = LOWERCASE_STANDARD + NUMBERS_STANDARD
            pool.append(fallback)
            guaranteedChars.add(randomCharFrom(fallback))
        }

        val effectiveLength = options.length.coerceAtLeast(guaranteedChars.size)
        val passwordChars = CharArray(effectiveLength)

        // Fill guaranteed characters first
        for (i in guaranteedChars.indices) {
            passwordChars[i] = guaranteedChars[i]
        }

        // Fill the remaining length with random picks from the whole pool
        val poolString = pool.toString()
        for (i in guaranteedChars.size until effectiveLength) {
            passwordChars[i] = randomCharFrom(poolString)
        }

        // Cryptographically shuffle using Fisher-Yates so guaranteed positions are distributed randomly
        fisherYatesShuffle(passwordChars)

        return String(passwordChars)
    }

    /**
     * Generates a high-entropy, memorable multi-word passphrase (e.g. "comet-pulsar-argon-citadel-74").
     */
    fun generatePassphrase(
        wordCount: Int = 4,
        separator: String = "-",
        includeNumber: Boolean = true
    ): String {
        val selectedWords = mutableListOf<String>()
        repeat(wordCount.coerceIn(3, 8)) {
            val index = secureRandom.nextInt(PASSPHRASE_WORDS.size)
            selectedWords.add(PASSPHRASE_WORDS[index])
        }

        var result = selectedWords.joinToString(separator)
        if (includeNumber) {
            val num = secureRandom.nextInt(90) + 10 // 10 to 99
            result += "$separator$num"
        }
        return result
    }

    /**
     * Calculates precise cryptographic entropy (Shannon information entropy) and brute-force resistance.
     */
    fun analyzeEntropy(password: String): EntropyAnalysis {
        if (password.isEmpty()) {
            return EntropyAnalysis(
                entropyBits = 0,
                poolSize = 0,
                score = 0,
                strengthLabel = "Empty",
                estimatedCrackTime = "Instant"
            )
        }

        var poolSize = 0
        if (password.any { it in UPPERCASE_STANDARD }) poolSize += 26
        if (password.any { it in LOWERCASE_STANDARD }) poolSize += 26
        if (password.any { it in NUMBERS_STANDARD }) poolSize += 10
        if (password.any { !it.isLetterOrDigit() }) poolSize += 32

        if (poolSize == 0) poolSize = 10

        val entropy = (password.length * log2(poolSize.toDouble())).toInt()

        val (score, label, crackTime) = when {
            entropy < 28 -> Triple(0, "Very Weak", "Instant (< 1 sec)")
            entropy < 40 -> Triple(1, "Weak", "Seconds to minutes")
            entropy < 60 -> Triple(2, "Moderate", "Hours to months")
            entropy < 80 -> Triple(3, "Strong", "Centuries")
            else -> Triple(4, "Very Strong", "Millions of years (Military Grade)")
        }

        return EntropyAnalysis(
            entropyBits = entropy,
            poolSize = poolSize,
            score = score,
            strengthLabel = label,
            estimatedCrackTime = crackTime
        )
    }

    fun evaluateStrength(password: String): PasswordStrength {
        val analysis = analyzeEntropy(password)
        return PasswordStrength(analysis.score, analysis.strengthLabel, analysis.entropyBits)
    }

    private fun randomCharFrom(set: String): Char {
        return set[secureRandom.nextInt(set.length)]
    }

    private fun filterCharacters(
        input: String,
        excludeAmbiguous: Boolean,
        excludeSimilar: Boolean
    ): String {
        return input.filter { c ->
            val isAmbiguous = excludeAmbiguous && c in AMBIGUOUS_CHARS
            val isSimilar = excludeSimilar && c in SIMILAR_CHARS
            !isAmbiguous && !isSimilar
        }
    }

    private fun fisherYatesShuffle(array: CharArray) {
        for (i in array.size - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val temp = array[i]
            array[i] = array[j]
            array[j] = temp
        }
    }
}
