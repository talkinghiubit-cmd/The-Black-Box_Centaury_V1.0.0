package com.example.security

import android.content.Context
import com.example.data.VaultMetadataEntity
import com.example.data.VaultRecordEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SnapshotPackage(
    val formatVersion: String,
    val createdAt: Long,
    val recordsCount: Int,
    val masterSalt: String,
    val verifierHash: String,
    val records: List<VaultRecordEntity>,
    val checksum: String
)

object SnapshotManager {

    private const val FORMAT_HEADER = "BLACKBOX_ENCRYPTED_SNAPSHOT_V1"

    fun createSnapshotFile(
        context: Context,
        metadata: VaultMetadataEntity,
        records: List<VaultRecordEntity>
    ): File {
        val snapshotDir = File(context.filesDir, "snapshots")
        if (!snapshotDir.exists()) snapshotDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(snapshotDir, "blackbox_snapshot_$timestamp.blackbox")

        val recordsArray = JSONArray()
        records.forEach { record ->
            val obj = JSONObject().apply {
                put("id", record.id)
                put("title", record.title)
                put("category", record.category)
                put("encryptedPayload", record.encryptedPayload)
                put("initializationVector", record.initializationVector)
                put("isFavorite", record.isFavorite)
                put("partitionId", record.partitionId)
                put("passwordUpdatedAt", record.passwordUpdatedAt)
                put("pwnedBreachCount", record.pwnedBreachCount)
                put("createdAt", record.createdAt)
                put("updatedAt", record.updatedAt)
            }
            recordsArray.put(obj)
        }

        val recordsString = recordsArray.toString()
        val checksum = BlackBoxSecurity.computeSha256(recordsString)

        val rootJson = JSONObject().apply {
            put("format", FORMAT_HEADER)
            put("createdAt", System.currentTimeMillis())
            put("recordsCount", records.size)
            put("masterSalt", metadata.masterSalt)
            put("verifierHash", metadata.verifierHash)
            put("checksum", checksum)
            put("records", recordsArray)
        }

        val tempFile = File(snapshotDir, "${file.name}.tmp")
        try {
            tempFile.writeText(rootJson.toString(2), Charsets.UTF_8)
            if (!tempFile.renameTo(file)) {
                file.writeText(rootJson.toString(2), Charsets.UTF_8)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
        return file
    }

    fun parseSnapshot(jsonContent: String): Result<SnapshotPackage> {
        return try {
            val root = JSONObject(jsonContent)
            val format = root.optString("format", "")
            if (format != FORMAT_HEADER) {
                return Result.failure(IllegalArgumentException("Unsupported or invalid snapshot format: $format"))
            }

            val recordsArray = root.getJSONArray("records")
            val reportedChecksum = root.getString("checksum")
            val actualChecksum = BlackBoxSecurity.computeSha256(recordsArray.toString())

            if (reportedChecksum != actualChecksum) {
                return Result.failure(SecurityException("Snapshot integrity verification failed. File may be corrupted or altered."))
            }

            val recordsList = mutableListOf<VaultRecordEntity>()
            for (i in 0 until recordsArray.length()) {
                val obj = recordsArray.getJSONObject(i)
                recordsList.add(
                    VaultRecordEntity(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        category = obj.getString("category"),
                        encryptedPayload = obj.getString("encryptedPayload"),
                        initializationVector = obj.getString("initializationVector"),
                        isFavorite = obj.optBoolean("isFavorite", false),
                        partitionId = obj.optInt("partitionId", 0),
                        passwordUpdatedAt = obj.optLong("passwordUpdatedAt", obj.optLong("updatedAt", System.currentTimeMillis())),
                        pwnedBreachCount = obj.optInt("pwnedBreachCount", 0),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            val pkg = SnapshotPackage(
                formatVersion = format,
                createdAt = root.getLong("createdAt"),
                recordsCount = root.getInt("recordsCount"),
                masterSalt = root.getString("masterSalt"),
                verifierHash = root.getString("verifierHash"),
                records = recordsList,
                checksum = reportedChecksum
            )
            Result.success(pkg)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
