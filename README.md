# حسابیار (Hesabyar)

Persian-first personal finance assistant for Android.

## Features

- **Transaction Management**: Track income and expenses with categories
- **Loan & Debt Tracking**: Manage debts and credits with people
- **Installment Management**: Track recurring payments with reminders
- **AI-Powered Parsing**: Natural language Persian input for quick entry
- **Budget Advisor**: AI-powered financial insights and recommendations
- **Analytics Dashboard**: Monthly spending, category breakdown, debt overview
- **Backup & Restore**: JSON-based backup with replace/merge modes
- **Excel Export**: Export reports to .xlsx format
- **Offline-First**: All core features work without internet
- **Jalali Calendar**: Full Persian calendar support

## Tech Stack

- Kotlin, Jetpack Compose, Material 3
- Room Database (SQLite)
- Navigation Compose
- Kotlin Coroutines + Flow
- WorkManager (background reminders)
- OkHttp + Retrofit (networking)
- Firebase AI / OpenRouter / Custom AI providers
- Robolectric + Roborazzi (testing)

## Build

```bash
# Debug build
./gradlew installDebug

# Run tests
./gradlew test

# Lint check
./gradlew lint
```

## Environment Setup

1. Copy `.env.example` to `.env`
2. Set `GEMINI_API_KEY` for AI features (optional)
3. For release builds, set signing credentials in `.env`

## Project Structure

```
app/src/main/java/io/github/mojri/hesabyar/
├── api/           # AI providers, parsers, budget advisor
├── data/          # Room entities, DAOs, repository, backup, Excel export
├── reminder/      # WorkManager workers, notification helpers
└── ui/            # Compose screens, ViewModels, theme
```

## Testing

```bash
# All unit tests
./gradlew test

# Single test class
./gradlew test --tests "io.github.mojri.hesabyar.OfflineParserTest"
```

## License

Private - All rights reserved.
