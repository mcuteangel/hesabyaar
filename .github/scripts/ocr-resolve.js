// Resolver decision core for stale OpenCodeReview inline review threads.
//
// Ownership identity: upstream alibaba/open-code-review embeds a per-comment
// HTML marker in every inline review comment body:
//   <!-- ocr-<runId>-<attempt>-<16 hex chars> -->
// (post-review-comments.js: formatComment + newCommentId; the Action itself
// matches this exact shape for its own idempotency check). We reuse that
// marker - not author logins, not comment text.
//
// Staleness rule (fail-safe): upstream exposes NO stable cross-run finding
// identity (per-comment IDs are random per run), so "absent from the latest
// LLM output" can never be distinguished from an LLM miss. Absence therefore
// yields UNCERTAIN with candidate: true - a RESOLVE-CANDIDATE - and NEVER
// auto-resolves by itself.
//
// Issue #206 phase 3 adds ONE evidence path on top: when a candidate's code
// location provably changed after the finding's original_commit_id (file
// deleted/renamed, or a diff hunk editing lines inside the original range),
// the workflow may resolve the thread and cite the resolving commit. The
// pure helpers below (toOriginalRange, parseChangedNewLines,
// diffTouchesLocation) keep that decision testable; absence alone still
// never resolves.
"use strict";

// Anchored to the HTML comment wrapper so user content cannot fake it
// accidentally; 8 random bytes = exactly 16 hex chars. Case-insensitive so a
// future producer emitting uppercase hex cannot flip ownership to NOT_OCR.
const OCR_ID_RE = /<!--\s*(ocr-\d+-\d+-[a-f0-9]{16})\s*-->/i;

function extractOcrId(body) {
  if (typeof body !== "string") return null;
  const m = OCR_ID_RE.exec(body);
  return m ? m[1] : null;
}

function isOcrInlineComment(comment) {
  return extractOcrId(comment && comment.body) !== null;
}

// A usable finding line: positive safe integer. Rejects floats, negatives,
// zero, and values beyond Number.MAX_SAFE_INTEGER - a huge or negative bound
// would otherwise fabricate a range that suppresses RESOLVE-CANDIDATEs.
function isValidFindingLine(value) {
  return typeof value === "number" && Number.isSafeInteger(value) && value > 0;
}

// A line field counts as ABSENT only when undefined or null (GitHub omits
// start_line on single-line comments). Any PRESENT value must be a valid
// finding line; an invalid present value poisons the whole location instead
// of silently collapsing into the other bound.
function presentLine(value) {
  if (value === undefined || value === null) return null;
  return isValidFindingLine(value) ? value : false;
}

// Single source of truth for location normalization. Accepts raw path and
// line fields; returns integer-only { path, start, end }, or null when the
// location is unusable: empty/non-string path, no line field at all, or ANY
// present field that is not a positive safe integer (float, zero, negative,
// unsafe). One-sided locations stay supported: a missing field falls back to
// the remaining valid one. Both comment ranges and current-finding ranges
// route through this helper, so their validation rules cannot drift.
function toRange(path, startLine, endLine) {
  if (typeof path !== "string" || path.length === 0) return null;
  const s = presentLine(startLine);
  const e = presentLine(endLine);
  if (s === false || e === false) return null;
  if (s === null && e === null) return null;
  const start = s !== null ? s : e;
  const end = e !== null ? e : s;
  return { path, start: Math.min(start, end), end: Math.max(start, end) };
}

// Normalize a REST review comment into { path, start, end } using RIGHT-side
// lines only. Returns null when side is missing or LEFT, or line info is
// unusable - such threads are never candidates (uncertain).
function toLineRange(comment) {
  if (!comment || comment.side !== "RIGHT") return null;
  return toRange(comment.path, comment.start_line, comment.line);
}

function rangesIntersect(a, b) {
  return a.path === b.path && Math.max(a.start, b.start) <= Math.min(a.end, b.end);
}

// Normalize one OCR finding into a usable range. OCR findings carry a
// stricter contract than GitHub review comments: the path must be a
// non-empty string and BOTH line bounds are required positive safe
// integers; anything else yields null instead of degrading to a partial or
// inflated range. Single source of truth shared by classifyThreads and
// isValidResultPayload so their rules cannot drift.
function toFindingRange(f) {
  return f &&
    typeof f === "object" &&
    typeof f.path === "string" &&
    f.path.length > 0 &&
    isValidFindingLine(f.start_line) &&
    isValidFindingLine(f.end_line)
    ? toRange(f.path, f.start_line, f.end_line)
    : null;
}

// Classify PR review comments against the current OCR findings.
//
// currentFindings: [{ path, start_line, end_line }] from the latest OCR run.
// resultAvailable: must be exactly true to compare locations; anything else
// (unreadable result, wrong schema, omitted flag) makes every decision
// UNCERTAIN (fail-safe), regardless of content.
// Returns [{ id, threadKey, decision, candidate, reason }] where decision is
// one of "KEEP" | "NOT_OCR" | "UNCERTAIN". There is deliberately NO RESOLVE:
// absence-only evidence (candidate: true) never auto-resolves a thread.
function classifyThreads({ reviewComments, currentFindings, resultAvailable }) {
  const findings = Array.isArray(currentFindings) ? currentFindings : [];
  const ranges = [];
  for (const f of findings) {
    // Findings must carry BOTH bounds as valid lines; anything else (partial
    // float, negative, huge) is dropped outright instead of being truncated
    // into a phantom range that could force a KEEP.
    const range = toFindingRange(f);
    if (range) ranges.push(range);
  }

  const decisions = [];
  for (const c of Array.isArray(reviewComments) ? reviewComments : []) {
    const id = extractOcrId(c && c.body);
    const key = c && c.id !== undefined ? String(c.id) : "?";
    if (!id) {
      decisions.push({ id: null, threadKey: key, decision: "NOT_OCR", candidate: false, reason: "no OpenCodeReview per-comment marker" });
      continue;
    }
    if (resultAvailable !== true) {
      decisions.push({ id, threadKey: key, decision: "UNCERTAIN", candidate: false, reason: "latest OCR result unavailable or invalid; resolving nothing" });
      continue;
    }
    const range = toLineRange(c);
    if (!range) {
      decisions.push({ id, threadKey: key, decision: "UNCERTAIN", candidate: false, reason: "OCR comment without usable RIGHT-side line range" });
      continue;
    }
    const stillReported = ranges.some((r) => rangesIntersect(r, range));
    decisions.push({
      id,
      threadKey: key,
      decision: stillReported ? "KEEP" : "UNCERTAIN",
      candidate: !stillReported,
      reason: stillReported
        ? `current finding overlaps ${range.path}:${range.start}-${range.end}`
        : `no current finding at ${range.path}:${range.start}-${range.end}; absence from one stochastic LLM run is not proof of staleness`,
    });
  }
  return decisions;
}

// Strict schema check for /tmp/ocr-result.json before it may count as an
// authoritative finding set: { comments: [{ path, start_line, end_line }] }.
// Every entry must normalize through toFindingRange - a partial float, a
// negative, or an out-of-range value poisons the whole payload instead of
// degrading to a truncated or inflated range.
function isValidResultPayload(payload) {
  if (!payload || typeof payload !== "object" || !Array.isArray(payload.comments)) return false;
  return payload.comments.every((f) => toFindingRange(f) !== null);
}

// ---- Phase 3: evidence-based staleness detection (pure, no I/O) ----

// Normalize a REST review comment into the location where the finding was
// ORIGINALLY posted, anchored to its own original_commit_id. GitHub anchors
// original_line/original_start_line to that commit's RIGHT side, so the same
// validation contract as toLineRange applies, plus: a full 40-hex
// original_commit_id is mandatory (it is the diff base we will compare
// against). Returns null for anything unusable - callers must treat null as
// "cannot prove staleness" and skip resolution. Deliberately does NOT fall
// back to current-position line fields: those are anchored to a different
// commit than the diff being compared, which would fabricate evidence.
function toOriginalRange(comment) {
  if (!comment || comment.side !== "RIGHT") return null;
  const sha = comment.original_commit_id;
  if (typeof sha !== "string" || !/^[0-9a-f]{40}$/.test(sha)) return null;
  const s = presentLine(comment.original_start_line);
  const e = presentLine(comment.original_line);
  if (s === false || e === false) return null;
  if (s === null && e === null) return null;
  const range = toRange(comment.path, s, e);
  if (!range) return null;
  return { sha, ...range };
}

// Extract OLD-side line numbers deleted or rewritten ('-' lines) by one
// unified diff patch, anchored to the diff's BASE commit - the same
// coordinate system as a finding's original_commit_id (this is deliberate:
// comparing base-side positions against an original range stays correct even
// when earlier hunks shift line numbers). Pure insertions are deliberately
// NOT evidence: they leave the reviewed old-side lines byte-for-byte
// unchanged, so the finding still applies (fail-safe, per review round on PR
// #211). Handles multiple hunks and '\\ No newline' markers. Non-string input
// (truncated diff, binary file) yields [] - callers must interpret that as
// "no proof".
function parseHunkChanges(patch) {
  const editedOld = [];
  if (typeof patch !== "string") return editedOld;
  let oldLine = null;
  for (const raw of patch.split("\n")) {
    const hunk = /^@@ -(\d+)/.exec(raw);
    if (hunk && /^@@ -\d+(?:,\d+)? \+\d+(?:,\d+)? @@/.test(raw)) {
      oldLine = parseInt(hunk[1], 10);
      continue;
    }
    if (oldLine === null) continue;
    if (raw.startsWith("-")) {
      editedOld.push(oldLine);
      oldLine += 1;
    } else if (raw.startsWith("+") || raw.startsWith("\\")) {
      // New-side content or marker: neither consumes an old line nor proves
      // an original line changed.
    } else {
      oldLine += 1; // context line moves both cursors
    }
  }
  return editedOld;
}

// Decide whether a REST compareCommits `files` array PROVES that the reviewed
// location changed between the finding's anchor commit and the comparison
// head. Returns { changed, reason }. Fail-safe: every ambiguous case (file
// absent from the diff = untouched, truncated/missing patch, malformed input)
// yields changed=false so a thread is never resolved without hard evidence.
function diffTouchesLocation(files, loc) {
  if (
    !Array.isArray(files) ||
    !loc ||
    typeof loc.path !== "string" ||
    typeof loc.sha !== "string" ||
    !isValidFindingLine(loc.start) ||
    !isValidFindingLine(loc.end)
  ) {
    return { changed: false, reason: "no usable diff data or malformed location; refusing to infer" };
  }
  const file = files.find((f) => f && (f.filename === loc.path || f.previous_filename === loc.path));
  if (!file) {
    return { changed: false, reason: `${loc.path} untouched in ${loc.sha.slice(0, 12)}..HEAD diff` };
  }
  if (file.status === "removed") {
    return { changed: true, reason: `${loc.path} was deleted after ${loc.sha.slice(0, 12)}` };
}
  // Renames are NOT evidence by themselves: a pure path-only rename leaves
  // the reviewed code byte-for-byte identical at its new location, so the
  // finding still applies and "the code changed" would be false. Fall through
  // to the patch check - only a content edit inside the original range
  // counts; a patchless pure rename lands on the fail-safe branch below.
  if (typeof file.patch !== "string") {
    return { changed: false, reason: `${loc.path} modified but its patch is unavailable/truncated; refusing to infer` };
  }
  const editHit = parseHunkChanges(file.patch).some((n) => n >= loc.start && n <= loc.end);
  return editHit
    ? { changed: true, reason: `lines ${loc.start}-${loc.end} of ${loc.path} edited/deleted after ${loc.sha.slice(0, 12)}` }
    : { changed: false, reason: `no reviewed line of ${loc.path}:${loc.start}-${loc.end} was edited/deleted at the anchor commit` };
}

// Given an ordered (newest-first) list of commit SHAs that touched `path`
// between the PR head and a finding's anchor commit, return the NEWEST commit
// that is strictly after the anchor - i.e. the commit most likely to have
// resolved the finding. The anchor is an ancestor of head (it is the
// finding's original_commit_id), so the list walks head -> ... -> anchor; the
// first entry that is not the anchor is therefore the newest touching commit
// after it. Returns null when the list is unusable or the anchor is already
// the newest touching commit, so callers fall back to the PR head SHA. Pure:
// no I/O, fully testable; it trusts the newest-first ordering the GitHub
// listCommits API guarantees for a given path.
function pickResolvingCommit(orderedShas, anchorSha) {
  if (!Array.isArray(orderedShas) || typeof anchorSha !== "string") return null;
  for (const sha of orderedShas) {
    if (typeof sha !== "string") return null;
    if (sha === anchorSha) return null; // reached the anchor; nothing newer touched it
    return sha; // first commit after the anchor that touched the path
  }
  return null;
}

module.exports = { OCR_ID_RE, extractOcrId, isOcrInlineComment, toRange, toLineRange, rangesIntersect, classifyThreads, isValidResultPayload, toOriginalRange, parseHunkChanges, diffTouchesLocation, pickResolvingCommit };
