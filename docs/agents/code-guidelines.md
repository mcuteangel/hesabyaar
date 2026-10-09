# Code Guidelines (agent guide)

> Part of the agent guide. Start at `AGENTS.md`; it says when to read this file.

## Mandatory Development Guidelines

Before you write or refactor code, verify the implementation against these principles.

### 1. Modular and Reusable Architecture (DRY)

- Define shared methods, utility functions, components, state formatters, and models once in a shared package. Examples are `ui/components/`, `ui/utils/`, and `core/`. Reuse them in every screen. One-off local logic, tests, documentation, and configuration changes are exempt.
- Do not duplicate shared helper functions inside screens. Do not duplicate UI elements inside screens. If you need a logic or a UI piece in more than one place, extract it into a shared reusable module.

### 2. Strict Material Design 3 (M3) Standards

- Always use the semantic Material3 tokens. Use `MaterialTheme.colorScheme.onSurfaceVariant`, `surfaceContainerLowest`, and `MaterialTheme.typography.*`.
- Do not hardcode manual colors, magic numbers, or arbitrary color alphas. An example is `onSurface.copy(alpha = 0.5f)`. If a design token is missing, define it in the design system or the theme module. Examples are `Theme.kt` and `Color.kt`. This keeps the screens consistent in the Light and Dark themes.

### 3. Minimalist Code and Zero Redundancy

- Keep the implementation clean and minimal. Remove redundant wrapper code and dead logic.
- Scan the file for duplication and anti-patterns when you modify it. Refactor and optimize them as part of the task.


### 4. JUnit `assertEquals` Argument Order

The 3-argument `assertEquals` signature is `assertEquals(String message, expected, actual)`. It is not `assertEquals(expected, actual, String message)`. If you put the message last, there is a compile-time type mismatch (`Int` vs `String`). Always put the message first:

```kotlin
// Correct
assertEquals("Should have 2 distinct orders", 2, orders.size)

// Wrong — compile error
assertEquals(2, orders.size, "Should have 2 distinct orders")
```

### 5. Test Naming Convention (Codacy Compliance)

- Do not use backtick-quoted test names. Codacy flags `` `fun \`name with spaces\`` `` as a violation of `[a-z][a-zA-Z0-9]*`. Use camelCase:
  - Bad: `` fun `putForecast then getForecast returns same value`() ``
  - Good: `fun putForecastThenGetForecastReturnsSameValue()`
- When you touch an existing backtick test, rename it to camelCase as part of the change.
- Use camelCase names for all new test files.

## Architecture Pattern

Hesabyar uses the MVVM + UseCase architecture in a single Android module. The Rust Core (`rust/hesabyar-core`) serves as the sole location for canonical business logic. Do not describe this project as a multi-module Clean Architecture repository.

### Data Flow

Canonical business logic data flow:

```text
UI Event
    ↓
ViewModel
    ↓
UseCase
    ↓
RustBridge
    ↓
hesabyar-core
(Rust / canonical business logic)
```

Persistence data flow:

```text
UseCase
    ↓
Repository
    ↓
Room
(persistence only)
```

When a workflow requires both business calculations and persistence, the UseCase coordinates both paths. The Repository handles data storage and retrieval only. The Repository must never contain business rules or financial calculations.

### Repository vs Rust Core Boundaries

Direct Repository access is permitted only when an operation meets all of these criteria:
- It is pure CRUD (Create, Read, Update, Delete).
- It performs persistence or querying only.
- It contains no business rules.
- It contains no business calculations.
- It contains no financial validations.
- It contains no rule-driven data transformations.

The Rust Core (`rust/hesabyar-core`) is mandatory via `UseCase → RustBridge → hesabyar-core` whenever an operation involves:
- Business rules.
- Financial calculations (interest, balances, budget allocations, health score).
- Financial validations (transaction constraints, loan limits, amount limits).
- Domain calculations.
- Rule-driven data transformations.
- Canonical logic shared across callers, except for the pre-approved Kotlin fallbacks listed in `docs/architecture/ADR-001-rust-sole-implementation.md`.

## UI Constraints

### Compose Only

All new UI implementations must use Jetpack Compose. Legacy Android Views and XML layouts are forbidden for new features. Do not use legacy patterns such as `findViewById(...)`.

### Existing Components First

Before creating any new UI component:
1. Inspect `ui/components/` for reusable components.
2. Review existing screen implementations for shared patterns.
3. Review standard Material 3 components.

Create a custom component only when neither Material 3 nor `ui/components/` provides a suitable match. Place any reusable custom component in `ui/components/` following DRY principles.

### RTL and Persian-First Layout

The UI defaults to right-to-left (RTL) layout and Persian-first presentation.

Forcing left-to-right (LTR) direction via `LocalLayoutDirection provides LayoutDirection.Ltr` is permitted only in specific contexts where numerical or financial values require LTR alignment (e.g., telephone numbers, card numbers, signed percentages).

Real example from `AccountBalanceCard.kt`:

```kotlin
// Force LTR layout so the sign (±) always appears on the left of the
// percentage, regardless of the page's RTL direction.
CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
  Text(
    text = "$sign$pct% $arrow",
    style = MaterialTheme.typography.labelMedium,
    color = color,
    textAlign = TextAlign.End,
  )
}
```

### Jalali Calendar

Use `JalaliCalendarHelper.kt` for all user-facing date presentation and date calculations in the UI.

Do not use `java.time.LocalDate` or `java.util.Date` directly for user-facing financial date logic. This constraint does not forbid standard epoch millisecond timestamps used for internal database storage or serialization.

### String Resources and Localization

When code reviews or static analysis tools flag hardcoded string literals:
1. Extract user-facing string literals into `app/src/main/res/values/strings.xml`.
2. Reference strings with `stringResource(R.string.<key>)` in Compose or `context.getString(R.string.<key>)`.
3. Never keep raw hardcoded text strings in UI code when flagged.

Follow these naming and reusability rules:

- **Common Action Verbs**: Use generic action prefixes (`action_save`, `action_cancel`, `action_confirm`, `action_delete`). Reuse them across screens.
- **Generic Labels and Errors**: Use standard prefixes for common UI states (`label_name`, `label_amount`, `error_empty_field`).
- **Feature-Specific Strings**: Prefix with the feature or domain (`<feature>_<element>_<detail>`), such as `debt_hub_tab_persons`.
- **Reusable Over Specific**: Do not duplicate keys for identical text. Search `strings.xml` before adding new keys.
- **Dynamic Content**: Use format specifiers (`%1$s`, `%1$d`) instead of string concatenation.
- **Persian-First**: Store the primary Persian text in `res/values/strings.xml`.
