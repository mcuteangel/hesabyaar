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
// yields UNCERTAIN with candidate: true - a RESOLVE-CANDIDATE for human/dry-run
// review - and NEVER auto-resolves. A current finding at the same location is
// KEEP, so two different findings sharing one location cannot cause a wrong
// decision either. Auto-resolution stays deferred until upstream provides a
// stable cross-run identity.
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

// Single source of truth for location normalization. Accepts raw path and
// line fields; returns integer-only { path, start, end }, or null when the
// location is unusable (empty/non-string path, no finite integer line). Both
// comment ranges and current-finding ranges route through this helper, so
// their validation rules cannot drift.
function toRange(path, startLine, endLine) {
  if (typeof path !== "string" || path.length === 0) return null;
  const s = Number.isInteger(startLine) ? startLine : null;
  const e = Number.isInteger(endLine) ? endLine : null;
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

// A usable finding line: positive safe integer. Rejects floats, negatives,
// zero, and values beyond Number.MAX_SAFE_INTEGER - a huge or negative bound
// would otherwise fabricate a range that suppresses RESOLVE-CANDIDATEs.
function isValidFindingLine(value) {
  return typeof value === "number" && Number.isSafeInteger(value) && value > 0;
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
    const range =
      f &&
      typeof f === "object" &&
      isValidFindingLine(f.start_line) &&
      isValidFindingLine(f.end_line)
        ? toRange(f.path, f.start_line, f.end_line)
        : null;
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
// Both line fields must be positive safe integers - a partial float, a
// negative, or an out-of-range value must poison the whole payload instead of
// degrading to a truncated or inflated range.
function isValidResultPayload(payload) {
  if (!payload || typeof payload !== "object" || !Array.isArray(payload.comments)) return false;
  return payload.comments.every(
    (f) =>
      f &&
      typeof f === "object" &&
      typeof f.path === "string" &&
      f.path.length > 0 &&
      isValidFindingLine(f.start_line) &&
      isValidFindingLine(f.end_line)
  );
}

module.exports = { OCR_ID_RE, extractOcrId, isOcrInlineComment, toRange, toLineRange, rangesIntersect, classifyThreads, isValidResultPayload };
