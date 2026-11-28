# R8 / ProGuard Hardening Rules for The Black-Box

# Preserve stack trace line numbers with sanitized source file names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve Kotlin metadata, generics, and annotations for reflection/serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class kotlin.Metadata { *; }

# Keep Vault Data Models, Room Entities, TypeConverters, and Security Payloads
-keep class com.example.model.** { *; }
-keep class com.example.data.** { *; }
-keep class com.example.autofill.** { *; }
-keep class com.example.security.VoiceprintTemplate { *; }
-keep class com.example.security.SnapshotPackage { *; }
-keep class com.example.security.EncryptedShareCapsule { *; }
-keep class com.example.security.DecryptedSharedItem { *; }
-keep class com.example.security.BlackBoxVaultMaintenanceJobService { *; }
-keep class com.example.nfc.** { *; }
-keep class com.example.widget.** { *; }
-keep class com.example.BlackBoxApplication { *; }

# AndroidX Room Database & Generated Implementations
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
}
-dontwarn androidx.room.paging.**

# Moshi JSON Serialization (kept for forward-compatible release builds)
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.FromJson <methods>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.ToJson <methods>;
}
-dontwarn com.squareup.moshi.**

# OkHttp3 & Okio (HIBP k-Anonymity Range Queries)
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# AndroidX BiometricPrompt & Hardware Keystore Support
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
