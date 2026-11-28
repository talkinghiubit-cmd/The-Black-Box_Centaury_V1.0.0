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
        assertEquals("GitHub", parsed?.second)
    }

    @Test
    fun testRfc6238AppendixBOfficialVectors() {
        val sha1Key = "12345678901234567890".toByteArray(Charsets.US_ASCII)
        val sha256Key = "12345678901234567890123456789012".toByteArray(Charsets.US_ASCII)
        val sha512Key = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray(Charsets.US_ASCII)

        val vectors = listOf(
            Triple(59L, com.example.security.TotpAlgorithm.SHA1 to "94287082", Triple("46119246", "90693936", sha1Key)),
            Triple(1111111109L, com.example.security.TotpAlgorithm.SHA1 to "07081804", Triple("68084774", "25091201", sha1Key)),
            Triple(1111111111L, com.example.security.TotpAlgorithm.SHA1 to "14050471", Triple("67062674", "99943326", sha1Key)),
            Triple(1234567890L, com.example.security.TotpAlgorithm.SHA1 to "89005924", Triple("91819424", "93441116", sha1Key)),
            Triple(2000000000L, com.example.security.TotpAlgorithm.SHA1 to "69279037", Triple("90698825", "38618901", sha1Key)),
            Triple(20000000000L, com.example.security.TotpAlgorithm.SHA1 to "65353130", Triple("77737706", "47863826", sha1Key))
        )

        for ((epochSec, sha1Pair, rest) in vectors) {
            val timeMillis = epochSec * 1000L
            val (_, expectedSha1) = sha1Pair
            val (expectedSha256, expectedSha512, _) = rest

            assertEquals(
                "SHA1 vector mismatch at t=$epochSec",
                expectedSha1,
                com.example.security.TotpAuthenticatorService.generateCodeForKeyBytes(
                    keyBytes = sha1Key,
                    algorithm = com.example.security.TotpAlgorithm.SHA1,
                    timeMillis = timeMillis,
                    digits = 8,
                    periodSeconds = 30
                )
            )
            assertEquals(
                "SHA256 vector mismatch at t=$epochSec",
                expectedSha256,
                com.example.security.TotpAuthenticatorService.generateCodeForKeyBytes(
                    keyBytes = sha256Key,
                    algorithm = com.example.security.TotpAlgorithm.SHA256,
                    timeMillis = timeMillis,
                    digits = 8,
                    periodSeconds = 30
                )
            )
            assertEquals(
                "SHA512 vector mismatch at t=$epochSec",
                expectedSha512,
                com.example.security.TotpAuthenticatorService.generateCodeForKeyBytes(
                    keyBytes = sha512Key,
                    algorithm = com.example.security.TotpAlgorithm.SHA512,
                    timeMillis = timeMillis,
                    digits = 8,
                    periodSeconds = 30
                )
            )
        }
    }

    @Test
    fun testGoogleAuthenticatorUriParsingBuildingAndValidation() {
        val rawUri = "otpauth://totp/Cloud%20Corp:alice+sec@example.com?secret=jbsw-y3dp-ehpk-3pxp&issuer=Cloud%20Corp&algorithm=SHA256&digits=8&period=60"
        val parsed = com.example.security.TotpAuthenticatorService.parseOtpAuthUri(rawUri)
        assertTrue(parsed.isSuccess)
        val config = parsed.getOrThrow()
        assertEquals("JBSWY3DPEHPK3PXP", config.secret)
        assertEquals("Cloud Corp", config.issuer)
        assertEquals("alice+sec@example.com", config.accountName)
        assertEquals(com.example.security.TotpAlgorithm.SHA256, config.algorithm)
        assertEquals(8, config.digits)
        assertEquals(60, config.periodSeconds)

        // Canonical URI roundtrip
        val rebuilt = com.example.security.TotpAuthenticatorService.buildOtpAuthUri(config)
        val reparsed = com.example.security.TotpAuthenticatorService.parseOtpAuthUri(rebuilt).getOrThrow()
        assertEquals(config, reparsed)

        // Validation preserves URI for extended config, normalizes plain Base32
        val validPlain = com.example.security.TotpAuthenticatorService.validateSeedInput(" jbsw y3dp ehpk 3pxp ")
        assertTrue(validPlain is com.example.security.TotpSeedValidationResult.Valid)
        assertEquals("JBSWY3DPEHPK3PXP", (validPlain as com.example.security.TotpSeedValidationResult.Valid).storableValue)

        val validExtended = com.example.security.TotpAuthenticatorService.validateSeedInput(rawUri)
        assertTrue(validExtended is com.example.security.TotpSeedValidationResult.Valid)
        assertTrue((validExtended as com.example.security.TotpSeedValidationResult.Valid).storableValue.startsWith("otpauth://totp/"))

        // Strict rejections
        assertTrue(com.example.security.TotpAuthenticatorService.parseOtpAuthUri("otpauth://hotp/Test:user?secret=JBSWY3DPEHPK3PXP&counter=1").isFailure)
        assertTrue(com.example.security.TotpAuthenticatorService.parseOtpAuthUri("otpauth://totp/Test?secret=JBSWY3DPEHPK3PXP&digits=7").isFailure)
        assertTrue(com.example.security.TotpAuthenticatorService.parseOtpAuthUri("otpauth://totp/Test?secret=JBSWY3DPEHPK3PXP&algorithm=MD5").isFailure)
        assertTrue(com.example.security.TotpAuthenticatorService.validateSeedInput("INVALID-CHAR-0189!").let {
            it is com.example.security.TotpSeedValidationResult.Invalid
        })
    }

    @Test
    fun testTotpVerificationWithClockDriftWindow() {
        val uri = "otpauth://totp/Acme:ops@acme.io?secret=JBSWY3DPEHPK3PXP&issuer=Acme&algorithm=SHA256&digits=8&period=30"
        val nowMillis = 1_700_000_005_000L

        val currentCode = com.example.security.TotpAuthenticatorService.generateCode(uri, nowMillis)!!
        val prevCode = com.example.security.TotpAuthenticatorService.generateCode(uri, nowMillis - 30_000L)!!
        val nextCode = com.example.security.TotpAuthenticatorService.generateCode(uri, nowMillis + 30_000L)!!
        val expiredCode = com.example.security.TotpAuthenticatorService.generateCode(uri, nowMillis - 60_000L)!!

        val currentCheck = com.example.security.TotpAuthenticatorService.verifyCode(uri, currentCode, nowMillis)
        assertTrue(currentCheck.isValid)
        assertEquals(0, currentCheck.matchedStepOffset)

        val prevCheck = com.example.security.TotpAuthenticatorService.verifyCode(uri, prevCode, nowMillis)
        assertTrue(prevCheck.isValid)
        assertEquals(-1, prevCheck.matchedStepOffset)

        val nextCheck = com.example.security.TotpAuthenticatorService.verifyCode(uri, nextCode, nowMillis)
        assertTrue(nextCheck.isValid)
        assertEquals(1, nextCheck.matchedStepOffset)

        val expiredCheck = com.example.security.TotpAuthenticatorService.verifyCode(uri, expiredCode, nowMillis, allowedDriftSteps = 1)
        assertFalse(expiredCheck.isValid)

        val malformedCheck = com.example.security.TotpAuthenticatorService.verifyCode(uri, "123456", nowMillis) // expects 8 digits
        assertFalse(malformedCheck.isValid)
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

    @Test
    fun testMultiBrowserCsvImportChromeEdgeFirefoxOpera() {
        // 1. Microsoft Edge CSV
        val edgeCsv = "name,url,username,password,note\n" +
            "Microsoft 365,https://login.microsoftonline.com,alice@corp.com,EdgeStrong#2026,Work tenant\n"
        val edgePreview = com.example.security.CsvPasswordImporter.parseFromString(
            csvContent = edgeCsv,
            sourceFileName = "microsoft-edge-passwords.csv",
            browserHint = com.example.security.ImportBrowserPreset.EDGE
        ).getOrThrow()
        assertEquals("Microsoft Edge CSV", edgePreview.detectedFormat)
        assertEquals(1, edgePreview.parsedItems.size)
        assertEquals("Microsoft 365", edgePreview.parsedItems[0].title)
        assertEquals("EdgeStrong#2026", edgePreview.parsedItems[0].password)

        // 2. Mozilla Firefox CSV with semicolon delimiter, chrome:// internal row skip, and timePasswordChanged
        val firefoxCsv = "\"url\";\"username\";\"password\";\"httpRealm\";\"formActionOrigin\";\"guid\";\"timeCreated\";\"timeLastUsed\";\"timePasswordChanged\"\n" +
            "\"chrome://FirefoxAccounts\";\"internal\";\"skip_me\";\"\";\"\";\"{g1}\";\"1700000000000\";\"1700000000000\";\"1700000000000\"\n" +
            "\"https://bugzilla.mozilla.org\";\"mozdev\";\"FoxSecret!99\";\"\";\"https://bugzilla.mozilla.org\";\"{g2}\";\"1700000000000\";\"1705000000000\";\"1706000000000\"\n"
        val firefoxPreview = com.example.security.CsvPasswordImporter.parseFromString(
            csvContent = firefoxCsv,
            sourceFileName = "logins.csv",
            browserHint = com.example.security.ImportBrowserPreset.FIREFOX
        ).getOrThrow()
        assertEquals("Mozilla Firefox CSV", firefoxPreview.detectedFormat)
        assertEquals(1, firefoxPreview.parsedItems.size)
        assertEquals(1, firefoxPreview.skippedRows)
        assertEquals("bugzilla.mozilla.org", firefoxPreview.parsedItems[0].title)
        assertEquals("mozdev", firefoxPreview.parsedItems[0].username)
        assertEquals("FoxSecret!99", firefoxPreview.parsedItems[0].password)
        assertEquals(1706000000000L, firefoxPreview.parsedItems[0].passwordUpdatedAt)

        // 3. Opera CSV with tab delimiter
        val operaCsv = "name\turl\tusername\tpassword\tnote\n" +
            "Opera Sync\thttps://auth.opera.com\topera_user\tOperaPass!77\tMain account\n"
        val operaPreview = com.example.security.CsvPasswordImporter.parseFromString(
            csvContent = operaCsv,
            sourceFileName = "Opera Passwords.csv",
            browserHint = com.example.security.ImportBrowserPreset.OPERA
        ).getOrThrow()
        assertEquals("Opera CSV", operaPreview.detectedFormat)
        assertEquals(1, operaPreview.parsedItems.size)
        assertEquals("Opera Sync", operaPreview.parsedItems[0].title)
        assertEquals("OperaPass!77", operaPreview.parsedItems[0].password)
    }
}
