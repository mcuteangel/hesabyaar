// Tests for the stale-OCR-thread resolver decision core. Plain node:test, no
// frameworks. Run: node --test .github/scripts/ocr-resolve.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require_ = createRequire(import.meta.url);
const {
  extractOcrId,
  isOcrInlineComment,
  toLineRange,
  rangesIntersect,
  classifyThreads,
  isValidResultPayload,
} = require_("./ocr-resolve.js");

const OCR_BODY = (id) => `<!-- ${id} -->\n\nsome finding text`;
const VALID_ID = "ocr-1234567890-1-0123456789abcdef";

const ocrComment = (id, path, line, startLine, side) => ({
  id,
  body: OCR_BODY(VALID_ID),
  path,
  line,
  start_line: startLine,
  side: side || "RIGHT",
});

test("extractOcrId accepts the upstream marker format", () => {
  assert.equal(extractOcrId(`<!-- ${VALID_ID} -->`), VALID_ID);
});

test("extractOcrId is case-insensitive on the hex suffix", () => {
  const upper = "ocr-1234567890-1-0123456789ABCDEF";
  assert.equal(extractOcrId(`<!-- ${upper} -->`), upper);
});

test("extractOcrId rejects non-OCR or malformed markers", () => {
  assert.equal(extractOcrId("CodeRabbit review"), null);
  assert.equal(extractOcrId("<!-- cubic:something -->"), null);
  assert.equal(extractOcrId("<!-- ocr-short -->"), null);
  assert.equal(extractOcrId(undefined), null);
});

test("isOcrInlineComment distinguishes OCR from other bots and humans", () => {
  assert.ok(isOcrInlineComment({ body: `x\n<!-- ${VALID_ID} -->\ny` }));
  assert.ok(!isOcrInlineComment({ body: "**CodeRabbit** has posted comments" }));
  assert.ok(!isOcrInlineComment({ body: "Cubic P1 comment" }));
  assert.ok(!isOcrInlineComment({ body: "CodeAnt suggestion" }));
  assert.ok(!isOcrInlineComment({ body: "LGTM, nice catch" }));
  assert.ok(!isOcrInlineComment({}));
});

test("toLineRange handles single-line and multi-line RIGHT-side comments", () => {
  assert.deepEqual(toLineRange(ocrComment(1, "a.kt", 10, undefined)), { path: "a.kt", start: 10, end: 10 });
  assert.deepEqual(toLineRange(ocrComment(2, "a.kt", 20, 15)), { path: "a.kt", start: 15, end: 20 });
});

test("toLineRange returns null for missing lines, LEFT side, and missing side", () => {
  assert.equal(toLineRange({ body: "", path: "a.kt" }), null);
  assert.equal(toLineRange(ocrComment(3, "a.kt", 10, undefined, "LEFT")), null);
  // A missing side must fail safe to UNCERTAIN, not default to RIGHT.
  assert.equal(toLineRange({ body: "", path: "a.kt", line: 10 }), null);
  // Side match is exact ("RIGHT"), not case-insensitive.
  assert.equal(toLineRange(ocrComment(3, "a.kt", 10, undefined, "right")), null);
  // RIGHT side but no usable line field at all -> null.
  assert.equal(toLineRange({ body: "", path: "a.kt", side: "RIGHT" }), null);
});

test("malformed comment line bounds fail closed -> UNCERTAIN, candidate false", () => {
  // Each malformed comment sits on top of a live finding at src/A.kt 10-12.
  // If any invalid bound leaked through as a usable range it would produce
  // KEEP; the fail-closed outcome is UNCERTAIN with candidate:false.
  const finding = [{ path: "src/A.kt", start_line: 10, end_line: 12 }];
  const cases = [
    ["zero bound", ocrComment(931, "src/A.kt", 10, 0)],
    ["negative bound", ocrComment(932, "src/A.kt", 10, -1)],
    ["float end collapses start", { id: 933, body: OCR_BODY(VALID_ID), path: "src/A.kt", side: "RIGHT", start_line: 10, line: 10.5 }],
    ["float start collapses end", { id: 934, body: OCR_BODY(VALID_ID), path: "src/A.kt", side: "RIGHT", start_line: 10.5, line: 10 }],
    ["unsafe integer bound", ocrComment(935, "src/A.kt", 10, 1e21)],
  ];
  for (const [label, comment] of cases) {
    const d = classifyThreads({
      reviewComments: [comment],
      currentFindings: finding.map((f) => ({ ...f })),
      resultAvailable: true,
    });
    assert.deepEqual(d.map((x) => x.decision), ["UNCERTAIN"], label);
    assert.equal(d[0].candidate, false, label);
  }
  // Unit-level proof for the same five inputs.
  const mk = (start_line, line) => ({ body: OCR_BODY(VALID_ID), path: "src/A.kt", side: "RIGHT", start_line, line });
  assert.equal(toLineRange(mk(0, 10)), null);
  assert.equal(toLineRange(mk(-1, 10)), null);
  assert.equal(toLineRange(mk(10, 10.5)), null);
  assert.equal(toLineRange(mk(10.5, 10)), null);
  assert.equal(toLineRange(mk(1e21, 10)), null);
  // Control: the same location with valid bounds still overlaps -> KEEP.
  const ok = run([ocrComment(936, "src/A.kt", 12, 10)], { findings: finding });
  assert.deepEqual(ok.map((x) => x.decision), ["KEEP"]);
  // One-sided legit locations stay supported (GitHub omits start_line on
  // single-line comments).
  assert.deepEqual(toLineRange({ path: "p.kt", side: "RIGHT", start_line: undefined, line: 7 }), {
    path: "p.kt",
    start: 7,
    end: 7,
  });
});

test("rangesIntersect requires same path and overlapping range", () => {
  assert.ok(rangesIntersect({ path: "a", start: 1, end: 5 }, { path: "a", start: 5, end: 9 }));
  assert.ok(!rangesIntersect({ path: "a", start: 1, end: 4 }, { path: "a", start: 5, end: 9 }));
  assert.ok(!rangesIntersect({ path: "a", start: 1, end: 5 }, { path: "b", start: 1, end: 5 }));
});

// ---- classifyThreads: the ten required scenarios ----

const findings = [
  { path: "src/A.kt", start_line: 10, end_line: 12 },
  { path: "src/B.kt", start_line: 30, end_line: 30 },
];

function run(comments, opts) {
  return classifyThreads({
    reviewComments: comments,
    // Clone the shared fixture so a future in-place normalization inside
    // classifyThreads cannot leak state between tests.
    currentFindings: opts && "findings" in opts ? opts.findings : findings.map((f) => ({ ...f })),
    resultAvailable: opts && "resultAvailable" in opts ? opts.resultAvailable : true,
  });
}

test("non-array inputs fail safe to empty decisions", () => {
  assert.deepEqual(classifyThreads({ reviewComments: null, currentFindings: null }), []);
  assert.deepEqual(
    classifyThreads({ reviewComments: undefined, currentFindings: "bad", resultAvailable: true }),
    []
  );
});

test("null findings with an OCR comment -> UNCERTAIN candidate, never RESOLVE", () => {
  const d = run([ocrComment(921, "src/A.kt", 12, 10)], { findings: null });
  assert.deepEqual(d.map((x) => x.decision), ["UNCERTAIN"]);
  assert.equal(d[0].candidate, true);
});

test("junk finding entries are ignored; valid ones still produce KEEP", () => {
  const d = run([ocrComment(922, "src/A.kt", 12, 10)], {
    findings: [null, "bad", {}, { path: "src/A.kt", start_line: 10, end_line: 12 }],
  });
  assert.deepEqual(d.map((x) => x.decision), ["KEEP"]);
});

test("1. finding still exists -> KEEP", () => {
  const d = run([ocrComment(101, "src/A.kt", 12, 10)]);
  assert.deepEqual(d.map((x) => x.decision), ["KEEP"]);
});

test("2. finding absent from latest run -> UNCERTAIN candidate, never RESOLVE", () => {
  const d = run([ocrComment(102, "src/OLD.kt", 7, 7)]);
  assert.deepEqual(d.map((x) => x.decision), ["UNCERTAIN"]);
  assert.equal(d[0].candidate, true);
});

test("3. CodeRabbit comment -> NOT_OCR", () => {
  const d = run([{ id: 201, body: "<!-- coderabbitai[bot] review -->\nfinding at src/A.kt:10" }]);
  assert.deepEqual(d.map((x) => x.decision), ["NOT_OCR"]);
});

test("4. Cubic comment -> NOT_OCR", () => {
  const d = run([{ id: 202, body: "P1: problem\n<!-- cubic:v=9e1ec23b -->" }]);
  assert.deepEqual(d.map((x) => x.decision), ["NOT_OCR"]);
});

test("5. CodeAnt comment -> NOT_OCR", () => {
  const d = run([{ id: 203, body: "**Suggestion:** pin this action [codeant]" }]);
  assert.deepEqual(d.map((x) => x.decision), ["NOT_OCR"]);
});

test("6. human review comment -> NOT_OCR", () => {
  const d = run([{ id: 204, body: "Please rename this variable." }]);
  assert.deepEqual(d.map((x) => x.decision), ["NOT_OCR"]);
});

test("7. uncertain ownership (OCR marker but no usable location) -> UNCERTAIN", () => {
  const d = run([{ id: 205, body: `<!-- ${VALID_ID} -->`, path: "src/A.kt" }]);
  assert.deepEqual(d.map((x) => x.decision), ["UNCERTAIN"]);
});

test("8. same file/line but different finding -> not incorrectly resolved", () => {
  // A different finding now lives exactly where the old one was: overlap keeps
  // the thread unresolved; we never judge whether it is "the same" finding.
  const d = run([ocrComment(206, "src/B.kt", 30, 30)]);
  assert.deepEqual(d.map((x) => x.decision), ["KEEP"]);
});

test("9. two OCR findings, one absent from latest run -> zero resolves, one candidate", () => {
  const d = run([
    ocrComment(301, "src/A.kt", 11, 10), // still reported
    ocrComment(302, "src/GONE.kt", 3, 3), // absent this run
  ]);
  assert.equal(d.filter((x) => x.decision === "RESOLVE").length, 0);
  const keep = d.find((x) => x.decision === "KEEP");
  const candidate = d.find((x) => x.candidate === true);
  assert.ok(keep);
  // Assert the RIGHT thread is the candidate (301 stays KEEP).
  assert.equal(candidate.threadKey, "302");
});

test("regression: completely clean latest run can never resolve an OCR thread", () => {
  // A flaky empty "Looks good" run must not wipe open threads.
  const d = run(
    [ocrComment(601, "src/A.kt", 10, 10), ocrComment(602, "src/GONE.kt", 3, 3)],
    { findings: [] }
  );
  assert.ok(d.length >= 2);
  assert.equal(d.some((x) => x.decision === "RESOLVE"), false);
  for (const x of d) {
    if (x.candidate) {
      assert.equal(x.decision, "UNCERTAIN");
      assert.match(x.reason, /not proof of staleness/);
    } else {
      assert.notEqual(x.decision, "RESOLVE");
    }
  }
});

test("no decision path ever emits RESOLVE", () => {
  const samples = [
    ...run([ocrComment(701, "src/A.kt", 11, 10)]),
    ...run([ocrComment(702, "src/GONE.kt", 3, 3)], { findings: [] }),
    ...run([{ id: 703, body: `<!-- ${VALID_ID} -->`, path: "x" }]),
    ...run([ocrComment(704, "src/A.kt", 5, 5)], { resultAvailable: false }),
    // Omitted flag must also fail safe, not default to available.
    ...classifyThreads({ reviewComments: [ocrComment(705, "src/GONE.kt", 3, 3)], currentFindings: [] }),
  ];
  assert.equal(samples.some((x) => x.decision === "RESOLVE"), false);
});

test("fail-safe: omitted resultAvailable defaults to uncertain", () => {
  const d = classifyThreads({ reviewComments: [ocrComment(801, "src/GONE.kt", 3, 3)], currentFindings: [] });
  assert.deepEqual(d.map((x) => x.decision), ["UNCERTAIN"]);
  assert.equal(d[0].candidate, false);
});

test("10. no previous OCR threads -> no-op", () => {
  const d = run([]);
  assert.equal(d.length, 0);
});

test("fail-safe: result unavailable -> nothing resolved even for OCR threads", () => {
  const d = run([ocrComment(401, "src/GONE.kt", 3, 3)], { resultAvailable: false });
  assert.deepEqual(d.map((x) => x.decision), ["UNCERTAIN"]);
});

test("clean run with zero findings marks OCR threads as candidates only, others NOT_OCR", () => {
  const d = run(
    [ocrComment(501, "src/GONE.kt", 3, 3), { id: 502, body: "human note" }],
    { findings: [] }
  );
  assert.deepEqual(d.map((x) => x.decision), ["UNCERTAIN", "NOT_OCR"]);
  assert.equal(d[0].candidate, true);
  assert.equal(d[1].candidate, false);
});

// ---- malformed findings must not create phantom KEEP ranges ----

test("float line value in a finding is rejected -> thread stays candidate", () => {
  const d = run([ocrComment(901, "src/A.kt", 10, 10)], {
    findings: [{ path: "src/A.kt", start_line: 10.5, end_line: 12 }],
  });
  assert.deepEqual(d.map((x) => x.decision), ["UNCERTAIN"]);
  assert.equal(d[0].candidate, true);
});

test("empty path in a finding is rejected -> thread stays candidate", () => {
  const d = run([ocrComment(902, "src/GONE.kt", 3, 3)], {
    findings: [{ path: "", start_line: 3, end_line: 3 }],
  });
  assert.deepEqual(d.map((x) => x.decision), ["UNCERTAIN"]);
  assert.equal(d[0].candidate, true);
});

// ---- isValidResultPayload (glue-side schema gate) ----

const goodFinding = { path: "src/A.kt", start_line: 10, end_line: 12 };

test("isValidResultPayload accepts the expected schema", () => {
  assert.equal(isValidResultPayload({ comments: [goodFinding] }), true);
  assert.equal(isValidResultPayload({ comments: [{ path: "a", start_line: 5, end_line: 5 }] }), true);
});

test("partial float {start_line:10, end_line:10.5} poisons the payload", () => {
  assert.equal(
    isValidResultPayload({ comments: [{ path: "src/A.kt", start_line: 10, end_line: 10.5 }] }),
    false
  );
});

test("partial float finding is dropped -> no phantom KEEP range", () => {
  // The malformed finding points at line 10; it must not create a truncated
  // {10,10} range that flips the overlapping comment to KEEP.
  const d = run([ocrComment(903, "src/A.kt", 10, 10)], {
    findings: [{ path: "src/A.kt", start_line: 10, end_line: 10.5 }],
  });
  assert.deepEqual(d.map((x) => x.decision), ["UNCERTAIN"]);
  assert.equal(d[0].candidate, true);
});

test("negative or huge line bounds are dropped -> no inflated KEEP range", () => {
  const d = run(
    [
      ocrComment(911, "src/A.kt", 10, 10),
      ocrComment(912, "src/B.kt", 30, 30),
    ],
    {
      findings: [
        { path: "src/A.kt", start_line: -1000000, end_line: 50 },
        { path: "src/B.kt", start_line: 1e21, end_line: 1e21 },
      ],
    }
  );
  assert.deepEqual(d.map((x) => x.decision), ["UNCERTAIN", "UNCERTAIN"]);
  for (const x of d) assert.equal(x.candidate, true);
});

test("isValidResultPayload rejects malformed payloads", () => {
  assert.equal(isValidResultPayload(null), false);
  assert.equal(isValidResultPayload("nope"), false);
  assert.equal(isValidResultPayload({}), false); // no comments array
  assert.equal(isValidResultPayload({ comments: "all good!" }), false);
  assert.equal(isValidResultPayload({ comments: [goodFinding, {}] }), false); // one bad entry
  assert.equal(isValidResultPayload({ comments: [{ path: "", start_line: 1 }] }), false);
  assert.equal(isValidResultPayload({ comments: [{ path: "a", start_line: "10" }] }), false);
  // Missing either integer bound is now invalid too (strict both-bounds rule).
  assert.equal(isValidResultPayload({ comments: [{ path: "a", end_line: 5 }] }), false);
  // Bounds must be positive safe integers.
  assert.equal(isValidResultPayload({ comments: [{ path: "a", start_line: 0, end_line: 5 }] }), false);
  assert.equal(isValidResultPayload({ comments: [{ path: "a", start_line: -3, end_line: 5 }] }), false);
  assert.equal(
    isValidResultPayload({ comments: [{ path: "a", start_line: 1e21, end_line: 1e21 }] }),
    false
  );
});
