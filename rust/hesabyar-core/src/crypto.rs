use aes_gcm::{
    aead::{Aead, KeyInit, OsRng},
    Aes256Gcm, Nonce,
};
use sha2::{Digest, Sha256};

use crate::models::HesabyarError;

/// Magic header for encrypted backup files.
pub const BACKUP_MAGIC: &str = "HESABYAR_BACKUP_V1";

/// Length of the AES-256-GCM nonce (12 bytes).
const NONCE_LEN: usize = 12;

/// Length of the AES-256 key (32 bytes).
pub const KEY_LEN: usize = 32;

/// Encrypt a JSON backup string using AES-256-GCM.
///
/// # Arguments
/// * `json` - The backup JSON string to encrypt
/// * `key` - A 32-byte AES-256 key
///
/// # Returns
/// Encrypted bytes: `[12-byte nonce][ciphertext + 16-byte auth tag]`
///
/// # Security
/// - Uses a random nonce for each encryption operation
/// - AES-256-GCM provides authenticated encryption (confidentiality + integrity)
/// - The nonce is prepended to the ciphertext for the receiver
pub fn encrypt_backup(json: &str, key: &[u8]) -> Result<Vec<u8>, HesabyarError> {
    if key.len() != KEY_LEN {
        return Err(HesabyarError::CryptoError {
            detail: format!("Invalid key length: expected {} bytes, got {}", KEY_LEN, key.len()),
        });
    }

    let cipher = Aes256Gcm::new_from_slice(key).map_err(|e| HesabyarError::CryptoError {
        detail: format!("Failed to create cipher: {}", e),
    })?;

    let nonce_bytes: [u8; NONCE_LEN] = rand::random();
    let nonce = Nonce::from_slice(&nonce_bytes);

    let ciphertext = cipher.encrypt(nonce, json.as_bytes()).map_err(|e| HesabyarError::CryptoError {
        detail: format!("Encryption failed: {}", e),
    })?;

    // Output: [nonce (12 bytes)][ciphertext + auth tag (16 bytes)]
    let mut output = Vec::with_capacity(NONCE_LEN + ciphertext.len());
    output.extend_from_slice(&nonce_bytes);
    output.extend_from_slice(&ciphertext);

    Ok(output)
}

/// Decrypt an encrypted backup using AES-256-GCM.
///
/// # Arguments
/// * `data` - Encrypted bytes: `[12-byte nonce][ciphertext + auth tag]`
/// * `key` - A 32-byte AES-256 key
///
/// # Returns
/// Decrypted JSON string
///
/// # Security
/// - Verifies authentication tag (tamper detection)
/// - Fails if data is corrupted or wrong key is used
pub fn decrypt_backup(data: &[u8], key: &[u8]) -> Result<String, HesabyarError> {
    if key.len() != KEY_LEN {
        return Err(HesabyarError::CryptoError {
            detail: format!("Invalid key length: expected {} bytes, got {}", KEY_LEN, key.len()),
        });
    }

    if data.len() < NONCE_LEN {
        return Err(HesabyarError::CryptoError {
            detail: "Encrypted data too short".to_string(),
        });
    }

    let (nonce_bytes, ciphertext) = data.split_at(NONCE_LEN);

    let cipher = Aes256Gcm::new_from_slice(key).map_err(|e| HesabyarError::CryptoError {
        detail: format!("Failed to create cipher: {}", e),
    })?;

    let nonce = Nonce::from_slice(nonce_bytes);

    let plaintext = cipher.decrypt(nonce, ciphertext).map_err(|e| HesabyarError::CryptoError {
        detail: format!("Decryption failed (wrong key or corrupted data): {}", e),
    })?;

    String::from_utf8(plaintext).map_err(|e| HesabyarError::CryptoError {
        detail: format!("Decrypted data is not valid UTF-8: {}", e),
    })
}

/// Compute SHA-256 checksum of data.
///
/// Returns a 64-character hexadecimal string.
pub fn compute_checksum(data: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(data);
    let result = hasher.finalize();
    hex_encode(&result)
}

/// Verify SHA-256 checksum of data.
///
/// Returns `true` if the computed checksum matches the expected value.
/// Uses constant-time comparison to prevent timing attacks.
pub fn verify_checksum(data: &[u8], expected: &str) -> bool {
    let computed = compute_checksum(data);
    constant_time_eq(computed.as_bytes(), expected.as_bytes())
}

/// Encode bytes as hexadecimal string.
fn hex_encode(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{:02x}", b)).collect()
}

/// Constant-time string comparison to prevent timing attacks.
fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    let mut result = 0u8;
    for (x, y) in a.iter().zip(b.iter()) {
        result |= x ^ y;
    }
    result == 0
}

/// Build an encrypted backup file with header, checksum, and encrypted data.
///
/// File format:
/// ```text
/// HESABYAR_BACKUP_V1\n
/// <64-char SHA-256 hex>\n
/// <encrypted binary data>
/// ```
///
/// The checksum is computed over the original JSON (before encryption),
/// allowing integrity verification before decryption.
pub fn build_encrypted_backup_file(json: &str, key: &[u8]) -> Result<Vec<u8>, HesabyarError> {
    let encrypted = encrypt_backup(json, key)?;
    let checksum = compute_checksum(json.as_bytes());

    let mut output = Vec::new();
    output.extend_from_slice(BACKUP_MAGIC.as_bytes());
    output.push(b'\n');
    output.extend_from_slice(checksum.as_bytes());
    output.push(b'\n');
    output.extend_from_slice(&encrypted);

    Ok(output)
}

/// Parse an encrypted backup file, verifying checksum and decrypting.
///
/// Returns the decrypted JSON string.
pub fn parse_encrypted_backup_file(data: &[u8], key: &[u8]) -> Result<String, HesabyarError> {
    // Find the first newline (end of magic header)
    let first_newline = data.iter().position(|&b| b == b'\n').ok_or_else(|| {
        HesabyarError::CryptoError {
            detail: "Invalid backup file format: missing header".to_string(),
        }
    })?;

    // Parse magic header (must be valid UTF-8)
    let magic = std::str::from_utf8(&data[..first_newline]).map_err(|e| {
        HesabyarError::CryptoError {
            detail: format!("Invalid header encoding: {}", e),
        }
    })?;

    if magic != BACKUP_MAGIC {
        return Err(HesabyarError::CryptoError {
            detail: format!("Invalid backup file magic: expected '{}', got '{}'", BACKUP_MAGIC, magic),
        });
    }

    // Find the second newline (end of checksum)
    let after_magic = &data[first_newline + 1..];
    let second_newline_offset = after_magic.iter().position(|&b| b == b'\n').ok_or_else(|| {
        HesabyarError::CryptoError {
            detail: "Invalid backup file format: missing checksum separator".to_string(),
        }
    })?;

    // Parse checksum (must be valid UTF-8)
    let checksum = std::str::from_utf8(&after_magic[..second_newline_offset]).map_err(|e| {
        HesabyarError::CryptoError {
            detail: format!("Invalid checksum encoding: {}", e),
        }
    })?;

    if checksum.len() != 64 {
        return Err(HesabyarError::CryptoError {
            detail: format!("Invalid checksum length: expected 64, got {}", checksum.len()),
        });
    }

    // Remaining bytes are the encrypted data (binary, not UTF-8)
    let encrypted_data = &after_magic[second_newline_offset + 1..];

    // Decrypt first (this also verifies the auth tag)
    let json = decrypt_backup(encrypted_data, key)?;

    // Then verify the checksum over the original JSON
    if !verify_checksum(json.as_bytes(), checksum) {
        return Err(HesabyarError::CryptoError {
            detail: "Checksum mismatch: backup data may be corrupted".to_string(),
        });
    }

    Ok(json)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_key() -> [u8; KEY_LEN] {
        [
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
            0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10,
            0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
            0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20,
        ]
    }

    // =====================================================================
    // Encrypt/Decrypt round-trip
    // =====================================================================

    #[test]
    fn test_encrypt_decrypt_round_trip() {
        let key = test_key();
        let json = r#"{"version":1,"timestamp":1710000000000,"app_version":"1.0","transactions":[],"loans":[],"installments":[],"categories":[]}"#;

        let encrypted = encrypt_backup(json, &key).unwrap();
        let decrypted = decrypt_backup(&encrypted, &key).unwrap();

        assert_eq!(json, decrypted);
    }

    #[test]
    fn test_encrypt_produces_different_ciphertext_each_time() {
        let key = test_key();
        let json = r#"{"test": "data"}"#;

        let enc1 = encrypt_backup(json, &key).unwrap();
        let enc2 = encrypt_backup(json, &key).unwrap();

        // Random nonce means different ciphertext each time
        assert_ne!(enc1, enc2);
    }

    #[test]
    fn test_decrypt_wrong_key_fails() {
        let key1 = test_key();
        let mut key2 = test_key();
        key2[0] ^= 0xff; // Flip bits

        let json = r#"{"test": "data"}"#;
        let encrypted = encrypt_backup(json, &key1).unwrap();

        let result = decrypt_backup(&encrypted, &key2);
        assert!(result.is_err());
    }

    #[test]
    fn test_decrypt_tampered_data_fails() {
        let key = test_key();
        let json = r#"{"test": "data"}"#;

        let mut encrypted = encrypt_backup(json, &key).unwrap();
        // Tamper with the ciphertext (after the nonce)
        if encrypted.len() > NONCE_LEN + 5 {
            encrypted[NONCE_LEN + 5] ^= 0xff;
        }

        let result = decrypt_backup(&encrypted, &key);
        assert!(result.is_err());
    }

    #[test]
    fn test_invalid_key_length() {
        let short_key = [0u8; 16]; // Wrong length
        let result = encrypt_backup("test", &short_key);
        assert!(result.is_err());
    }

    #[test]
    fn test_empty_json() {
        let key = test_key();
        let json = "";
        let encrypted = encrypt_backup(json, &key).unwrap();
        let decrypted = decrypt_backup(&encrypted, &key).unwrap();
        assert_eq!(json, decrypted);
    }

    #[test]
    fn test_persian_json() {
        let key = test_key();
        let json = r#"{"description":"خرید نان صبحانه","amount":50000}"#;
        let encrypted = encrypt_backup(json, &key).unwrap();
        let decrypted = decrypt_backup(&encrypted, &key).unwrap();
        assert_eq!(json, decrypted);
    }

    // =====================================================================
    // Checksum tests
    // =====================================================================

    #[test]
    fn test_compute_checksum_deterministic() {
        let data = b"hello world";
        let c1 = compute_checksum(data);
        let c2 = compute_checksum(data);
        assert_eq!(c1, c2);
    }

    #[test]
    fn test_compute_checksum_length() {
        let checksum = compute_checksum(b"test");
        assert_eq!(checksum.len(), 64); // SHA-256 = 32 bytes = 64 hex chars
    }

    #[test]
    fn test_verify_checksum_correct() {
        let data = b"important data";
        let checksum = compute_checksum(data);
        assert!(verify_checksum(data, &checksum));
    }

    #[test]
    fn test_verify_checksum_incorrect() {
        let data = b"important data";
        let wrong_checksum = "0000000000000000000000000000000000000000000000000000000000000000";
        assert!(!verify_checksum(data, wrong_checksum));
    }

    #[test]
    fn test_verify_checksum_wrong_length() {
        let data = b"test";
        assert!(!verify_checksum(data, "tooshort"));
    }

    // =====================================================================
    // Encrypted backup file format tests
    // =====================================================================

    #[test]
    fn test_build_and_parse_encrypted_backup_file() {
        let key = test_key();
        let json = r#"{"version":1,"timestamp":1710000000000,"app_version":"1.0"}"#;

        let file_data = build_encrypted_backup_file(json, &key).unwrap();
        let parsed_json = parse_encrypted_backup_file(&file_data, &key).unwrap();

        assert_eq!(json, parsed_json);
    }

    #[test]
    fn test_encrypted_backup_file_has_magic_header() {
        let key = test_key();
        let json = r#"{"test": true}"#;

        let file_data = build_encrypted_backup_file(json, &key).unwrap();
        let header = std::str::from_utf8(&file_data[..BACKUP_MAGIC.len()]).unwrap();
        assert_eq!(header, BACKUP_MAGIC);
    }

    #[test]
    fn test_parse_encrypted_backup_wrong_magic() {
        let key = test_key();
        let data = b"WRONG_MAGIC\nchecksum\nencrypted";

        let result = parse_encrypted_backup_file(data, &key);
        assert!(result.is_err());
    }

    #[test]
    fn test_parse_encrypted_backup_wrong_key() {
        let key1 = test_key();
        let mut key2 = test_key();
        key2[0] ^= 0xff;

        let json = r#"{"test": true}"#;
        let file_data = build_encrypted_backup_file(json, &key1).unwrap();

        let result = parse_encrypted_backup_file(&file_data, &key2);
        assert!(result.is_err());
    }

    // =====================================================================
    // Edge cases
    // =====================================================================

    #[test]
    fn test_encrypt_large_payload() {
        let key = test_key();
        // 1000 transactions worth of JSON
        let json: String = format!(
            r#"{{"transactions":[{}]}}"#,
            (0..1000)
                .map(|i| format!(r#"{{"id":{},"amount":{}}}"#, i, i * 1000))
                .collect::<Vec<_>>()
                .join(",")
        );

        let encrypted = encrypt_backup(&json, &key).unwrap();
        let decrypted = decrypt_backup(&encrypted, &key).unwrap();
        assert_eq!(json, decrypted);
    }

    #[test]
    fn test_constant_time_eq_equal() {
        assert!(constant_time_eq(b"hello", b"hello"));
    }

    #[test]
    fn test_constant_time_eq_different() {
        assert!(!constant_time_eq(b"hello", b"world"));
    }

    #[test]
    fn test_constant_time_eq_different_lengths() {
        assert!(!constant_time_eq(b"hello", b"hello world"));
    }
}
