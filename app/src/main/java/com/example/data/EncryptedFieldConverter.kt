package com.example.data

import androidx.room.TypeConverter
import org.json.JSONObject

/**
 * Room TypeConverter for [EncryptedField] objects.
 * Serializes and deserializes the ciphertext and initialization vector (IV) pair
 * into a compact JSON string representation suitable for SQLite text columns.
 */
class EncryptedFieldConverter {

    @TypeConverter
    fun fromEncryptedField(field: EncryptedField?): String? {
        if (field == null) return null
        return try {
            val json = JSONObject()
            json.put("ct", field.ciphertext)
            json.put("iv", field.iv)
            json.toString()
        } catch (e: Exception) {
            "${field.ciphertext}:${field.iv}"
        }
    }

    @TypeConverter
    fun toEncryptedField(value: String?): EncryptedField? {
        if (value.isNullOrBlank()) return null
        return try {
            if (value.startsWith("{") && value.endsWith("}")) {
                val json = JSONObject(value)
                EncryptedField(
                    ciphertext = json.getString("ct"),
                    iv = json.getString("iv")
                )
            } else if (value.contains(":")) {
                val parts = value.split(":", limit = 2)
                EncryptedField(ciphertext = parts[0], iv = parts[1])
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
