package com.example.security

import android.content.Context
import android.net.Uri
import com.example.model.VaultCategory
import com.example.model.VaultItem
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.net.URI
import java.util.Locale
import java.util.UUID

data class CsvImportPreview(
    val detectedFormat: String,
    val sourceFileName: String,
    val parsedItems: List<VaultItem>,
    val skippedRows: Int,
    val duplicateCount: Int,
    val duplicateKeys: Set<String>
)

data class CsvImportCommitResult(
    val importedCount: Int,
    val skippedDuplicates: Int
)

/**
 * RFC-4180 compliant streaming CSV password importer supporting Google Chrome,
 * Chromium/Edge/Brave, Firefox, Safari, Bitwarden, 1Password, LastPass, and generic CSV files.
 *
 * Enforces strict size and row bounds to prevent memory exhaustion on untrusted files,
 * and computes normalized credential fingerprints for duplicate detection.
 */
object CsvPasswordImporter {

    const val MAX_CSV_BYTES = 10L * 1024L * 1024L // 10 MB safety ceiling
    const val MAX_IMPORT_ROWS = 10_000

    private val TITLE_ALIASES = setOf(
        "name", "title", "account", "service", "site", "sitename", "login_name", "label"
    )
    private val URL_ALIASES = setOf(
        "url", "origin", "website", "webaddress", "login_uri", "uri", "hostname", "domain"
    )
    private val USERNAME_ALIASES = setOf(
        "username", "user", "login_username", "email", "login", "user_name", "account_name", "userid"
    )
    private val PASSWORD_ALIASES = setOf(
        "password", "pass", "login_password", "pw", "secret", "passcode"
    )
    private val NOTES_ALIASES = setOf(
        "note", "notes", "extra", "comment", "comments", "description", "memo"
    )
    private val TOTP_ALIASES = setOf(
        "totp", "login_totp", "otp", "2fa", "totp_secret"
    )
    private val CATEGORY_ALIASES = setOf(
        "type", "category", "grouping", "folder"
    )

    fun parseFromUri(
        context: Context,
        uri: Uri,
        existingItems: List<VaultItem> = emptyList()
    ): Result<CsvImportPreview> {
        return try {
            var fileName = "chrome_passwords.csv"
            var fileSize = -1L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) fileSize = cursor.getLong(sizeIdx)
                }
            }
            if (fileSize > MAX_CSV_BYTES) {
                return Result.failure(
                    IllegalArgumentException("CSV file exceeds the 10 MB safety limit.")
                )
            }
            val stream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(IllegalArgumentException("Unable to open selected CSV file."))
            stream.use { input ->
                parseFromInputStream(input, fileName, existingItems)
            }
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Failed to read CSV file: ${e.message}", e))
        }
    }

    fun parseFromInputStream(
        inputStream: InputStream,
        sourceFileName: String = "passwords.csv",
        existingItems: List<VaultItem> = emptyList()
    ): Result<CsvImportPreview> {
        return try {
            val bounded = BoundedInputStream(inputStream, MAX_CSV_BYTES + 1)
            BufferedReader(InputStreamReader(bounded, Charsets.UTF_8)).use { reader ->
                parseFromReader(reader, sourceFileName, existingItems)
            }
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Failed to parse CSV stream: ${e.message}", e))
        }
    }

    fun parseFromString(
        csvContent: String,
        sourceFileName: String = "pasted_passwords.csv",
        existingItems: List<VaultItem> = emptyList()
    ): Result<CsvImportPreview> {
        if (csvContent.toByteArray(Charsets.UTF_8).size > MAX_CSV_BYTES) {
            return Result.failure(IllegalArgumentException("CSV content exceeds 10 MB limit."))
        }
        return StringReader(csvContent).use { reader ->
            parseFromReader(reader, sourceFileName, existingItems)
        }
    }

    fun parseFromReader(
        reader: Reader,
        sourceFileName: String = "passwords.csv",
        existingItems: List<VaultItem> = emptyList()
    ): Result<CsvImportPreview> {
        val rows = parseRfc4180Rows(reader, MAX_IMPORT_ROWS + 1)
        if (rows.isEmpty()) {
            return Result.failure(IllegalArgumentException("The CSV file is empty."))
        }

        val headerRow = rows.first().mapIndexed { idx, col ->
            val cleaned = if (idx == 0) col.removePrefix("\uFEFF") else col
            normalizeHeader(cleaned)
        }

        val titleIdx = headerRow.indexOfFirst { it in TITLE_ALIASES }
        val urlIdx = headerRow.indexOfFirst { it in URL_ALIASES }
        val userIdx = headerRow.indexOfFirst { it in USERNAME_ALIASES }
        val passIdx = headerRow.indexOfFirst { it in PASSWORD_ALIASES }
        val notesIdx = headerRow.indexOfFirst { it in NOTES_ALIASES }
        val totpIdx = headerRow.indexOfFirst { it in TOTP_ALIASES }
        val catIdx = headerRow.indexOfFirst { it in CATEGORY_ALIASES }

        if (passIdx < 0 && userIdx < 0 && urlIdx < 0 && notesIdx < 0) {
            return Result.failure(
                IllegalArgumentException(
                    "Unrecognized CSV headers. Expected Google Chrome format (name, url, username, password, note)."
                )
            )
        }

        val isChromeHeader = headerRow.containsAll(listOf("name", "url", "username", "password"))
        val detectedFormat = when {
            isChromeHeader -> "Google Chrome CSV"
            headerRow.contains("login_uri") || headerRow.contains("login_password") -> "Bitwarden CSV"
            headerRow.contains("httprealm") || headerRow.contains("formactionorigin") -> "Firefox CSV"
            else -> "Standard CSV"
        }

        val existingFingerprints = existingItems.map { fingerprintItem(it) }.toHashSet()
        val seenImportFingerprints = HashSet<String>()
        val duplicateKeys = HashSet<String>()
        val parsedItems = mutableListOf<VaultItem>()
        var skippedRows = 0
        var duplicateCount = 0
        val now = System.currentTimeMillis()

        for (rowIdx in 1 until rows.size) {
            if (parsedItems.size >= MAX_IMPORT_ROWS) break
            val row = rows[rowIdx]
            if (row.all { it.isBlank() }) {
                skippedRows++
                continue
            }

            val rawTitle = sanitizeSingleLine(row.getOrNull(titleIdx).orEmpty())
            val rawUrl = sanitizeSingleLine(row.getOrNull(urlIdx).orEmpty())
            val rawUsername = sanitizeSingleLine(row.getOrNull(userIdx).orEmpty())
            val rawPassword = sanitizeCredential(row.getOrNull(passIdx).orEmpty())
            val rawNotes = sanitizeMultiLine(row.getOrNull(notesIdx).orEmpty())
            val rawTotp = sanitizeSingleLine(row.getOrNull(totpIdx).orEmpty())
            val rawCategory = sanitizeSingleLine(row.getOrNull(catIdx).orEmpty()).lowercase(Locale.US)

            if (rawTitle.isEmpty() && rawUrl.isEmpty() && rawUsername.isEmpty() &&
                rawPassword.isEmpty() && rawNotes.isEmpty()
            ) {
                skippedRows++
                continue
            }

            val resolvedCategory = if (
                (rawCategory.contains("note") || rawCategory == "secure_note") &&
                rawPassword.isEmpty() && rawUrl.isEmpty()
            ) {
                VaultCategory.NOTE
            } else if (rawPassword.isEmpty() && rawUrl.isEmpty() && rawUsername.isEmpty() && rawNotes.isNotEmpty()) {
                VaultCategory.NOTE
            } else {
                VaultCategory.LOGIN
            }

            val resolvedTitle = when {
                rawTitle.isNotBlank() -> rawTitle
                rawUrl.isNotBlank() -> extractHostLabel(rawUrl).ifBlank { "Imported Login #${parsedItems.size + 1}" }
                rawUsername.isNotBlank() -> rawUsername
                else -> "Imported Record #${parsedItems.size + 1}"
            }

            val item = VaultItem(
                id = UUID.randomUUID().toString(),
                title = resolvedTitle,
                category = resolvedCategory,
                username = rawUsername,
                password = rawPassword,
                url = rawUrl,
                notes = rawNotes,
                totpSecret = rawTotp,
                passwordUpdatedAt = now,
                pwnedBreachCount = if (resolvedCategory == VaultCategory.LOGIN && rawPassword.isNotEmpty()) -1 else 0,
                createdAt = now,
                updatedAt = now
            )

            val fp = fingerprintItem(item)
            if (fp in existingFingerprints || fp in seenImportFingerprints) {
                duplicateCount++
                duplicateKeys.add(item.id)
            }
            seenImportFingerprints.add(fp)
            parsedItems.add(item)
        }

        if (parsedItems.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("No importable password or note records found in the CSV file.")
            )
        }

        return Result.success(
            CsvImportPreview(
                detectedFormat = detectedFormat,
                sourceFileName = sourceFileName,
                parsedItems = parsedItems,
                skippedRows = skippedRows,
                duplicateCount = duplicateCount,
                duplicateKeys = duplicateKeys
            )
        )
    }

    fun fingerprintItem(item: VaultItem): String {
        val host = extractHostLabel(item.url).lowercase(Locale.US)
        val user = item.username.trim().lowercase(Locale.US)
        val secret = item.password
        val title = item.title.trim().lowercase(Locale.US)
        val notes = if (item.category == VaultCategory.NOTE) item.notes.trim() else ""
        return "${item.category.name}|$host|$title|$user|$secret|$notes"
    }

    fun extractHostLabel(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            val host = URI(withScheme).host ?: ""
            host.removePrefix("www.")
        } catch (_: Exception) {
            trimmed.substringAfter("://").substringBefore("/").removePrefix("www.")
        }
    }

    /**
     * Stateful RFC-4180 parser supporting quoted commas, escaped quotes (""), and embedded newlines.
     */
    internal fun parseRfc4180Rows(reader: Reader, maxRows: Int): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var prevCharWasQuote = false
        var chInt = reader.read()

        while (chInt != -1) {
            val ch = chInt.toChar()
            if (inQuotes) {
                if (ch == '"') {
                    val next = reader.read()
                    if (next == '"'.code) {
                        field.append('"')
                        chInt = reader.read()
                        continue
                    } else {
                        inQuotes = false
                        prevCharWasQuote = true
                        chInt = next
                        continue
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> {
                        if (field.isEmpty() && !prevCharWasQuote) {
                            inQuotes = true
                        } else {
                            field.append(ch)
                        }
                        prevCharWasQuote = false
                    }
                    ',' -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                        prevCharWasQuote = false
                    }
                    '\r' -> {
                        val next = reader.read()
                        currentRow.add(field.toString())
                        field.setLength(0)
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                        prevCharWasQuote = false
                        if (rows.size >= maxRows) return rows
                        chInt = if (next == '\n'.code) reader.read() else next
                        continue
                    }
                    '\n' -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                        prevCharWasQuote = false
                        if (rows.size >= maxRows) return rows
                    }
                    else -> {
                        field.append(ch)
                        prevCharWasQuote = false
                    }
                }
            }
            chInt = reader.read()
        }

        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            rows.add(currentRow)
        }

        return rows
    }

    private fun normalizeHeader(raw: String): String {
        return raw.trim()
            .lowercase(Locale.US)
            .replace(" ", "_")
            .replace("-", "_")
    }

    private fun sanitizeSingleLine(value: String): String {
        return value.filter { it >= ' ' && it != '\u007F' }.trim()
    }

    private fun sanitizeCredential(value: String): String {
        // Preserve leading/trailing spaces only if intentionally present, strip control chars
        return value.filter { it >= ' ' && it != '\u007F' }
    }

    private fun sanitizeMultiLine(value: String): String {
        return value.filter { it == '\n' || it == '\t' || (it >= ' ' && it != '\u007F') }.trim()
    }

    private class BoundedInputStream(
        private val delegate: InputStream,
        private val maxBytes: Long
    ) : InputStream() {
        private var bytesRead = 0L

        override fun read(): Int {
            if (bytesRead >= maxBytes) {
                throw IllegalArgumentException("CSV file exceeds the maximum allowed 10 MB size.")
            }
            val b = delegate.read()
            if (b != -1) bytesRead++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (bytesRead >= maxBytes) {
                throw IllegalArgumentException("CSV file exceeds the maximum allowed 10 MB size.")
            }
            val allowed = minOf(len.toLong(), maxBytes - bytesRead).toInt()
            val count = delegate.read(b, off, allowed)
            if (count > 0) bytesRead += count
            return count
        }

        override fun close() {
            delegate.close()
        }
    }
}
