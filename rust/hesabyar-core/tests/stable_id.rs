//! Backup stable-ID field: legacy parsing, v3 round-trip, serialization.
//! Integration tests live here (not in models/mod.rs) so the new coverage
//! does not grow the baselined module size.

use hesabyar_core::models::{Account, BackupPayload};

#[test]
fn v2_backup_without_stable_id_deserializes_to_none() {
    // Legacy (v2) backups predate the stableId field; parsing must not fail.
    let json = r#"{
        "version": 2,
        "timestamp": 1710000000000,
        "appVersion": "2.0.0",
        "accounts": [{"id": 1, "name": "حساب اصلی", "type": "BANK"}]
    }"#;
    let payload: BackupPayload = serde_json::from_str(json).unwrap();
    assert_eq!(payload.accounts.len(), 1);
    assert_eq!(payload.accounts[0].stable_id, None);
}

#[test]
fn v3_backup_with_stable_id_round_trips() {
    let json = r#"{
        "version": 3,
        "timestamp": 1710000000000,
        "appVersion": "3.0.0",
        "accounts": [{
            "id": 1,
            "name": "حساب اصلی",
            "type": "BANK",
            "stableId": "2e5944bd-3c3b-41c9-ad1d-773d2c3bfa3a"
        }]
    }"#;
    let payload: BackupPayload = serde_json::from_str(json).unwrap();
    assert_eq!(
        payload.accounts[0].stable_id.as_deref(),
        Some("2e5944bd-3c3b-41c9-ad1d-773d2c3bfa3a")
    );
    let reserialized = serde_json::to_string(&payload.accounts[0]).unwrap();
    assert!(reserialized.contains(r#""stableId":"2e5944bd-3c3b-41c9-ad1d-773d2c3bfa3a""#));
}

#[test]
fn none_stable_id_omits_field_when_serializing() {
    let account = Account {
        id: 1,
        name: "حساب اصلی".to_string(),
        account_type: "BANK".to_string(),
        bank_name: None,
        card_number: None,
        account_number: None,
        iban: None,
        initial_balance: 0,
        color: 0xFF4CAF50,
        icon: None,
        is_archived: false,
        display_order: 0,
        created_at: 0,
        updated_at: 0,
        stable_id: None,
    };
    let json = serde_json::to_string(&account).unwrap();
    assert!(!json.contains("stableId"));
}
