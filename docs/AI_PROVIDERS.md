# AI Providers

## Overview

Hesabyar supports multiple AI providers for natural language parsing and budget advice. AI is optional — all core features work offline.

---

## Supported Providers

### Google Gemini

- **Type:** `GEMINI`
- **API:** Google Generative Language API
- **Auth:** API key (Gemini API key)
- **Default model:** `gemini-2.0-flash`

### OpenRouter

- **Type:** `OPENROUTER`
- **API:** OpenAI-compatible chat completions
- **Auth:** Bearer token
- **Models:** Any model available on OpenRouter

### Custom Endpoint

- **Type:** `CUSTOM`
- **API:** OpenAI-compatible chat completions
- **Auth:** Bearer token
- **Base URL:** User-provided endpoint

---

## Configuration

AI configs are stored in `EncryptedSharedPreferences` (AES-256-GCM).

Each config contains:

- `id` — UUID
- `providerType` — GEMINI, OPENROUTER, or CUSTOM
- `apiKey` — API key or token
- `model` — Model identifier
- `baseUrl` — Endpoint URL (OpenRouter/Custom only)
- `label` — User-friendly display name

Multiple configs can be stored. One is marked as active.

---

## Online vs Offline Mode

### Online Mode

Uses the configured AI provider for:

- Smart text parsing (Persian → structured transaction)
- Budget advice generation
- Budget forecast

### Offline Mode

Falls back to local rule-based parsing:

- `PersianAmountParser` — Token-based amount extraction
- `MoneyDetector` — Determines if text contains money
- `GeminiParser.parseSentenceOffline()` — Category inference + type detection
- `BudgetAdvisor.getOfflineAdvice()` — Rule-based financial advice
- `BudgetAdvisor.getOfflineForecast()` — Rule-based forecast

---

## Parsing Pipeline

### Online Flow

```
User Text → GeminiParser.parseSentence()
         → AiProvider.generateContent() [API call]
         → JSON response parsed
         → ParsedResult returned
         → User confirms → Saved to Room
```

### Offline Flow

```
User Text → GeminiParser.parseSentenceOffline()
         → MoneyDetector.containsMoney() [gate check]
         → PersianAmountParser.parseAmount() [tokenize + interpret]
         → Category inference (keyword matching)
         → Type detection (income/expense/loan/installment)
         → Description generation
         → ParsedResult returned
         → User confirms → Saved to Room
```

---

## PersianAmountParser

Token-based parser for extracting amounts from Persian text.

### Pipeline

1. **Normalize** — Convert Arabic/Persian digits to ASCII, remove separators
2. **Tokenize** — Character-by-character scanner producing Number and Unit tokens
3. **Interpret** — Three paths:
   - `interpretWithUnits` — Explicit units (میلیون, هزار, etc.)
   - `interpretShorthand` — Position-based ("5 و 400" = 5M + 400K)
   - `interpretBareLast` — Fallback to last number

### Unit Cascade

When a number appears after a unit, the next number without a unit inherits a lower unit:

- `1 میلیارد 250 میلیون` → 1B + 250M
- `5 میلیون 400 هزار` → 5M + 400K

---

## MoneyDetector

Gates amount parsing to prevent false positives.

**Detects:**

- Unit words: هزار, میلیون, میلیارد, تومان, تومن
- Context keywords: خریدم, پرداخت, هزینه, حقوق, درآمد, قرض, وام, etc.

Returns `false` for non-money sentences (time, PIN codes, etc.).

---

## Caching Strategy

### Forecast & Advice Cache

- Cached in SharedPreferences (plain text)
- Cache duration: 10 minutes
- Invalidated when data signature changes
- Data signature: `txCount|txTotal|loanCount|instCount|catCount`

### Model Cache

- Cached in EncryptedSharedPreferences
- Duration: 24 hours
- Per-provider cache entries

---

## Security

- API keys stored in EncryptedSharedPreferences (AES-256-GCM)
- Keys never logged in plain text
- Legacy plaintext SharedPreferences migrated and cleared
- No API keys in source code (use .env for build secrets)
