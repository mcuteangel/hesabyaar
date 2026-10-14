# Migration Notes

## Room Database Migrations

### v1 → v2: Double to Long (Toman to Rial)

**Date:** Initial migration

**What changed:**

- All monetary columns converted from `Double` (Toman) to `Long` (Rial)
- Values multiplied by 1000 during migration

**Affected tables and columns:**

- `transactions`: `amount`
- `loans`: `originalAmount`, `remainingAmount`
- `installments`: `amount`
- `payment_history`: `amount`

**SQL approach:** CREATE new table → INSERT with CAST(amount * 10 AS INTEGER) → DROP old → RENAME new

**Data preservation:** All existing data preserved with the 10x Toman-to-Rial multiplier

**Correction note:** The factor was wrongly 1000 in the first shipping of this migration. MIGRATION_3_4 exists to undo that error. The factor is now 10, which is the correct conversion.

---

### v3 → v4: Undo Toman-to-Rial over-inflation

**Date:** Currency correction

**What changed:**

- Amounts inflated by the faulty MIGRATION_1_2 multiply-by-1000 are divided by 100
- Correct factor: 1 Toman = 10 Rials, so stored values were 100x too large

**Affected tables and columns:**

- `transactions`: `amount`
- `loans`: `originalAmount`, `remainingAmount`
- `installments`: `amount`
- `payment_history`: `amount`

**SQL approach:** UPDATE with a bounded predicate, `column > MAX_LEGITIMATE_RIAL AND column <= MAX_INFLATED_RIAL`

**Why the bounds:** An absolute threshold cannot separate an inflated row from a legitimate large amount, because both are plain integers in the same column. The lower bound is 10 times the largest plausible Toman amount. No legitimate Rial amount exceeds it, so the correction can only touch a value that no user could have entered. The upper bound is the same largest Toman amount times the inflation factor. Above it, no amount is explainable by either source, so the row is left untouched. In practice the band is empty, so this migration is a safety net rather than an active repair.

**Row counting:** Each affected table is counted before the update, and the count is logged. This is the only record of a data correction on a user device.

**Idempotency:** Room applies each migration exactly once per database file and wraps it in a single transaction. A partial correction cannot survive.

---

### v2 → v3: Categories Table + categoryId

**Date:** Added category system

**What changed:**

- Created `categories` table with 8 default categories
- Recreated `transactions` table with `categoryId` (Long) replacing `category` (String)
- Migrated string category values to numeric IDs using CASE/WHEN mapping

**Category mapping:**

| Old String      | New categoryId |
|-----------------|---------------|
| Food            | 1             |
| Transportation  | 2             |
| Shopping        | 3             |
| Bills           | 4             |
| Installments    | 5             |
| Loans           | 6             |
| Income          | 7             |
| Other (default) | 8             |

**Data preservation:** All transactions preserved with correct category references

---

## AI Config Migration

### Legacy SharedPreferences → EncryptedSharedPreferences

**What changed:**

- AI provider configs moved from plain `SharedPreferences` to `EncryptedSharedPreferences`
- Uses AES-256-GCM encryption for values, AES-256-SIV for keys

**Migration process:**

1. Check if migration already completed (`migration_complete_v1` flag)
2. Read all data from legacy prefs
3. Write to encrypted prefs
4. Set migration flag
5. Clear legacy prefs

**Data preserved:** All AI configs, active config ID, online mode setting, model cache

---

## Breaking Changes

- **v1 → v2:** Every monetary column changed type from `Double` to `Long` and unit from Toman to Rial. Existing data is converted, not dropped.
- **v3 → v4:** Amounts inflated by the faulty MIGRATION_1_2 factor of 1000 are divided by 100. Legitimate large amounts are protected by the bounded predicate.

No migration drops a table or a column.

---

## Rollback Strategy

Room migrations are one-way. If a migration fails:

1. The app will crash on startup
2. User must uninstall and reinstall (data loss)
3. Backup/restore can recover data from a previous backup file

**Recommendation:** Always test migrations on a copy of production data before releasing.
