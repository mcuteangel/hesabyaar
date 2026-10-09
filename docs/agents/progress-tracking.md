# Progress Tracking (agent guide)

> Part of the agent guide. Start at `AGENTS.md`; it says when to read this file.

## Progress Tracking

### Role of `progress.md`

The `progress.md` file records the active operational state of coding agents.
- It is not an architecture specification.
- It is not an implementation source of truth.
- It does not replace `AGENTS.md`.
- It does not replace domain documentation.
- It is not a permanent changelog.
- It is not a scratchpad for full session transcripts.

### Reading Before Tasks

At the start of every task, an agent must:
1. Read `AGENTS.md`.
2. Read `progress.md` if the file exists.
3. Validate claims in `progress.md` against the live repository state.

Never treat claims in `progress.md` as verified facts without repository confirmation.

### Updating Frequency

Update `progress.md` upon reaching meaningful milestones:
- Starting a significant task.
- Completing a task phase.
- Discovering an important technical finding.
- Documenting an agreed architectural decision.
- Executing a verification run (pass or fail).
- Encountering or resolving a blocker.

Trivial edits do not require updating `progress.md`.

### State Over Changelog

When a task finishes, remove outdated details or summarize them concisely. Do not append timestamped log entries for each session. Keep `progress.md` focused on current state.

### Evidence Standard

Record evidence for every substantive claim:
- File paths and line numbers.
- Exact commands executed.
- Named test cases.
- Observed results.

Never record speculative status expressions like "probably fixed", "should work", or "I think this is correct".

### Conflict Resolution

When `progress.md` contradicts the repository or other documentation, apply this precedence hierarchy:
1. Current live repository code.
2. `AGENTS.md`.
3. Specific domain documentation.
4. `progress.md`.

Report any contradiction and update `progress.md` to reflect verified reality.

### Active Task Hygiene

Only genuinely active tasks belong in the `Active Task` section of `progress.md`. Completed tasks must be moved to the completed summary or removed.

### Secrets Protection

Never write tokens, passwords, API keys, credentials, private keys, or secrets into `progress.md`.
