# Privacy Policy — The Black-Box

**Effective Date:** November 24, 2026  
**Application:** The Black-Box (`com.aistudio.blackbox.vltsec`)

## 1. Zero-Knowledge & Offline-First Architecture
The Black-Box is an offline-first encrypted password and secrets manager. We do **not** operate user accounts, cloud sync servers, analytics trackers, crash telemetry SDKs, or advertising SDKs.

- **Local Encryption:** All vault entries, notes, payment cards, keys, and attachments are encrypted locally on your device using AES-256-GCM with keys derived from your Master Password via PBKDF2-HMAC-SHA256 (100,000 iterations) and random 16-byte salts.
- **No Cloud Backup:** `android:allowBackup="false"` and explicit `backup_rules.xml` / `data_extraction_rules.xml` exclusions ensure encrypted vault databases and preferences are excluded from Android Auto Backup and device-to-device transfers.

## 2. Permissions & Data Handling

### Microphone (`RECORD_AUDIO`) — Optional Voice Biometrics
- Used **only** if you explicitly enroll or unlock with Voice Biometrics.
- Audio is captured in memory, converted into a 42-dimensional MFCC + pitch mathematical feature vector strictly on-device, and the raw PCM audio buffer is immediately discarded.
- No audio recordings are ever saved to disk or transmitted over the network. Your mathematical voice template is encrypted via the Android Hardware Keystore.

### Network (`INTERNET`, `ACCESS_NETWORK_STATE`) — Optional Breach Audit
- Used **only** when you manually tap to scan a password or vault via HaveIBeenPwned (HIBP).
- Uses the **k-Anonymity** model: your device computes the SHA-1 hash locally and transmits **only the first 5 hex characters** to `https://api.pwnedpasswords.com/range/{prefix}` with `Add-Padding: true`.
- Plaintext passwords and full SHA-1 hashes never leave your device. Fetched prefix ranges are cached locally for offline verification, or you may import an offline HIBP SHA-1 file directly.

### Biometrics (`USE_BIOMETRIC`)
- Biometric prompts are handled locally by Android's `BiometricPrompt` / Keystore APIs. Raw fingerprint or face data is never accessible to the app.

### Android Autofill Service (`BIND_AUTOFILL_SERVICE`) — Optional
- Only active if you opt in via Android System Settings.
- Inspects screen field hints and browser web domains strictly on-device to match stored login records and requires vault unlock authentication when the vault is locked.

## 3. Data Retention & Deletion
All vault data resides exclusively on your device. You may purge individual records, disable biometrics/voiceprints, clear the offline HIBP range cache, or wipe the entire vault inside **Settings → Erase & Reset Vault**, or uninstall the app to permanently remove all local data.

## 4. Contact
For privacy or security inquiries, contact: `talkinghiubit@gmail.com`.
