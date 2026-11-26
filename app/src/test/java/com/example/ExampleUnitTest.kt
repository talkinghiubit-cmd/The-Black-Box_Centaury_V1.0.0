package com.example

import com.example.model.VaultCategory
import com.example.security.BlackBoxSecurity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    @Test
    fun testKeyDerivationIsolation() {
        val masterPassword = "PrimaryMasterPassword#2024".toCharArray()
        val duressPin = "9821".toCharArray()

        val saltPrimary = BlackBoxSecurity.generateRandomSalt()
        val saltDuress = BlackBoxSecurity.generateRandomSalt()

        val keyPrimary = BlackBoxSecurity.deriveMasterKey(masterPassword, saltPrimary)
        val keyDuress = BlackBoxSecurity.deriveMasterKey(duressPin, saltDuress)

        // Keys must not be identical
        assertFalse(keyPrimary.encoded.contentEquals(keyDuress.encoded))

        // Decryption with wrong key must fail
        val plaintext = "Sensitive Nuclear Launch Code"
        val (ciphertext, iv) = BlackBoxSecurity.encrypt(plaintext, keyPrimary)

        val decryptedWithCorrectKey = BlackBoxSecurity.decrypt(ciphertext, iv, keyPrimary)
        assertEquals(plaintext, decryptedWithCorrectKey)

        try {
            BlackBoxSecurity.decrypt(ciphertext, iv, keyDuress)
            fail("Decryption with duress key on primary ciphertext should have thrown an exception")
        } catch (e: Exception) {
            // Expected cryptographic tag mismatch
            assertTrue(true)
        }
    }

    @Test
    fun testTotpGeneration() {
        // Test RFC 6238 / Base32 secret
        val secret = "JBSWY3DPEHPK3PXP" // Standard Base32 test vector: "Hello!"
        val code1 = BlackBoxSecurity.generateTotpCode(secret, timeMillis = 1111111111000L)
        assertNotNull(code1)
        assertEquals(6, code1?.length)
        assertTrue(code1?.all { it.isDigit() } == true)

        // Test URI parser
        val uri = "otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP&issuer=GitHub"
        val parsed = BlackBoxSecurity.parseTotpUri(uri)
        assertNotNull(parsed)
        assertEquals("JBSWY3DPEHPK3PXP", parsed?.first)
    }

    @Test
    fun testHaveIBeenPwnedSha1Prefixing() {
        val password = "password"
        val sha1 = BlackBoxSecurity.sha1Hex(password)
        assertEquals(40, sha1.length)
        // Known SHA-1 of "password": 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", sha1)
        val prefix = sha1.substring(0, 5)
        val suffix = sha1.substring(5)
        assertEquals("5BAA6", prefix)
        assertEquals("1E4C9B93F3F0682250B6CF8331B7EE68FD8", suffix)
    }

    @Test
    fun testFileByteEncryptionRoundtrip() {
        val salt = BlackBoxSecurity.generateRandomSalt()
        val key = BlackBoxSecurity.deriveMasterKey("SecretFileKey#1".toCharArray(), salt)

        val sampleFileBytes = "Secret Tax Document and Private Financial Summary 2024".toByteArray(Charsets.UTF_8)
        val (ciphertext, iv) = BlackBoxSecurity.encryptFileBytes(sampleFileBytes, key)

        val decrypted = BlackBoxSecurity.decryptFileBytes(ciphertext, iv, key)
        assertArrayEquals(sampleFileBytes, decrypted)
    }

    @Test
    fun testDecoyItemsGeneration() {
        val decoys = BlackBoxSecurity.generateRealisticDecoyItems()
        assertTrue(decoys.size >= 4)
        assertTrue(decoys.all { it.partitionId == 1 })
        assertTrue(decoys.any { it.category == VaultCategory.LOGIN })
        assertTrue(decoys.any { it.category == VaultCategory.NOTE })
        assertTrue(decoys.any { it.category == VaultCategory.CARD })
    }
}
