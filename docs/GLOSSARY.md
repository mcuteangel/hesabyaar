# Hesabyar — Domain Glossary

Terms used across the codebase, the database, and the backup format. Use these exact terms. Do not invent synonyms.

## Money

| Term | Meaning |
|------|---------|
| Rial | Base storage unit. All amounts are stored as `Long` in Rial. Never use `Float` or `Double` for money. |
| Toman | Display unit. 1 Toman = 10 Rial. The UI shows Toman. Conversion lives in `CurrencyFormatter.fromRial()` and `rust/hesabyar-core/src/currency.rs`. |
| Amount | Always means Rial in storage and in backups. The UI converts to Toman for display. |

## Transactions

| Term | Meaning |
|------|---------|
| Transaction | One income or expense record. Type is `EXPENSE` or `INCOME`. |
| Category | Transaction classification. Table `categories`. Each record has a unique English `key` (for example `Food`) and a Persian display `name`. 8 default categories exist. |
| categoryId | Foreign key from `transactions` to `categories.id`. Replaced the old string `category` column in schema v3. |
| personName | Optional person linked to a transaction (for example a loan-related payment). |
| dueDate | Optional timestamp for scheduled transactions. |
| installmentId | Optional link from a transaction to an installment record. |

## Loans and debts

| Term | Meaning |
|------|---------|
| Loan | Money lent to or borrowed from a person. Table `loans`. |
| DEBTOR | Loan type where someone owes **you** money. You are the creditor. |
| CREDITOR | Loan type where **you** owe someone money. You are the debtor. |
| originalAmount | The loan amount at creation, in Rial. |
| remainingAmount | The unpaid part, in Rial. Decreases with each repayment. |
| isSettled | True when the loan is fully repaid. |
| payment_history | Table of partial repayments. Each row links to `loans.id` through `loanId`. |

## Installments

| Term | Meaning |
|------|---------|
| Installment | A scheduled payment with a due date. Table `installments`. |
| isPaid | True when the installment is paid. |
| reminderEnabled | True when WorkManager reminders are active for this installment. |
| Reminder | A WorkManager notification. See `reminder/ReminderScheduler.kt`. |

## Accounts

| Term | Meaning |
|------|---------|
| Account | A money container (cash, bank card, and so on). Has a type and a balance. |
| Active account | The account used for new records by default. The last active account cannot be deleted. |

## Backup

| Term | Meaning |
|------|---------|
| Backup | A JSON export of all user data. Created from the Settings screen. |
| version | Backup format version. Independent from the app version and the Room schema version. |
| appVersion | The app version that created the backup. For diagnostics only. |
| REPLACE | Restore mode. Deletes all existing data. Then writes the backup contents. |
| MERGE | Restore mode. Updates categories by key. Appends new transactions, loans, and installments. |
| BACKUP_SCHEMA_VERSION | Rust constant in `hesabyar-core/src/models/mod.rs`. Single source of truth for the backup format version. |
| BackupCipher | Kotlin class that encrypts backup payloads (`auth/BackupCipher.kt`). |

## AI and parsing

| Term | Meaning |
|------|---------|
| AiProvider | Interface for AI backends. Implementations: Gemini, OpenRouter, custom endpoint. |
| Smart parsing | Extracting amount, date, and category from Persian free text. Example: «دیروز ۵۰ هزارتومن ناهار». |
| Offline fallback | Rule-based Rust parser and advisor. Used when no AI provider is reachable. The offline NLP parser is a permanent Kotlin fallback per ADR-001. |
| Money detector | Gate that decides whether a text contains a money amount before parsing. |
| Category inference | Keyword-based guess of the transaction category. |
| Budget advisor | Suggests budget actions. AI-backed, with an offline rule-based fallback. |
| Budget forecast | Predicts future spending. AI-backed, with an offline rule-based fallback. |
| ai_validation | Rust module that validates AI output before the app uses it. |

## Architecture terms

| Term | Meaning |
|------|---------|
| Rust core | `rust/hesabyar-core`. Sole location for new business logic. Exposed to Kotlin through UniFFI. |
| RustBridge | Kotlin bridge classes (`rust/RustBridge*.kt`). They call the Rust core through UniFFI. |
| UniFFI | Tool that generates Kotlin bindings from Rust. Output: `hesabyar_core.kt` (gitignored). |
| UseCase | Domain-layer class that orchestrates one user goal. Example: `ManageTransactionUseCase`. |
| Delegate | Data-layer class that holds per-entity logic. Example: `TransactionDelegate`. |
| Repository | Persistence layer. Handles storage and retrieval only. Holds no business rules. |
| Jalali calendar | The Persian calendar. All user-facing dates use it through `JalaliCalendarHelper.kt`. |
| Room schema version | Current version: 3. Independent from the backup format version. |
