# Security Improvements

## API Key Storage

### Before

- API keys stored in plain `SharedPreferences`
- readable by any app with root access

### After

- API keys stored in `EncryptedSharedPreferences`
- AES-256-GCM encryption for values
- AES-256-SIV for key encryption
- Automatic migration from legacy plain prefs

**Implementation:** `AiProviderConfig.kt` → `AiConfigManager`

---

## Build Secrets

- `.env` file for build-time secrets (API keys, keystore passwords)
- `.env` is git-ignored — never committed to repository
- `.env.example` provides placeholder template
- Secrets Gradle Plugin maps `.env` → `BuildConfig` fields
- Keystore file (`my-upload-key.jks`) is git-ignored

---

## Data Storage

### Room Database

- SQLite database stored in app-private directory
- Encrypted on disk via SQLCipher (`SupportOpenHelperFactory` in `AppDatabase.getDatabase`); see `data/DatabaseKeyManager.kt`
- Access restricted to app process only

### Backups

- JSON format, plain text by default
- Stored in user-selected location via SAF
- Sensitive fields encrypted with passphrase AES-GCM via `auth/BackupCipher.kt` when the user sets a passphrase (person `phone` and `notes`, and account `cardNumber`, `accountNumber`, and `iban`)

---

## Network Security

- All API calls use HTTPS
- OkHttp configured with 30s connect / 60s read timeouts
- No certificate pinning (future enhancement)
- No sensitive data in URL parameters (API key in header for OpenRouter/Custom)

---

## Authentication & App Lock

- App locking with PIN or Biometrics (`BiometricPrompt` via `auth/BiometricHelper.kt`)
- PIN hashed with salt and stored in `EncryptedSharedPreferences` (`auth/PinStorage.kt`)

---

## What's NOT Encrypted

1. **Plaintext backup fields** — Free-text installment notes and payment-history notes (unlike person notes, which are encrypted), transaction descriptions and amounts, loan and installment amounts, bank-loan amounts (received amount, monthly installment amount, total repayable amount, total interest), account names and initial balances, person names, and category names stay plaintext. Only person phone and person notes, plus account card number, account number, and IBAN, are encrypted via `auth/BackupCipher.kt`.
2. **SharedPreferences** — Non-sensitive app preferences and reminder config (sensitive tokens and keys use `EncryptedSharedPreferences`)

---

## Future Security Enhancements

- [ ] Full payload backup encryption
- [ ] Certificate pinning
- [ ] ProGuard/R8 minification and obfuscation
- [ ] Root detection
