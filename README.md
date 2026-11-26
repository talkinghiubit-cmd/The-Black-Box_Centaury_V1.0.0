# The Black-Box

**The Black-Box** is an offline-first, end-to-end encrypted Android password manager and secure vault built with **Kotlin**, **Jetpack Compose**, **Material Design 3**, and **Room**. All sensitive records, binary attachments, and biometric key wrappers remain encrypted on-device with zero cloud dependency.

---

## Key Features

### Cryptographic Vault & Local Storage
- **AES-256-GCM Encryption:** All vault items (Logins, Secure Notes, Payment Cards, SSH & Keys) and file attachments are encrypted using `AES/GCM/NoPadding` with a fresh 12-byte random IV and 128-bit authentication tag per record.
- **PBKDF2-HMAC-SHA256 Key Derivation:** Master keys are derived from the user's master password using `PBKDF2WithHmacSHA256` (100,000 iterations, 256-bit key, 16-byte random salt).
- **Encrypted Room Persistence (Schema v6):** Dedicated entities and DAOs for vault records, passwords, secure notes, encrypted binary attachments, and vault metadata (`BlackBoxDatabase`), backed by Room `TypeConverter`s (`EncryptedFieldConverter`, `EncryptedBlobConverter`).
- **Duress / Decoy Vault Partition:** Supports dual vault partitions (`Primary` and `Duress / Decoy`) to isolate sensitive records under coercion.
- **Backup Exclusion & Privacy Shield:** Disables Android cloud backups (`android:allowBackup="false"`, `backup_rules.xml`, `data_extraction_rules.xml`), blurs/obscures contents when backgrounded (`PrivacyShieldOverlay`), and enforces configurable inactivity auto-lock (`InactivityLockManager`).

### Multi-Modal Biometric Unlock
- **Hardware-Backed Fingerprint / Face Unlock:** Uses `androidx.biometric` (`BIOMETRIC_STRONG`) paired with a `CryptoObject` cipher backed by `AndroidKeyStore` to unwrap the vault key strictly upon cryptographic authentication.
- **On-Device Voice Biometric Recognition:** Real-time 16 kHz PCM `AudioRecord` capture and on-device DSP verification (`VoiceBiometricEngine`) combining Voice Activity Detection (VAD), pitch estimation, and MFCC/spectral speaker embeddings—with AndroidKeyStore template protection and a 5-attempt lockout policy.

### Import, Export & Secure Sharing
- **Google Chrome & CSV Password Import:** Streaming RFC-4180 CSV parser (`CsvPasswordImporter`) with zero-permission Storage Access Framework (SAF) file selection, header auto-detection (Google Chrome, Edge/Brave, Firefox, Safari, Bitwarden, 1Password, LastPass, and generic CSV), credential deduplication, and 10 MB / 10,000-row safety ceilings.
- **Encrypted Vault Snapshots (`.blackbox`):** Creates and restores integrity-checked (SHA-256) encrypted snapshots (`SnapshotManager`).
- **Self-Decrypting Offline HTML Export:** Exports full or selective vault items into a standalone, encrypted HTML file (`HtmlVaultExporter`) that decrypts locally in any offline browser.
- **Ephemeral Secure Sharing:** Encrypted payload sharing with burn-after-reading tracking (`SecureShareManager`).

### Security Health & Password Auditing
- **Offline-First k-Anonymity Breach Checks:** Queries the Have I Been Pwned range API (`BreachChecker`) using only the first 5 characters of the SHA-1 hash (`Add-Padding` enabled) when online, and caches verified ranges locally via `OfflineHibpStore` (plus optional air-gapped SHA-1 dataset import) so repeated checks work offline.
- **Vault Health Dashboard:** Audits password entropy, reuse, breach exposure, and age, paired with a CSPRNG password generator (`SecurePasswordGenerator`).

---

## Architecture

The codebase follows a modular **MVVM** architecture separated by responsibility under `com.example`:

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # Edge-to-edge host activity, SAF launchers, biometric activity context
├── model/
│   └── VaultModels.kt               # Domain models (VaultItem, VaultCategory, VaultAttachment, VaultHealthReport)
├── data/
│   ├── BlackBoxDatabase.kt          # Room database (v6) & MIGRATION_5_6
│   ├── VaultEntities.kt             # Room entities (VaultRecord, Password, SecureNote, Attachment, Metadata)
│   ├── EncryptedFieldConverter.kt   # Room TypeConverters for encrypted strings/fields
│   ├── EncryptedBlobConverter.kt    # Room TypeConverters for encrypted binary blobs
│   ├── VaultDao.kt / PasswordDao.kt / SecureNoteDao.kt / VaultMetadataDao.kt
│   └── VaultRepository.kt           # Transactional encryption/decryption, batch CSV import, biometric & voice flows
├── security/
│   ├── BlackBoxSecurity.kt          # PBKDF2, AES-256-GCM, AndroidKeyStore wrapping, SHA-1/SHA-256 helpers
│   ├── AESGCMEncryptionUtility.kt   # Low-level AES-GCM cipher utility
│   ├── BiometricAuthHelper.kt / BiometricManagerWrapper.kt / BiometricVaultAuthService.kt / BiometricVaultAuthenticator.kt
│   ├── VoiceBiometricEngine.kt      # 16 kHz PCM capture, VAD, FFT/MFCC extraction & cosine matching
│   ├── CsvPasswordImporter.kt       # Streaming RFC-4180 CSV parser & duplicate fingerprinting
│   ├── BreachChecker.kt             # Offline-first HIBP k-Anonymity client & local prefix matcher
│   ├── OfflineHibpStore.kt          # Persistent local HIBP prefix cache & offline SHA-1 dataset importer
│   ├── HtmlVaultExporter.kt         # Standalone self-decrypting HTML vault generator
│   ├── SnapshotManager.kt           # .blackbox encrypted snapshot import/export
│   ├── SecureShareManager.kt        # Ephemeral encrypted share packages & burn tracking
│   ├── AttachmentStorageManager.kt  # Encrypted local file attachment storage
│   ├── SecurePasswordGenerator.kt   # Cryptographically secure password/passphrase generator
│   └── InactivityLockManager.kt     # Session timeout & auto-lock monitor
└── ui/
    ├── VaultViewModel.kt            # StateFlow UI state management, lock/unlock, CSV preview, voice/health state
    ├── VaultViewModelLifecycleObserver.kt
    ├── screens/                     # LockScreen.kt, DashboardScreen.kt
    ├── components/                  # CommonComponents.kt, PrivacyShieldOverlay.kt
    ├── dialogs/                     # ItemDetail, ItemEdit, CsvImport, BackupExport, Settings, VoiceBiometric, etc.
    └── theme/                       # Material 3 color scheme, typography, shapes
```

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language** | Kotlin 2.2.10 |
| **UI** | Jetpack Compose (BOM `2024.09.00`), Material Design 3 |
| **Database** | Room `2.7.0` with KSP (`2.3.5`) |
| **Security & Biometrics** | `androidx.biometric:1.2.0-alpha05`, `javax.crypto` (AES-256-GCM / PBKDF2), `AndroidKeyStore` |
| **Networking** | OkHttp `4.10.0`, Retrofit `2.12.0`, Moshi `1.15.2` |
| **Concurrency** | Kotlin Coroutines & Flow `1.10.2` |
| **Testing** | JUnit 4, Robolectric `4.16.1`, Kotlinx Coroutines Test |
| **SDK** | Min SDK 24 · Target & Compile SDK 36 |

---

## Permissions

- `USE_BIOMETRIC` — Hardware-backed fingerprint/face authentication.
- `RECORD_AUDIO` — Runtime-requested only when enrolling or unlocking via Voice Biometrics.
- `INTERNET` & `ACCESS_NETWORK_STATE` — Used strictly for k-Anonymity breach checks against `api.pwnedpasswords.com`.
- `VIBRATE` — Haptic feedback for vault lock/unlock actions.

*(CSV import and `.blackbox` snapshot selection use Android's zero-permission Storage Access Framework.)*

---

## Building & Running Tests

Build the debug APK:
```bash
gradle :app:assembleDebug
```

Run local JVM & Robolectric unit tests (including Room migration, cryptographic verification, voice DSP matching, and CSV importer tests):
```bash
gradle :app:testDebugUnitTest
```
