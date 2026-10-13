# Project Board & Roadmap Hygiene

The [Hesabyar Project board](https://github.com/users/mcuteangel/projects/1) and `docs/ROADMAP.md` are the live record of what's being worked on. Keep them current as you work — don't batch updates at the end.

## Board Status (ProjectV2 `PVT_kwHOAD9VX84Bm0Xa`)

- When you start work on an issue: set its board Status to `In Progress`.
- When the fix PR merges: set Status to `Done`.
- New issues auto-appear via the board's auto-add workflow; closed issues auto-archive — don't add them manually.
- Update via GraphQL: find the item with `node(id: $projectId) { items(first: 100, query: "<issue#>") }`, then `updateProjectV2ItemFieldValue` on the Status field (`PVTSSF_lAHOAD9VX84Bm0Xa`) with the option ID for In Progress / Done (query `field(name: "Status") { options { id name } }` for current IDs).

## Roadmap (`docs/ROADMAP.md`)

- When a roadmap checkbox item ships (merged to main): flip `[ ]` → `[x]`.
- When work starts on a planned item: `[ ]` → `[~]`. When blocked: `[!]`.
- Keep the item wording accurate to what actually shipped; add new planned items under the right section as scope emerges.
- The roadmap edit rides in the same PR as the feature — never a separate docs PR.
