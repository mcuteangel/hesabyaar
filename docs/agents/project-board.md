# Project Board & Roadmap Hygiene

The [Hesabyar Project board](https://github.com/users/mcuteangel/projects/1) and `docs/ROADMAP.md` are the live record of what's being worked on. Keep them current as you work — don't batch updates at the end.

## Board Status (ProjectV2 `PVT_kwHOAD9VX84Bm0Xa`)

- When you start work on an issue: set its board Status to `In Progress`.
- When the fix PR merges: set Status to `Done`.
- New issues auto-appear via the board's auto-add workflow; closed issues auto-archive — don't add them manually.
- Update the board Status via GraphQL:
  - Find the item ID: `node(id: $projectId) { ... on ProjectV2 { items(first: 100, query: "<issue#>") { nodes { id } } } }`.
  - Set the Status field (`PVTSSF_lAHOAD9VX84Bm0Xazhlbydk`) with `updateProjectV2ItemFieldValue`.
  - Use the option ID for `In Progress` or `Done`.
  - Find option IDs: `node(id: $projectId) { ... on ProjectV2 { field(name: "Status") { ... on ProjectV2SingleSelectField { options { id name } } } } }`.
  - Do not copy the field ID from a document. Read it first with
    `node(id: $projectId) { ... on ProjectV2 { fields(first: 20) { nodes { ... on ProjectV2SingleSelectField { id name } } } } }`.
    The ID in this file went stale once already and the mutation failed with `NOT_FOUND`.

## Roadmap (`docs/ROADMAP.md`)

- When a roadmap checkbox item ships (merged to main): flip its current marker to `[x]`. This applies whether it was `[ ]`, `[~]`, or `[!]` before the merge.
- When work starts on a planned item (before or as the feature PR opens): `[ ]` → `[~]`. When blocked: `[!]`. If work begins before any PR is open, set `[~]` immediately. Carry the change into the first feature PR — never leave the roadmap stale.
- Keep the item wording accurate to what actually shipped; add new planned items under the right section as scope emerges.
- The roadmap edit rides in the same PR as the feature — never a separate docs PR.
