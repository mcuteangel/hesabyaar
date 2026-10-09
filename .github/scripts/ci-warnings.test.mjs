// Tests for ci-warnings.mjs — pure node:test, no frameworks.
// Run: node --test .github/scripts/ci-warnings.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  stripLogPrefix,
  makeRepoRelative,
  parseLogLine,
  parseLog,
  categoriseKotlinMessage,
  categoriseAnnotation,
  dedupe,
  renderComment,
  normalizeMessage,
  escapeMarkdown,
  buildSourceLink,
  shortSha,
} from "./ci-warnings.mjs";

const REPO = "owner/repo";
const SHA = "abcdef1234567890abcdef1234567890abcdef12";

// ---------------------------------------------------------------------------
// stripLogPrefix
// ---------------------------------------------------------------------------

test("stripLogPrefix removes timestamp and ANSI codes", () => {
  const line = "2026-10-04T05:12:33.1234567Z \x1b[31mwarning: foo\x1b[0m";
  assert.equal(stripLogPrefix(line), "warning: foo");
});

test("stripLogPrefix handles line without timestamp", () => {
  assert.equal(stripLogPrefix("warning: foo"), "warning: foo");
});

test("stripLogPrefix handles empty line", () => {
  assert.equal(stripLogPrefix(""), "");
});

// ---------------------------------------------------------------------------
// makeRepoRelative
// ---------------------------------------------------------------------------

test("makeRepoRelative strips runner prefix", () => {
  const path = "/home/runner/work/hesabyaar/hesabyaar/app/src/main/java/Foo.kt";
  assert.equal(makeRepoRelative(path, "hesabyaar"), "app/src/main/java/Foo.kt");
});

test("makeRepoRelative strips file:// URL prefix", () => {
  const path = "file:///home/runner/work/hesabyaar/hesabyaar/app/src/main/java/Foo.kt";
  assert.equal(makeRepoRelative(path, "hesabyaar"), "app/src/main/java/Foo.kt");
});

test("makeRepoRelative leaves other paths unchanged", () => {
  const path = "/some/other/path/Foo.kt";
  assert.equal(makeRepoRelative(path, "hesabyaar"), path);
});

// ---------------------------------------------------------------------------
// normalizeMessage
// ---------------------------------------------------------------------------

test("normalizeMessage collapses whitespace", () => {
  assert.equal(normalizeMessage("  foo   bar  \n  baz  "), "foo bar baz");
});

// ---------------------------------------------------------------------------
// escapeMarkdown
// ---------------------------------------------------------------------------

test("escapeMarkdown escapes pipe and angle brackets", () => {
  assert.equal(escapeMarkdown("foo|bar<baz>"), "foo\\|bar&lt;baz&gt;");
});

// ---------------------------------------------------------------------------
// buildSourceLink
// ---------------------------------------------------------------------------

test("buildSourceLink builds correct URL", () => {
  const link = buildSourceLink("owner/repo", SHA, "app/src/Foo.kt", 42);
  assert.equal(link, `https://github.com/owner/repo/blob/${SHA}/app/src/Foo.kt#L42`);
});

// ---------------------------------------------------------------------------
// shortSha
// ---------------------------------------------------------------------------

test("shortSha returns first 7 chars", () => {
  assert.equal(shortSha(SHA), "abcdef1");
});

// ---------------------------------------------------------------------------
// categoriseKotlinMessage
// ---------------------------------------------------------------------------

test("categoriseKotlinMessage detects deprecation", () => {
  assert.equal(categoriseKotlinMessage("Unused import deprecated"), "deprecation");
});

test("categoriseKotlinMessage detects unsafe call", () => {
  assert.equal(categoriseKotlinMessage("Unnecessary safe call"), "unsafe-call");
  assert.equal(categoriseKotlinMessage("Unnecessary non-null assertion"), "unsafe-call");
});

test("categoriseKotlinMessage detects tautology", () => {
  assert.equal(categoriseKotlinMessage("Condition always 'true'"), "tautology");
  assert.equal(categoriseKotlinMessage("Condition always 'false'"), "tautology");
  assert.equal(categoriseKotlinMessage("Check for instance is always true"), "tautology");
});

test("categoriseKotlinMessage detects redundant cast", () => {
  assert.equal(categoriseKotlinMessage("Cast is redundant"), "redundant-cast");
  assert.equal(categoriseKotlinMessage("No cast needed"), "redundant-cast");
});

test("categoriseKotlinMessage detects unused", () => {
  assert.equal(categoriseKotlinMessage("Variable is unused"), "unused");
  assert.equal(categoriseKotlinMessage("Function is never used"), "unused");
});

test("categoriseKotlinMessage detects non-exhaustive when", () => {
  assert.equal(categoriseKotlinMessage("When expression is not exhaustive"), "non-exhaustive-when");
});

test("categoriseKotlinMessage defaults to kotlin-other", () => {
  assert.equal(categoriseKotlinMessage("Some other warning"), "kotlin-other");
});

// ---------------------------------------------------------------------------
// parseLogLine - Kotlin
// ---------------------------------------------------------------------------

test("parseLogLine parses Kotlin warning with file:// prefix", () => {
  const line = `w: file:///home/runner/work/hesabyaar/hesabyaar/app/src/main/java/io/github/mojri/hesabyar/api/AiProvider.kt:142:23 Unnecessary safe call on nullable receiver`;
  const f = parseLogLine(line, REPO, "Android CI", "build");
  assert.ok(f);
  assert.equal(f.category, "unsafe-call");
  assert.equal(f.file, "app/src/main/java/io/github/mojri/hesabyar/api/AiProvider.kt");
  assert.equal(f.line, 142);
  assert.ok(f.message.includes("Unnecessary safe call"));
});

test("parseLogLine parses Kotlin warning without file:// prefix", () => {
  const line = `w: /home/runner/work/hesabyaar/hesabyaar/app/src/main/java/Foo.kt:10:5 Unused import`;
  const f = parseLogLine(line, REPO, "Android CI", "build");
  assert.ok(f);
  assert.equal(f.category, "unused");
  assert.equal(f.file, "app/src/main/java/Foo.kt");
  assert.equal(f.line, 10);
});

test("parseLogLine parses Kotlin warning without column number", () => {
  const line = `w: file:///home/runner/work/hesabyaar/hesabyaar/app/src/main/java/Foo.kt:42 Unused import`;
  const f = parseLogLine(line, REPO, "Android CI", "build");
  assert.ok(f);
  assert.equal(f.category, "unused");
  assert.equal(f.file, "app/src/main/java/Foo.kt");
  assert.equal(f.line, 42);
});

test("parseLogLine parses Kotlin always-true condition", () => {
  const line = `w: file:///home/runner/work/hesabyaar/hesabyaar/app/src/main/java/Foo.kt:332:13 Condition 'x > 0' is always 'true'`;
  const f = parseLogLine(line, REPO, "Android CI", "build");
  assert.ok(f);
  assert.equal(f.category, "tautology");
  assert.equal(f.line, 332);
});

test("parseLogLine parses Kotlin redundant cast", () => {
  const line = `w: file:///home/runner/work/hesabyaar/hesabyaar/app/src/test/java/FooTest.kt:24:17 Cast is redundant`;
  const f = parseLogLine(line, REPO, "Android CI", "test");
  assert.ok(f);
  assert.equal(f.category, "redundant-cast");
  assert.equal(f.line, 24);
});

test("parseLogLine parses Kotlin unused expression in generated file", () => {
  const line = `w: file:///home/runner/work/hesabyaar/hesabyaar/app/src/main/java/io/github/mojri/hesabyar/rust/hesabyar_core.kt:1086:9 Expression is unused`;
  const f = parseLogLine(line, REPO, "Android CI", "build");
  assert.ok(f);
  assert.equal(f.category, "unused");
  assert.ok(f.file.endsWith("hesabyar_core.kt"));
});

// ---------------------------------------------------------------------------
// parseLogLine - Gradle
// ---------------------------------------------------------------------------

test("parseLogLine parses Deprecated Gradle features", () => {
  const line = "Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.";
  const f = parseLogLine(line, REPO, "Android CI", "build");
  assert.ok(f);
  assert.equal(f.category, "gradle");
});

test("parseLogLine parses Gradle scheduled removal", () => {
  const line = "The Task.leftShift(Closure) method has been deprecated. This is scheduled to be removed in Gradle 9.0.";
  const f = parseLogLine(line, REPO, "Android CI", "build");
  assert.ok(f);
  assert.equal(f.category, "gradle");
});

test("parseLogLine parses Kapt deprecation", () => {
  const line = "w: Kapt support is deprecated and will be removed in a future release";
  const f = parseLogLine(line, REPO, "Android CI", "build");
  assert.ok(f);
  assert.equal(f.category, "gradle");
});

test("parseLogLine parses AGP WARNING", () => {
  const line = "WARNING: The option 'android.enableR8' is deprecated";
  const f = parseLogLine(line, REPO, "Android CI", "build");
  assert.ok(f);
  assert.equal(f.category, "gradle");
});

// ---------------------------------------------------------------------------
// parseLogLine - Workflow commands
// ---------------------------------------------------------------------------

test("parseLogLine parses ::warning command", () => {
  const line = "::warning file=app/src/Foo.kt,line=10::Deprecated API used";
  const f = parseLogLine(line, REPO, "Android CI", "build");
  assert.ok(f);
  assert.equal(f.category, "script");
  assert.equal(f.file, "app/src/Foo.kt");
  assert.equal(f.line, 10);
});

test("parseLogLine parses ::warning command without commas", () => {
  const line = "::warning file=app/src/Foo.kt::Deprecated API used";
  const f = parseLogLine(line, REPO, "Android CI", "build");
  assert.ok(f);
  assert.equal(f.category, "script");
  assert.equal(f.file, "app/src/Foo.kt");
  assert.equal(f.line, undefined);
  assert.equal(f.message, "Deprecated API used");
});

// ---------------------------------------------------------------------------
// parseLogLine - Node/npm
// ---------------------------------------------------------------------------

test("parseLogLine parses npm warn deprecated", () => {
  const line = "npm warn deprecated package@1.0.0: Package is deprecated";
  const f = parseLogLine(line, REPO, "Node CI", "install");
  assert.ok(f);
  assert.equal(f.category, "node");
});

test("parseLogLine parses npm WARN deprecated (uppercase)", () => {
  const line = "npm WARN deprecated package@1.0.0: Package is deprecated";
  const f = parseLogLine(line, REPO, "Node CI", "install");
  assert.ok(f);
  assert.equal(f.category, "node");
});

test("parseLogLine parses Node.js DeprecationWarning", () => {
  const line = "(node:1234) [DEP0040] DeprecationWarning: The `punycode` module is deprecated";
  const f = parseLogLine(line, REPO, "Node CI", "test");
  assert.ok(f);
  assert.equal(f.category, "node");
});

test("parseLogLine parses ExperimentalWarning", () => {
  const line = "(node:1234) ExperimentalWarning: The Fetch API is experimental";
  const f = parseLogLine(line, REPO, "Node CI", "test");
  assert.ok(f);
  assert.equal(f.category, "node");
});

// ---------------------------------------------------------------------------
// parseLogLine - Python
// ---------------------------------------------------------------------------

test("parseLogLine parses Python DeprecationWarning", () => {
  const line = "DeprecationWarning: 'collections.abc' should be used instead of 'collections'";
  const f = parseLogLine(line, REPO, "Python CI", "test");
  assert.ok(f);
  assert.equal(f.category, "python");
});

test("parseLogLine parses Python FutureWarning", () => {
  const line = "FutureWarning: The default dtype will change";
  const f = parseLogLine(line, REPO, "Python CI", "test");
  assert.ok(f);
  assert.equal(f.category, "python");
});

test("parseLogLine parses Python UserWarning", () => {
  const line = "UserWarning: Some warning";
  const f = parseLogLine(line, REPO, "Python CI", "test");
  assert.ok(f);
  assert.equal(f.category, "python");
});

test("parseLogLine parses pip WARNING", () => {
  const line = "WARNING: You are using pip version 21.0; however, version 22.0 is available.";
  const f = parseLogLine(line, REPO, "Python CI", "install");
  assert.ok(f);
  assert.equal(f.category, "python");
});

test("parseLogLine parses pip DEPRECATION", () => {
  const line = "DEPRECATION: Python 3.6 support will be removed";
  const f = parseLogLine(line, REPO, "Python CI", "install");
  assert.ok(f);
  assert.equal(f.category, "python");
});

// ---------------------------------------------------------------------------
// parseLogLine - Shell/generic
// ---------------------------------------------------------------------------

test("parseLogLine parses warning: prefix", () => {
  const line = "warning: unused variable 'x'";
  const f = parseLogLine(line, REPO, "Script CI", "build");
  assert.ok(f);
  assert.equal(f.category, "script");
});

test("parseLogLine parses Warning: prefix", () => {
  const line = "Warning: deprecated function";
  const f = parseLogLine(line, REPO, "Script CI", "build");
  assert.ok(f);
  assert.equal(f.category, "script");
});

test("parseLogLine parses WARN prefix", () => {
  const line = "WARN Something deprecated";
  const f = parseLogLine(line, REPO, "Script CI", "build");
  assert.ok(f);
  assert.equal(f.category, "script");
});

// ---------------------------------------------------------------------------
// parseLog - Rust multi-line
// ---------------------------------------------------------------------------

test("parseLog parses Rust two-line warning", () => {
  const text = `warning: unused import: std::io::Read
 --> src/main.rs:10:5
`;
  const findings = parseLog(text, REPO, "Rust CI", "test");
  assert.equal(findings.length, 1);
  assert.equal(findings[0].category, "rust");
  assert.equal(findings[0].file, "src/main.rs");
  assert.equal(findings[0].line, 10);
  assert.ok(findings[0].message.includes("unused import"));
});

test("parseLog parses Rust warning without location", () => {
  const text = "warning: unused crate dependency\n";
  const findings = parseLog(text, REPO, "Rust CI", "test");
  assert.equal(findings.length, 1);
  assert.equal(findings[0].category, "rust");
  assert.equal(findings[0].file, undefined);
  assert.equal(findings[0].line, undefined);
});

test("parseLog does not misclassify generic warning as Rust", () => {
  const text = "warning: something deprecated in script\n";
  const findings = parseLog(text, REPO, "Script CI", "build");
  assert.equal(findings.length, 1);
  assert.equal(findings[0].category, "script");
});

test("parseLog handles mixed lines", () => {
  const text = `warning: unused import
 --> src/lib.rs:5:1
w: file:///home/runner/work/hesabyaar/hesabyaar/app/src/Foo.kt:10:5 Unused import
`;
  const findings = parseLog(text, REPO, "Mixed CI", "test");
  assert.equal(findings.length, 2);
  assert.equal(findings[0].category, "rust");
  assert.equal(findings[1].category, "unused");
});

// ---------------------------------------------------------------------------
// categoriseAnnotation
// ---------------------------------------------------------------------------

test("categoriseAnnotation returns null for failure level", () => {
  const ann = { annotation_level: "failure", message: "error", path: "Foo.kt", start_line: 10 };
  assert.equal(categoriseAnnotation(ann, REPO), null);
});

test("categoriseAnnotation detects Node.js 20 actions deprecated", () => {
  const ann = { annotation_level: "warning", message: "Node.js 20 actions are deprecated", path: undefined, start_line: undefined };
  const cat = categoriseAnnotation(ann, REPO);
  assert.ok(cat);
  assert.equal(cat.category, "actions-runtime");
});

test("categoriseAnnotation detects set-output deprecation", () => {
  const ann = { annotation_level: "warning", message: "The `set-output` command is deprecated", path: undefined, start_line: undefined };
  const cat = categoriseAnnotation(ann, REPO);
  assert.ok(cat);
  assert.equal(cat.category, "actions-runtime");
});

test("categoriseAnnotation detects unexpected input", () => {
  const ann = { annotation_level: "notice", message: "Unexpected input 'foo'", path: undefined, start_line: undefined };
  const cat = categoriseAnnotation(ann, REPO);
  assert.ok(cat);
  assert.equal(cat.category, "actions-input");
});

test("categoriseAnnotation categorises Kotlin file by message", () => {
  const ann = { annotation_level: "warning", message: "Unnecessary safe call", path: "app/src/Foo.kt", start_line: 10 };
  const cat = categoriseAnnotation(ann, REPO);
  assert.ok(cat);
  assert.equal(cat.category, "unsafe-call");
  assert.equal(cat.file, "app/src/Foo.kt");
  assert.equal(cat.line, 10);
});

test("categoriseAnnotation categorises Rust file", () => {
  const ann = { annotation_level: "warning", message: "unused variable", path: "src/main.rs", start_line: 5 };
  const cat = categoriseAnnotation(ann, REPO);
  assert.ok(cat);
  assert.equal(cat.category, "rust");
  assert.equal(cat.file, "src/main.rs");
});

test("categoriseAnnotation categorises JS file", () => {
  const ann = { annotation_level: "warning", message: "deprecated", path: "script.js", start_line: 1 };
  const cat = categoriseAnnotation(ann, REPO);
  assert.ok(cat);
  assert.equal(cat.category, "node");
});

test("categoriseAnnotation categorises Python file", () => {
  const ann = { annotation_level: "warning", message: "deprecated", path: "script.py", start_line: 1 };
  const cat = categoriseAnnotation(ann, REPO);
  assert.ok(cat);
  assert.equal(cat.category, "python");
});

// ---------------------------------------------------------------------------
// dedupe
// ---------------------------------------------------------------------------

test("dedupe groups by category+file+line+message", () => {
  const findings = [
    { category: "unused", file: "a.kt", line: 10, message: "x is unused", workflow: "CI", job: "build" },
    { category: "unused", file: "a.kt", line: 10, message: "x is unused", workflow: "CI", job: "test" },
    { category: "unused", file: "a.kt", line: 11, message: "y is unused", workflow: "CI", job: "build" },
  ];
  const d = dedupe(findings);
  assert.equal(d.length, 2);
  const first = d.find((x) => x.line === 10);
  assert.equal(first.count, 2);
  assert.equal(first.locations.size, 2);
  assert.ok(first.locations.has("CI/build"));
  assert.ok(first.locations.has("CI/test"));
});

test("dedupe normalises message whitespace", () => {
  const findings = [
    { category: "unused", file: "a.kt", line: 10, message: "x  is   unused", workflow: "CI", job: "build" },
    { category: "unused", file: "a.kt", line: 10, message: "x is unused", workflow: "CI", job: "test" },
  ];
  const d = dedupe(findings);
  assert.equal(d.length, 1);
  assert.equal(d[0].count, 2);
});

// ---------------------------------------------------------------------------
// renderComment
// ---------------------------------------------------------------------------

test("renderComment zero findings shows success message", () => {
  const md = renderComment({ findings: [], sha: SHA, repo: REPO, runsScanned: 3, codeCompiled: true });
  assert.ok(md.includes("<!-- ci-warnings-report -->"));
  assert.ok(md.includes("✅ No warnings in 3 workflow runs"));
  assert.ok(md.includes(shortSha(SHA)));
});

test("renderComment includes title and summary table", () => {
  const findings = [
    { category: "unsafe-call", file: "a.kt", line: 10, message: "Unnecessary safe call", workflow: "CI", job: "build" },
    { category: "deprecation", file: "b.kt", line: 20, message: "Deprecated API", workflow: "CI", job: "build" },
  ];
  const md = renderComment({ findings, sha: SHA, repo: REPO, runsScanned: 1, codeCompiled: true });
  assert.ok(md.includes("### ⚠️ CI warnings report"));
  assert.ok(md.includes("**Total:** 2 warnings"));
  assert.ok(md.includes("Unnecessary safe call"));
  assert.ok(md.includes("Deprecated API"));
  assert.ok(md.includes("Category | Count"));
  assert.ok(md.includes("Unnecessary safe call"));
  assert.ok(md.includes("Kotlin deprecation"));
});

test("renderComment orders categories correctly", () => {
  const findings = [
    { category: "rust", file: "a.rs", line: 1, message: "rust warn", workflow: "CI", job: "build" },
    { category: "deprecation", file: "a.kt", line: 1, message: "dep", workflow: "CI", job: "build" },
    { category: "actions-runtime", file: undefined, line: undefined, message: "action deprecated", workflow: "CI", job: "build" },
  ];
  const md = renderComment({ findings, sha: SHA, repo: REPO, runsScanned: 1, codeCompiled: true });
  // actions-runtime should appear before deprecation, which appears before rust
  const idxActions = md.indexOf("Actions runtime deprecation");
  const idxDep = md.indexOf("Kotlin deprecation");
  const idxRust = md.indexOf("Rust warning");
  assert.ok(idxActions < idxDep, "actions-runtime before deprecation");
  assert.ok(idxDep < idxRust, "deprecation before rust");
});

test("renderComment tags generated hesabyar_core.kt files", () => {
  const findings = [
    { category: "unused", file: "app/src/main/java/io/github/mojri/hesabyar/rust/hesabyar_core.kt", line: 1086, message: "Expression is unused", workflow: "CI", job: "build" },
  ];
  const md = renderComment({ findings, sha: SHA, repo: REPO, runsScanned: 1, codeCompiled: true });
  assert.ok(md.includes("(generated — fix in Rust/uniffi, not by hand)"));
});

test("renderComment includes codeCompiled note when false", () => {
  const findings = [
    { category: "unused", file: "a.kt", line: 10, message: "unused", workflow: "CI", job: "build" },
  ];
  const md = renderComment({ findings, sha: SHA, repo: REPO, runsScanned: 1, codeCompiled: false });
  assert.ok(md.includes("Kotlin compile was cached/up-to-date"));
});

test("renderComment does not include codeCompiled note when true or undefined", () => {
  const findings = [
    { category: "unused", file: "a.kt", line: 10, message: "unused", workflow: "CI", job: "build" },
  ];
  let md = renderComment({ findings, sha: SHA, repo: REPO, runsScanned: 1, codeCompiled: true });
  assert.ok(!md.includes("Kotlin compile was cached"));
  md = renderComment({ findings, sha: SHA, repo: REPO, runsScanned: 1, codeCompiled: undefined });
  assert.ok(!md.includes("Kotlin compile was cached"));
});

test("renderComment truncates long category lists with weighted counts", () => {
  const findings = [];
  for (let i = 0; i < 60; i++) {
    // Each finding has count 2 (via duplicate)
    findings.push({ category: "unused", file: `a${i}.kt`, line: i, message: `unused ${i}`, workflow: "CI", job: "build" });
    findings.push({ category: "unused", file: `a${i}.kt`, line: i, message: `unused ${i}`, workflow: "CI", job: "test" });
  }
  const md = renderComment({ findings, sha: SHA, repo: REPO, runsScanned: 1, codeCompiled: true });
  // Remaining items: 10 items with count 2 = 20 weighted warnings
  assert.ok(md.includes("…and 20 more (see job logs)"));
});

test("renderComment truncates overall comment at 60000 chars", () => {
  const findings = [];
  // Use multiple categories with long unique messages to bypass per-category 50 limit
  // and force the total markdown length past 60000 characters.
  const categories = [
    "actions-runtime", "deprecation", "gradle", "unsafe-call", "tautology",
    "redundant-cast", "unused", "rust", "node", "python", "script", "other"
  ];
  for (const cat of categories) {
    for (let i = 0; i < 50; i++) {
      findings.push({
        category: cat,
        file: `very/long/nested/path/to/source/file/number_${i}.kt`,
        line: i * 10,
        message: `Extremely long warning message intended to blow past sixty thousand characters `.repeat(5),
        workflow: "Workflow Name",
        job: "Job Name",
      });
    }
  }
  const md = renderComment({ findings, sha: SHA, repo: REPO, runsScanned: 1, codeCompiled: true });
  assert.ok(md.length <= 60000);
  assert.ok(md.includes("(comment truncated, see job logs for full details)"));
});

test("renderComment includes count suffix and locations for duplicates", () => {
  const findings = [
    { category: "unused", file: "a.kt", line: 10, message: "x is unused", workflow: "CI", job: "build" },
    { category: "unused", file: "a.kt", line: 10, message: "x is unused", workflow: "CI", job: "test" },
  ];
  const md = renderComment({ findings, sha: SHA, repo: REPO, runsScanned: 1, codeCompiled: true });
  assert.ok(md.includes("×2"));
  assert.ok(md.includes("CI/build"));
  assert.ok(md.includes("CI/test"));
});

test("renderComment puts messages in code spans (no escaping needed)", () => {
  const findings = [
    { category: "unused", file: "a.kt", line: 10, message: "foo|bar<baz>", workflow: "CI", job: "build" },
  ];
  const md = renderComment({ findings, sha: SHA, repo: REPO, runsScanned: 1, codeCompiled: true });
  assert.ok(md.includes("`foo|bar<baz>`"));
});

// ---------------------------------------------------------------------------
// timestamp + ANSI stripping integration
// ---------------------------------------------------------------------------

test("parseLogLine handles timestamp and ANSI codes in Kotlin warning", () => {
  const line = "2026-10-04T05:12:33.1234567Z \x1b[33mw: file:///home/runner/work/hesabyaar/hesabyaar/app/src/Foo.kt:10:5 \x1b[0mUnnecessary safe call";
  const f = parseLogLine(line, REPO, "CI", "build");
  assert.ok(f);
  assert.equal(f.category, "unsafe-call");
  assert.equal(f.file, "app/src/Foo.kt");
});
// ---------------------------------------------------------------------------
// Every workflow must be watched by ci-warnings-report.yml
// ---------------------------------------------------------------------------

test("ci-warnings-report.yml lists every other workflow by name", async () => {
  const fs = await import("node:fs");
  const path = await import("node:path");
  const { fileURLToPath } = await import("node:url");
  const dir = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "workflows");
  const report = fs.readFileSync(path.join(dir, "ci-warnings-report.yml"), "utf8");
  const block = report.match(/workflow_run:\s*\n\s*workflows:\s*\n((?:\s+-\s+.+\n)+)/);
  assert.ok(block, "workflow_run.workflows list not found");
  const watched = new Set(
    [...block[1].matchAll(/-\s+"?([^"\n]+?)"?\s*$/gm)].map((m) => m[1].trim()),
  );
  const missing = [];
  for (const file of fs.readdirSync(dir)) {
    if (!/\.ya?ml$/.test(file) || file === "ci-warnings-report.yml") continue;
    const text = fs.readFileSync(path.join(dir, file), "utf8");
    const on = text.match(/^on:\s*\n((?:[ \t]+.*\n|\s*\n)+)/m);
    const triggers = on ? [...on[1].matchAll(/^  ([a-z_]+):/gm)].map((m) => m[1]) : [];
    // Reusable workflows report under the caller's run.
    if (triggers.length === 1 && triggers[0] === "workflow_call") continue;
    const name = text.match(/^name:\s*"?(.+?)"?\s*$/m)?.[1];
    if (!watched.has(name)) missing.push(`${file} (${name})`);
  }
  assert.deepEqual(missing, [], `add these to ci-warnings-report.yml workflow_run.workflows: ${missing.join(", ")}`);
});

test("parseLog reads the two-line Rust form behind ISO timestamps", () => {
  const log = [
    "2026-10-04T05:12:33.1234567Z warning: unused variable: `x`",
    "2026-10-04T05:12:33.1234568Z   --> src/forecast.rs:12:9",
  ].join("\n");
  const [f] = parseLog(log, REPO, "Rust Lint", "clippy");
  assert.equal(f.category, "rust");
  assert.equal(f.file, "src/forecast.rs");
  assert.equal(f.line, 12);
});

test("parseLogLine ignores command echoes and ##[warning] lines", () => {
  assert.equal(parseLogLine("2026-10-04T05:12:33.1234567Z ##[group]Run ./gradlew assembleDebug --warning-mode all", REPO, "CI", "b"), null);
  assert.equal(parseLogLine("##[warning]Node.js 20 actions are deprecated", REPO, "CI", "b"), null);
  assert.equal(parseLogLine("ok 18 - parseLogLine parses Kotlin warning", REPO, "CI", "b"), null);
});

test("renderComment survives backticks and links path-less items to their job", () => {
  const findings = [
    { category: "rust", message: "unused variable: `x`", workflow: "Rust Lint", job: "clippy", jobUrl: "https://github.com/o/r/actions/runs/1/job/2" },
  ];
  const md = renderComment({ findings, sha: SHA, repo: REPO, runsScanned: 1 });
  assert.ok(md.includes("``` unused variable: `x` ```"));
  assert.ok(md.includes("[Rust Lint/clippy](https://github.com/o/r/actions/runs/1/job/2)"));
});

test("renderComment safely handles markdown delimiters and path characters", () => {
  const findings = [
    {
      category: "unused",
      file: "path/[with]/brackets & (parens)/file#1.kt",
      line: 15,
      message: "warning with backticks `x` and brackets [link](https://evil.com)",
      workflow: "CI",
      job: "build",
    },
  ];
  const md = renderComment({ findings, sha: SHA, repo: REPO, runsScanned: 1, codeCompiled: true });
  // Check that the source link safely encodes the path
  assert.ok(md.includes("%5Bwith%5D"));
  assert.ok(md.includes("%26"));
  assert.ok(md.includes("%231.kt"));
  // Check that the label brackets are escaped to avoid Markdown injection
  assert.ok(md.includes("\\[with\\]"));
  // Check that message with backticks is fenced
  assert.ok(md.includes("``` warning with backticks `x` and brackets [link](https://evil.com) ```"));
});
