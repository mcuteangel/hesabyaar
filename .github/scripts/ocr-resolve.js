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
// accidentally; 8 random bytes = exactly 16 hex chars.
const OCR_ID_RE = /<!--\s*(ocr-\d+-\d+-[a-f0-9]{16})\s*-->/;

function extractOcrId(body) {
  if (typeof body !== "string") return null;
  const m = OCR_ID_RE.exec(body);
  return m ? m[1] : null;
}

function isOcrInlineComment(comment) {
  return extractOcrId(comment && comment.body) !== null;
}

// Normalize a REST review comment into { path, start, end } using RIGHT-side
// lines only. Returns null when line info is missing or on the LEFT side -
// such threads are never resolved (uncertain).
function toLineRange(comment) {
  if (!comment || typeof comment.path !== "string" || comment.path.length === 0) return null;
  if (comment.side && comment.side !== "RIGHT") return null;
  const start = Number.isFinite(comment.start_line) ? comment.start_line : null;
  const end = Number.isFinite(comment.line) ? comment.line : null;
  if (start === null && end === null) return null;
  const s = start !== null ? start : end;
  const e = end !== null ? end : start;
  if (!Number.isInteger(s) || !Number.isInteger(e)) return null;
  return { path: comment.path, start: Math.min(s, e), end: Math.max(s, e) };
}

function rangesIntersect(a, b) {
  return a.path === b.path && Math.max(a.start, b.start) <= Math.min(a.end, b.end);
}

// Classify PR review comments against the current OCR findings.
//
// currentFindings: [{ path, start_line, end_line }] from the latest OCR run.
// resultAvailable: false when the latest result could not be read/validated -
// then every decision is UNCERTAIN (fail-safe), regardless of content.
// Returns [{ id, threadKey, decision, candidate, reason }] where decision is
// one of "KEEP" | "NOT_OCR" | "UNCERTAIN". There is deliberately NO RESOLVE:
// absence-only evidence (candidate: true) never auto-resolves a thread.
function classifyThreads({ reviewComments, currentFindings, resultAvailable }) {
  const findings = Array.isArray(currentFindings) ? currentFindings : [];
  const ranges = [];
  for (const f of findings) {
    if (!f || typeof f.path !== "string") continue;
    const s = Number.isFinite(f.start_line) ? f.start_line : null;
    const e = Number.isFinite(f.end_line) ? f.end_line : null;
    if (s === null && e === null) continue;
    const start = s !== null ? s : e;
    const end = e !== null ? e : s;
    ranges.push({ path: f.path, start: Math.min(start, end), end: Math.max(start, end) });
  }

  const decisions = [];
  for (const c of Array.isArray(reviewComments) ? reviewComments : []) {
    const id = extractOcrId(c && c.body);
    const key = c && c.id !== undefined ? String(c.id) : "?";
    if (!id) {
      decisions.push({ id: null, threadKey: key, decision: "NOT_OCR", reason: "no OpenCodeReview per-comment marker" });
      continue;
    }
    if (resultAvailable === false) {
      decisions.push({ id, threadKey: key, decision: "UNCERTAIN", reason: "latest OCR result unavailable; resolving nothing" });
      continue;
    }
    const range = toLineRange(c);
    if (!range) {
      decisions.push({ id, threadKey: key, decision: "UNCERTAIN", reason: "OCR comment without usable RIGHT-side line range" });
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

module.exports = { OCR_ID_RE, extractOcrId, isOcrInlineComment, toLineRange, rangesIntersect, classifyThreads };
