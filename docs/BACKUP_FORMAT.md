# Backup Format

## Overview

Hesabyar uses a JSON-based backup format. Backups contain all user data and can be restored via the Settings screen.

---

## JSON Structure

```json
{
  "version": 4,
  "timestamp": 1700000000000,
  "appVersion": "1.0",
  "categories": [...],
  "transactions": [...],
  "loans": [...],
  "installments": [...],
  "paymentHistories": [...],
  "settings": {
    "darkMode": true
  }
}
```

---

## Fields

### Root

| Field       | Type   | Description                    |
|-------------|--------|--------------------------------|
| version     | Int    | Backup format version (current: 4; older versions restore; missing optional link fields default to null, and omitted collections default to empty arrays) |
| timestamp   | Long   | Backup creation time (ms)      |
| appVersion  | String | App version that created backup |
| categories  | Array  | Category records               |
| transactions| Array  | Transaction records            |
| loans       | Array  | Loan records                   |
| installments| Array  | Installment records            |
| paymentHistories | Array | Payment history records    |
| settings    | Object | App settings                   |

### Transaction

```json
{
  "id": 1,
  "type": "EXPENSE",
  "categoryId": 1,
  "amount": 5000000,
  "description": "خرید مواد غذایی",
  "personName": "",
  "date": 1700000000000,
  "dueDate": 0,
  "installmentId": 0,
  "loanId": null
}
```

`loanId` (optional) is set only on the disbursement row of a tracked loan:
the transaction `insertLoanWithInitial` created for that loan.

### Loan

```json
{
  "id": 1,
  "personName": "علی",
  "type": "DEBTOR",
  "originalAmount": 10000000,
  "remainingAmount": 5000000,
  "description": "قرض دادن به علی",
  "date": 1700000000000,
  "isSettled": false
}
```

### Installment

```json
{
  "id": 1,
  "title": "قسط ماشین",
  "amount": 2000000,
  "dueDate": 1700000000000,
  "isPaid": false,
  "reminderEnabled": true,
  "notes": ""
}
```

### PaymentHistory

```json
{
  "id": 1,
  "loanId": 1,
  "amount": 1000000,
  "date": 1700000000000,
  "notes": "بازپرداخت جزئی",
  "linkedTransactionId": 12
}
```

`linkedTransactionId` (optional) is the id of the ledger transaction a
tracked repayment generated.

### Stable loan links

`transactions[].loanId` and `paymentHistories[].linkedTransactionId` let
`deleteLoan` remove exactly the loan's own ledger rows instead of matching on
person, amount and date (issues #242 and #430). Both are optional:

- Missing, `null`, `0`, a negative or fractional number, or any value other
  than a positive integer (number or digit-only string) means "no link"; the row falls back
  to the legacy matching on delete. Backups written before these keys existed
  restore unchanged.
- Every restore path (REPLACE, MERGE and the raw-list `importBackup`) first
  checks the links inside the backup. `loanId` must name a loan in the same
  backup. `linkedTransactionId` must name a transaction in the same backup
  that is not a loan disbursement, that no other payment claims, and whose
  amount equals the payment's amount (a generated repayment always copies
  its payment's amount at creation). Rows that share an id lose their link.
  Anything else is dropped to `null`.
- **REPLACE** and `importBackup` keep source ids, so surviving links stay
  as-is. Dropping dangling ones matters because SQLite can reuse a rowid for
  an unrelated row later.
- **MERGE** re-points each link to the merged row's new id. A link to a loan or
  transaction the merge skipped becomes `null`.

### Category

```json
{
  "id": 1,
  "name": "خوراک",
  "key": "Food",
  "icon": "Restaurant",
  "color": 1308622848,
  "type": "EXPENSE",
  "isDefault": true
}
```

---

## Restore Modes

### REPLACE

Deletes all existing data and replaces with backup contents.

**Use when:** Restoring from a full backup or switching devices.

### MERGE

Updates existing categories by key, appends new transactions/loans/installments.

**Use when:** Combining data from multiple sources.

---

## Validation Rules

- Version must be ≥ 1
- Transaction amounts must be > 0
- Transaction types must be "EXPENSE" or "INCOME"
- Loan amounts must be > 0
- Loan types must be "DEBTOR" or "CREDITOR"
- Installment amounts must be > 0

Invalid backups are rejected with error messages before restore.

---

## Compatibility

- Backups from v1.x are compatible with current version
- Older backups without categories field are handled gracefully (default categories used)
- All monetary values are in Rial (Long)
