# Hesabyar – Localization and String Resources Guide

This document defines the rules for text strings, localization, and resource key architecture in Hesabyar.

## Core Principles

1. **Persian-First Application**: Hesabyar is designed primarily for Persian speakers. The default resource directory `app/src/main/res/values/strings.xml` contains Persian text. Future translations (for example, English) belong in locale-specific directories such as `values-en/strings.xml`.
2. **Zero Hardcoded Strings in UI**: Never leave hardcoded user-facing strings in UI code when reviews or static analysis tools flag them. Extract them to `strings.xml`.
3. **High Key Reusability**: Maximize key reuse across features. Common words and actions must share single reusable resource keys.

## Key Naming Conventions

All string resource keys must use `snake_case` with standardized prefix patterns:

### 1. Action Verbs (`action_*`)

Use the `action_` prefix for generic interactive buttons, chips, and clickable elements. Reuse these across all screens.

Examples:
- `action_save`: ذخیره
- `action_cancel`: انصراف
- `action_confirm`: تأیید
- `action_delete`: حذف
- `action_edit`: ویرایش
- `action_retry`: تلاش مجدد
- `action_back`: بازگشت
- `action_close`: بستن
- `action_add`: افزودن

Do not create screen-specific duplicates like `person_save_button` or `debt_cancel_action`. Use `action_save` and `action_cancel`.

### 2. Form Labels (`label_*`)

Use the `label_` prefix for text field headers, placeholders, and form field titles.

Examples:
- `label_name`: نام
- `label_amount`: مبلغ
- `label_description`: توضیحات
- `label_date`: تاریخ
- `label_category`: دسته‌بندی
- `label_account`: حساب

### 3. Error Messages (`error_*`)

Use the `error_` prefix for validation errors, network failures, and general error notices.

Examples:
- `error_empty_field`: این فیلد نمی‌تواند خالی باشد
- `error_invalid_amount`: مبلغ وارد شده نامعتبر است
- `error_network`: خطای برقراری ارتباط با شبکه
- `error_unknown`: خطای ناشناخته رخ داد

### 4. Status and Feedback Messages (`status_*` or `msg_*`)

Use for feedback snackbars, empty state displays, and loading text.

Examples:
- `status_loading`: در حال بارگذاری…
- `status_empty`: موردی یافت نشد
- `status_success`: عملیات با موفقیت انجام شد

### 5. Feature-Scoped Strings (`<feature>_*`)

When a string is unique to a specific feature or domain flow, prefix it with the feature name:

Format: `<feature>_<component_or_element>_<detail>`

Examples:
- `person_loan_timeline_empty`: هیچ وامی برای این شخص ثبت نشده است.
- `debt_hub_tab_persons`: اشخاص
- `debt_hub_tab_installments`: اقساط
- `settle_confirm_dialog_title`: تسویه کامل
- `settle_confirm_dialog_message`: آیا از تسویه کامل تمام وام‌ها و طلب‌های %1$s اطمینان دارید؟

## Parameterized Strings (Dynamic Content)

Never concatenate strings with variables in Kotlin code. Use numbered positional format specifiers:

```xml
<!-- In strings.xml -->
<string name="person_settle_confirm_message">آیا از تسویه حساب با %1$s به مبلغ %2$s اطمینان دارید؟</string>
```

```kotlin
// In Compose
Text(text = stringResource(R.string.person_settle_confirm_message, personName, formattedAmount))
```

Rules:
- Always use numbered specifiers (`%1$s`, `%2$d`) so translators can change word order.
- Format money through `CurrencyFormatter` before passing to formatted strings.
- Format dates through `JalaliCalendarHelper` or `formatPersianDate` before passing to formatted strings.

## What NOT to Localize

Do not put these in `strings.xml`:
- Log tags (`TAG = "PersonViewModel"`)
- Database column names and entity constants
- Room migration scripts
- Rust UniFFI method names and FFI types
- Internal technical error messages intended for developers in logs

## Usage in Code

### In Jetpack Compose

```kotlin
// Basic text
Text(text = stringResource(R.string.action_save))

// Formatted string
Text(text = stringResource(R.string.account_selected, accountName))
```

### In Non-Composable / ViewModels

Pass resource IDs (`@StringRes val messageRes: Int`) to the UI layer so the Composable resolves the string in the active configuration. If resolution is necessary before UI, format using Android `Context`:

```kotlin
context.getString(R.string.error_invalid_amount)
```
