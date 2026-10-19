# Plan 019: Remove Dead Design-System Tokens — 4 Remaining of 7 in Issue #456

- **Issue:** #456
- **PR:** #515
- **Created:** 2026-10-18
- **Author:** Claude Code
- **Status:** DONE (merged in PR #515)

## 1. Problem Statement

Issue #456 lists 7 bullet entries representing 10 individual design-system tokens claimed to have zero usages. A live-repository re-audit on 2026-10-18 (branch `feat/issue-456-remove-dead-tokens`) reconciles these 10 tokens: 5 were already wired by merged work (Plans 015–018), 1 was wrongly claimed dead but is actively used (`Level4`), and only 4 tokens were genuinely dead:

- **10 tokens total** across the 7 issue bullets:
  1. `Dimens.FABSize` (already wired)
  2. `Dimens.FABIconSize` (already wired)
  3. `Dimens.BottomNavClearance` (dead)
  4. `Dimens.ChipHeight` (dead)
  5. `Dimens.TabHeight` (already wired)
  6. `Dimens.SURFACE_TINT_ALPHA` (already wired)
  7. `Dimens.SECONDARY_TEXT_ALPHA` (already wired)
  8. `ElevationTokens.Level1` (dead)
  9. `ElevationTokens.Level4` (live, used at `MainNavigationBars.kt:80`)
  10. `ElevationTokens.Level5` (dead)

**Already wired (no action; cite evidence in the PR and issue comment):**

1. `Dimens.FABSize` / `Dimens.FABIconSize` (2 tokens) — wired by Plan 015 (issue #451, merged): `ui/components/HesabyarFab.kt:40,48`. Tests: `HesabyarFabTokenTest.fabDimensionsTokensMatchSpec`.
2. `Dimens.TabHeight` (1 token) — wired by Plan 016 (issue #452, merged PR #483): `ui/components/HesabyarTabRow.kt:34` (`heightIn(min = Dimens.TabHeight)`).
3. `Dimens.SURFACE_TINT_ALPHA` (1 token) — wired by Plan 018 (issue #455, PR #498): 4 call sites (`BalanceCard.kt:36`, `CategoryManagementScreen.kt:492`, `SmartAssistantScreen.kt:207,436`).
4. `Dimens.SECONDARY_TEXT_ALPHA` (1 token) — wired by Plan 018: 12 call sites (`AnalyticsScreen.kt:391`, `CustomTimePickerDialog.kt:182,236`, `InstallmentScreen.kt:424`, `SettingsScreen.kt:926`, `ReportsScreen.kt:610,734`, `SmartAssistantScreen.kt:238,474`, `AccountBreakdownCard.kt:102`, `TransactionMiniItem.kt:131`).

**Stale claims in the issue text (documented; not acted on):**

- "The app has NO BottomNav" is wrong. `MainActivity.kt:173` renders `MainBottomNavigation` in compact width. Plan 015 already recorded this in `progress.md` (Plan 015 Findings). The rename the issue asks for also already happened: `Dimens.FabClearance` (80dp) exists at `Dimens.kt:43` with 4 call sites.
- `ElevationTokens.Level4` is used at `MainNavigationBars.kt:80` (`tonalElevation` on `NavigationBar`). It is not dead.

**Still dead (the 4 tokens deleted by this plan):**

1. `Dimens.BottomNavClearance` — a `@Deprecated` alias for `FabClearance` with zero usages. The rename is long merged; the alias can go.
2. `Dimens.ChipHeight` (36dp) — zero usages. `HesabyarChip.kt` and `CategoryChip.kt` both use M3 `FilterChip`, which owns its height (M3 default 32dp). Forcing 36dp on the chips would be a visual change; issue #456 carries the `refactor` label ("Code restructuring without behavior change"). Delete the token. If a future chip-standardization issue picks 36dp, it re-adds the token with a consumer in the same PR.
3. `ElevationTokens.Level1` (1dp) — zero usages. No elevated component in the codebase takes an explicit 1dp elevation. Delete.
4. `ElevationTokens.Level5` (12dp) — zero usages. Same rationale. Delete.

Goal per the issue: "Do not leave aspirational tokens that nothing consumes."

## 2. Goals & Acceptance Criteria

- [x] **Criterion 1 (Dead tokens deleted):** `Dimens.kt` no longer contains `BottomNavClearance` or `ChipHeight`. `ElevationTokens.kt` no longer contains `Level1` or `Level5`. Verification: `grep -rn "BottomNavClearance\|ChipHeight" app/src` and `grep -rn "Level1\|Level5" app/src --include="*.kt" --include="*.java"` return zero matches.
- [x] **Criterion 2 (Live tokens untouched):** `FabClearance`, `FABSize`, `FABIconSize`, `TabHeight`, `SURFACE_TINT_ALPHA`, `SECONDARY_TEXT_ALPHA`, and `ElevationTokens.Level0/Level2/Level3/Level4` keep their exact values and KDoc. Verification: `DimensTokenTest`, `HesabyarFabTokenTest`, and the full suite still pass unchanged.
- [x] **Criterion 3 (Doc sync):** `docs/architecture/ARCHITECTURE.md` (AppElevation list, lines 459-466) lists only the remaining levels (`Level0`, `Level2`, `Level3`, `Level4`). `python scripts/check_docs.py` reports no broken references.
- [x] **Criterion 4 (No behavior change):** `git diff main...HEAD` for the fix PR touches only `app/src/main/java/io/github/mojri/hesabyar/ui/designsystem/Dimens.kt`, `app/src/main/java/io/github/mojri/hesabyar/ui/designsystem/ElevationTokens.kt`, `docs/architecture/ARCHITECTURE.md`, `plans/` metadata, and `progress.md`. No composable behavior, layout, or value changes.
- [x] **Criterion 5 (Static checks):** `./gradlew.bat ktlintFormat --no-daemon` then `./gradlew.bat ktlintCheck detekt --no-daemon` report BUILD SUCCESSFUL with 0 findings. No new baseline entries.
- [x] **Criterion 6 (Tests green):** Targeted runs of `DimensTokenTest`, `HesabyarFabTokenTest`, `HesabyarFabTest`, `HesabyarTabRowTest`, and `MainNavigationComponentsTest` pass. Full `./gradlew.bat testDebugUnitTest --no-daemon` passes with 0 failures. Raw `<testcase>` lines are pasted as evidence.
- [x] **Criterion 7 (Issue hygiene):** A comment on issue #456 lists the five already-resolved items with file:line evidence, links this PR, and states which four tokens were deleted (posted: comment #6250269870). The issue is not closed until the PR merges.

## 3. Implementation Steps

### Phase 0: Re-audit at Execution Time (guard against drift)

Prerequisite: none. Main may move before execution (#457, #458, or other PRs may land).

1. On the execution branch, re-run the usage greps and confirm each count is still zero:
   ```bash
   grep -rn "BottomNavClearance" app/src
   grep -rn "ChipHeight" app/src
   grep -rn "ElevationTokens\.Level1\b" app/src
   grep -rn "ElevationTokens\.Level5\b" app/src
   ```
2. Done criteria: every command prints only the definition file (or nothing). If any command shows a consumer, **STOP** — that token is no longer dead. Skip deleting it and note the drift in the PR.

### Phase 1: Delete the Dead Tokens

Dependency: Phase 0 passed for the tokens in this phase.

1. In `app/src/main/java/io/github/mojri/hesabyar/ui/designsystem/Dimens.kt`:
   - Delete the whole `@Deprecated` block and the `BottomNavClearance` property (current lines 41-45).
   - Delete `val ChipHeight = 36.dp` (current line 47).
   - Keep `FabClearance` with its existing KDoc (line 38-39).
2. In `app/src/main/java/io/github/mojri/hesabyar/ui/designsystem/ElevationTokens.kt`:
   - Delete `val Level1: Dp = 1.dp` (line 9).
   - Delete `val Level5: Dp = 12.dp` (line 13).
   - Keep `Level0`, `Level2`, `Level3`, `Level4` unchanged. Do not renumber.
3. Done criteria: `grep -rn "BottomNavClearance\|ChipHeight" app/src` → 0 matches. `grep -rn "ElevationTokens.Level1\|ElevationTokens.Level5\|val Level1\|val Level5" app/src/main` → 0 matches. `./gradlew.bat --no-daemon compileDebugKotlin` succeeds.

   > Note: the naive `grep -rn "Level1\|Level5" app/src/main` matches the removal-comment lines inside `ElevationTokens.kt` itself (lines 7-8) and the plan docs in `plans/`. To assert the tokens are truly gone from source, search for their qualified usage (`ElevationTokens.Level1`, `ElevationTokens.Level5`) plus their declarations (`val Level1`, `val Level5`), excluding comments. A zero result on that form confirms no consumer.

### Phase 2: Doc Sync

Dependency: Phase 1.

1. In `docs/architecture/ARCHITECTURE.md`, replace the `AppElevation` block (lines 459-466) so the list contains only `Level0`, `Level2`, `Level3`, `Level4`.
2. Run `python scripts/check_docs.py`. Fix any stale path reference it reports.
3. Done criteria: check_docs clean; the ARCHITECTURE.md block matches the code.

### Phase 3: Verification and Evidence

Dependency: Phase 1 and 2.

1. Static checks:
   ```bash
   ./gradlew.bat ktlintFormat --no-daemon
   ./gradlew.bat ktlintCheck detekt --no-daemon
   ```
   Expect BUILD SUCCESSFUL, 0 findings. Fix on touch; never re-baseline (`docs/agents/detekt.md`).
2. Targeted tests:
   ```bash
   ./gradlew.bat testDebugUnitTest --no-daemon --tests "io.github.mojri.hesabyar.ui.designsystem.DimensTokenTest" --tests "io.github.mojri.hesabyar.ui.components.HesabyarFabTokenTest" --tests "io.github.mojri.hesabyar.ui.components.HesabyarFabTest" --tests "io.github.mojri.hesabyar.ui.components.HesabyarTabRowTest" --tests "io.github.mojri.hesabyar.MainNavigationComponentsTest"
   ```
   These cover every remaining Dimens/FAB/Tab/elevation consumer. Collect the raw `<testcase>` lines with test names.
3. Full fast suite (deletions can break remote references):
   ```bash
   ./gradlew.bat testDebugUnitTest --no-daemon
   ```
   Expect BUILD SUCCESSFUL, 0 failures.
4. Done criteria: all commands pass; raw evidence (file contents of the two edited files, `<testcase>` lines, command results) is ready for the PR body per the AGENTS.md evidence standard.

### Phase 4: PR and Issue Hygiene

Dependency: Phase 3.

1. Commit with a `refactor(designsystem):` subject (never `feat` — `docs/agents/pr-conventions.md`). No version-bump confirmation flag is needed.
2. Open the PR via `scripts/new-pr.sh --title "refactor(designsystem): remove 4 dead tokens from Dimens and ElevationTokens" --body-file pr-body.md --base main`. Body sections: Summary, Changes, Type of Change (Refactor box), Verification & Evidence with raw output.
3. Comment on issue #456: the 5 already-resolved items with file:line evidence, the 2 stale claims corrected (BottomNav exists at `MainActivity.kt:173`; `Level4` used at `MainNavigationBars.kt:80`), the 4 deleted tokens, and the PR link. Do not close the issue until the PR merges.
4. Update this plan's Status line and its `plans/README.md` row; when the PR merges, move the plan to `plans/archive/` and flip the Epic #459 checklist item `- [ ] #456` to `- [x]`.
5. Done criteria: PR open and green; issue comment posted; metadata committed in the same PR.

## 4. Verification

Windows commands from the repository root (this repo uses `gradlew.bat`):

```bash
./gradlew.bat ktlintFormat --no-daemon
./gradlew.bat ktlintCheck detekt --no-daemon
./gradlew.bat --no-daemon compileDebugKotlin
./gradlew.bat testDebugUnitTest --no-daemon --tests "io.github.mojri.hesabyar.ui.designsystem.DimensTokenTest" --tests "io.github.mojri.hesabyar.ui.components.HesabyarFabTokenTest" --tests "io.github.mojri.hesabyar.ui.components.HesabyarFabTest" --tests "io.github.mojri.hesabyar.ui.components.HesabyarTabRowTest" --tests "io.github.mojri.hesabyar.MainNavigationComponentsTest"
./gradlew.bat testDebugUnitTest --no-daemon
python scripts/check_docs.py
```

Evidence standard per `AGENTS.md`: paste the exact post-change file contents of `Dimens.kt` and `ElevationTokens.kt`, the raw `<testcase>` lines with test names, exact file paths with line numbers for anything referenced, and the raw grep results from Phase 0. Do not report "passed" without them.

## 5. Risks, Dependencies, and STOP Conditions

- **Drift risk:** another PR may wire one of the four tokens before this plan executes (e.g. a chip standardization under Epic #459). Phase 0 re-audit is mandatory; deleting a token that gained a consumer is the only real data-loss-class risk in this plan, and the re-audit prevents it. **STOP if any token gained a consumer.**
- **Chip-height decision:** deleting `ChipHeight` instead of wiring it into `HesabyarChip` is deliberate. Wiring 36dp onto M3 `FilterChip` (default 32dp) changes every chip's height, which violates the issue's `refactor` (no-behavior-change) label. If the user prefers the visual change, Phase 1 step 1c becomes "wire `Dimens.ChipHeight` into `HesabyarChip` via `Modifier.heightIn(min = Dimens.ChipHeight)`" and the PR must call out the visual change — that is a scope change requiring user approval before execution.
- **Deprecated-alias removal:** `BottomNavClearance` is app-internal object API with zero usages; removal cannot break callers. If compile fails after removal, an unknown reflection-based usage exists — **STOP** and investigate before proceeding.
- **No renumbering:** keep `Level2/Level3/Level4` values (3/6/8dp) untouched. Do not "renumber" levels to close gaps; M3 elevation scale positions are semantic.
- **Hard constraints:** this plan touches no money fields (no `Float`/`Double` money), no Room schema (no migration), no secrets, no calendar or offline code, no business logic (UI constants only), no `GlobalScope`. It satisfies the AGENTS.md checklist by construction.
- **Branch note:** the working tree at planning time is on `feat/issue-455-icon-size-alpha-tokens` with open PR #498. Execution must branch from `main` after #498 merges (or from that branch only if the user explicitly wants a stacked PR — `Dimens.kt` line numbers above assume the #498 state, which is why Phase 0 re-verifies).
- **Board:** set issue #456 to `In Progress` per `docs/agents/project-board.md` (read field/option IDs at runtime). Known blocker from `progress.md`: the `gh` token may lack `project` scopes; if the mutation fails with `INSUFFICIENT_SCOPES`, report it and continue — the PR merge closes the loop.
