// CI Warnings Report — parse GitHub Actions logs and annotations into a single PR comment.
// Pure ESM, Node 20+ built-ins only. Export pure functions for testability.


// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Strip GitHub Actions log timestamp prefix and ANSI escape codes.
 * Example prefix: "2026-10-04T05:12:33.1234567Z "
 */
function stripLogPrefix(line) {
  // Remove ISO timestamp with microseconds + 'Z ' prefix
  let s = line.replace(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{7}Z\s*/, "");
  // Remove ANSI escape sequences (use unicode escape to avoid no-control-regex lint warning)
  s = s.replace(/\u001b\[[0-9;]*m/g, "");
  return s;
}

/**
 * Normalize a message for deduping: collapse whitespace, trim.
 */
function normalizeMessage(msg) {
  return msg.replace(/\s+/g, " ").trim();
}

/**
 * Make a path repo-relative by stripping the GitHub Actions runner prefix.
 * "/home/runner/work/<repo>/<repo>/..." -> "app/src/..."
 */
function makeRepoRelative(path) {
  // Runner checkouts live at /home/runner/work/<name>/<name>/; <name> is the
  // repo name (not owner/repo), so match any two segments rather than `repo`.
  return path.replace(/^(?:file:\/\/)?\/?home\/runner\/work\/[^/]+\/[^/]+\//, "");
}

/**
 * Escape characters that break markdown tables or links.
 */
function escapeMarkdown(s) {
  return String(s)
    .replace(/\\/g, "\\\\")
    .replace(/\[/g, "\\[")
    .replace(/\]/g, "\\]")
    .replace(/\(/g, "\\(")
    .replace(/\)/g, "\\)")
    .replace(/\|/g, "\\|")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

/**
 * Render text as an inline code span.
 *
 * The source text is log/annotation content that a fork PR can influence, so
 * we must not let a backtick run in it close the span and inject Markdown. When
 * the message itself contains backticks, wrap it in a fence of 3 backticks
 * (longer than any single backtick run in the message); otherwise use a single
 * backtick. A single backtick inside a 1-backtick span would close it, so the
 * 3-backtick fence is required for backtick-bearing messages.
 */
function codeSpan(s, max = 300) {
  const t = normalizeMessage(s).slice(0, max) + (s.length > max ? "…" : "");
  if (t.includes("`")) {
    const maxConsecutive = (t.match(/`+/g) || []).reduce((max, m) => Math.max(max, m.length), 0);
    const fence = "`".repeat(Math.max(3, maxConsecutive + 1));
    return `${fence} ${t} ${fence}`;
  }
  return `\`${t}\``;
}

/**
 * Build a GitHub source link for a file at a specific line on a given SHA.
 */
function buildSourceLink(repo, sha, file, line) {
  // Percent-encode each path segment so an untrusted path cannot break the URL
  // (e.g. inject "#" or ")") and escape out of the link destination.
  const encodedPath = file.split("/").map(encodeURIComponent).join("/");
  return `https://github.com/${repo}/blob/${sha}/${encodedPath}#L${line}`;
}


/**
 * Short SHA (7 chars).
 */
function shortSha(sha) {
  return sha.slice(0, 7);
}

// ---------------------------------------------------------------------------
// Parsers for individual log lines
// ---------------------------------------------------------------------------

/**
 * Parse a single log line into a finding object or null.
 * Returns { category, file, line, message, rawLine, workflow, job }
 */
function parseLogLine(line, repo, workflow, job) {
  const clean = stripLogPrefix(line);
  // `##[warning]` lines are also published as check-run annotations, which
  // carry file/line; `+ cmd` are command echoes. The Gradle invocation is
  // echoed as a log line (`./gradlew … --warning-mode all`); that text is not
  // a warning. Skip only lines that actually look like a gradlew command, not
  // any line that merely mentions the flag.
  if (/^##\[/.test(clean) || /^\+ /.test(clean)) return null;
  if ((/--warning-mode/.test(clean) && /gradlew/.test(clean)) || /^##\[group\]Run\b.*--warning-mode/.test(clean)) return null;
  if (!clean) return null;

  // Kotlin compiler warnings: w: file:///home/.../Foo.kt:142:23 Unnecessary safe call ...
  // Also non-file:// form: w: /home/.../Foo.kt:142:23 ...  The column is
  // optional: some compiler outputs emit only line:message.
  const kotlinMatch = clean.match(
    /^w:\s+(?:file:\/\/\/)?([^\s:]+):(\d+)(?::\d+)?\s+(.+)$/
  );
  if (kotlinMatch) {
    const [, file, lineStr, message] = kotlinMatch;
    const relFile = makeRepoRelative(file);
    const category = categoriseKotlinMessage(message);
    return { category, file: relFile, line: parseInt(lineStr, 10), message, workflow, job };
  }

  // Gradle deprecation warnings
  if (clean.includes("Deprecated Gradle features were used")) {
    return { category: "gradle", file: undefined, line: undefined, message: clean, workflow, job };
  }
  if (clean.match(/has been deprecated\.\s*This is scheduled to be removed in Gradle/)) {
    return { category: "gradle", file: undefined, line: undefined, message: clean, workflow, job };
  }
  if (clean.match(/^w:\s+Kapt support/)) {
    return { category: "gradle", file: undefined, line: undefined, message: clean, workflow, job };
  }
  // AGP/Android Gradle warnings print as `WARNING: <...>` with a known AGP/Gradle
  // token. Require the recognised prefix so a script warning merely mentioning
  // "android.enableR8" does not get mis-filed as a Gradle deprecation.
  if (clean.match(/^WARNING:\s/) && clean.match(/\b(AGP|Android Gradle Plugin|android\.enableR8|android\.useAndroidX|Gradle \d+|deprecated Gradle)\b/i)) {
    return { category: "gradle", file: undefined, line: undefined, message: clean, workflow, job };
  }

  // Generic workflow command warnings echoed in logs:
  // ::warning file=...,line=...::message (properties independently optional,
  // commas optional between parameters)
  const workflowCmdMatch = clean.match(
    /^::warning\s+(?:file=([^,\s]+),?)?\s*(?:line=(\d+),?)?\s*(?:col=(\d+),?)?::(.+)$/
  );
  if (workflowCmdMatch) {
    const [, file, lineStr, , message] = workflowCmdMatch;
    return {
      category: "script",
      file: file ? makeRepoRelative(file) : undefined,
      line: lineStr ? parseInt(lineStr, 10) : undefined,
      message,
      workflow,
      job,
    };
  }

  // Python warnings
  if (clean.match(/^(DeprecationWarning|FutureWarning|UserWarning):/)) {
    return { category: "python", file: undefined, line: undefined, message: clean, workflow, job };
  }
  if (clean.match(/^DEPRECATION:\s/) || (clean.match(/^WARNING:\s/) && clean.match(/\b(pip|python|setuptools|wheel|venv)\b/i))) {
    // pip warnings
    return { category: "python", file: undefined, line: undefined, message: clean, workflow, job };
  }

  // Lines starting with warning:/Warning:/WARN that are not a Rust diagnostic
  // (Rust is handled earlier by looking ahead for the `--> loc` line) land here.
  if (clean.match(/^(warning:|Warning:|WARN\s+)/i)) {
    return { category: "script", file: undefined, line: undefined, message: clean, workflow, job };
  }

  // Node/npm deprecation warnings
  if (clean.match(/^npm\s+(warn|WARN)\s+deprecated\s+/i)) {
    return { category: "node", file: undefined, line: undefined, message: clean, workflow, job };
  }
  if (clean.match(/^\(node:\d+\)\s+\[DEP\d+\]\s+DeprecationWarning:/)) {
    return { category: "node", file: undefined, line: undefined, message: clean, workflow, job };
  }
  if (clean.includes("ExperimentalWarning")) {
    return { category: "node", file: undefined, line: undefined, message: clean, workflow, job };
  }

  // Catch-all: anything that looks like a warning but didn't match above
  // Heuristic: contains "warning" or "deprecat" (case-insensitive) and not an error.
  // Kotlin compiler errors start with `e:`, which must also be excluded.
  if (
    clean.match(/\b(warning|warn)\b\s*[:\]!]|\bdeprecat(ed|ion)\b/i) &&
    !clean.match(/^(?:e:|error|Error|ERROR)/)
  ) {
    return { category: "other", file: undefined, line: undefined, message: clean, workflow, job };
  }

  return null;
}

/**
 * Categorise Kotlin warning message into sub-category.
 */
function categoriseKotlinMessage(message) {
  const msg = message.toLowerCase();
  if (msg.includes("deprecated")) return "deprecation";
  if (msg.includes("unnecessary safe call") || msg.includes("unnecessary non-null assertion")) return "unsafe-call";
  if (msg.includes("always 'true'") || msg.includes("always 'false'") || msg.includes("check for instance is always")) return "tautology";
  if (msg.includes("cast is redundant") || msg.includes("no cast needed")) return "redundant-cast";
  if (msg.includes("is unused") || msg.includes("is never used") || /^unused\b/.test(msg)) return "unused";
  if (msg.includes("non-exhaustive") || (msg.includes("when") && msg.includes("not exhaustive"))) return "non-exhaustive-when";
  return "kotlin-other";
}

/**
 * Parse multi-line text (handles Rust 2-line warnings).
 * Returns array of findings.
 */
function parseLog(text, repo, workflow, job) {
  const lines = text.split(/\r?\n/);
  const findings = [];
  let i = 0;
  while (i < lines.length) {
    const line = stripLogPrefix(lines[i]);
    // Rust warning: first line "warning: <msg>", second line " --> path:line:col"
    const rustFirst = line.match(/^warning:\s*(.+)$/i);
    if (rustFirst) {
      const message = rustFirst[1];
      // Look ahead for the location line (`--> file:line:col`). A Rust compiler
      // diagnostic always prints the location on the next line.
      if (i + 1 < lines.length) {
        const nextLine = stripLogPrefix(lines[i + 1]);
        const locMatch = nextLine.match(/^\s*-->\s*([^:]+):(\d+):\d+/);
        if (locMatch) {
          const [, file, lineStr] = locMatch;
          const relFile = makeRepoRelative(file);
          findings.push({
            category: "rust",
            file: relFile,
            line: parseInt(lineStr, 10),
            message,
            workflow,
            job,
          });
          i += 2;
          continue;
        }
      }
      // If no Rust `-->` location follows, this is either a location-less
      // rustc warning (like unused crate dependency) or a generic script warning.
      // Check if it's a known rustc message or let parseLogLine classify it.
      if (/unused\s+crate\s+dependency|cannot\s+find|clippy::/i.test(message)) {
        findings.push({
          category: "rust",
          file: undefined,
          line: undefined,
          message,
          workflow,
          job,
        });
        i++;
        continue;
      }
      // Fall through to normal line parsing so generic script warnings are not misfiled as Rust.
    }
    // Normal single-line parsing
    const finding = parseLogLine(line, repo, workflow, job);
    if (finding) findings.push(finding);
    i++;
  }
  return findings;
}

/**
 * Categorise a check-run annotation into a warning category.
 * Only processes warning/notice level; failures are excluded unless deprecation-related.
 */
function categoriseAnnotation(annotation, repo) {
  const level = annotation.annotation_level;
  const message = annotation.message || "";
  // Exclude failures except deprecation-related ones (GitHub Actions runtime
  // deprecations can be reported at failure level).
  if (level === "failure" && !/deprecat/i.test(message)) return null;

  const path = annotation.path;
  const line = annotation.start_line;

  // Actions runtime deprecations: only match known GH Actions deprecation messages.
  const isKnownActionsRuntime =
    message.includes("Node.js 20 actions are deprecated") ||
    (message.includes("actions are deprecated") && message.includes("will be removed")) ||
    message.includes("The `set-output` command is deprecated") ||
    message.includes("The `set-env` command is deprecated") ||
    /runner version.*deprecated/i.test(message);

  if (isKnownActionsRuntime) {
    return { category: "actions-runtime", file: undefined, line: undefined, message };
  }

  // Unexpected input warnings
  if (message.includes("Unexpected input")) {
    return { category: "actions-input", file: undefined, line: undefined, message };
  }

  // If annotation points to a source file, categorise by file extension/content
  if (path) {
    const relPath = makeRepoRelative(path);
    if (relPath.endsWith(".kt") || relPath.endsWith(".kts")) {
      // Could be a Kotlin compiler annotation
      const cat = categoriseKotlinMessage(message);
      return { category: cat, file: relPath, line, message };
    }
    if (relPath.endsWith(".rs")) {
      return { category: "rust", file: relPath, line, message };
    }
    if (relPath.endsWith(".js") || relPath.endsWith(".ts") || relPath.endsWith(".mjs")) {
      return { category: "node", file: relPath, line, message };
    }
    if (relPath.endsWith(".py")) {
      return { category: "python", file: relPath, line, message };
    }
    return { category: "other", file: relPath, line, message };
  }

  // A path-less annotation is either a runner/action notice or a script's
  // `::warning::`. Classify as actions-runtime when the message matches a known
  // GitHub Actions deprecation signature OR explicitly names a deprecated action
  // (e.g. "deprecated-action", "action ... deprecated"). Otherwise treat as
  // script so unrelated deprecations aren't mislabeled as runtime ones. The
  // action-context match is anchored so it only fires on clear action references,
  // not any sentence that merely contains "action" and "deprecated" far apart.
  const isActionsRuntime =
    /Node\.js \d+ actions are deprecated/i.test(message) ||
    /actions are deprecated.*will be removed/i.test(message) ||
    /set-output command is deprecated/i.test(message) ||
    /set-env command is deprecated/i.test(message) ||
    /runner version.*deprecated/i.test(message) ||
    /\bdeprecated[- ]action\b/i.test(message) ||
    /\bgithub action[s]?\b.*\bdeprecated\b/i.test(message) ||
    /\bdeprecated\b.*\bgithub action[s]?\b/i.test(message);
  const category = isActionsRuntime ? "actions-runtime" : "script";
  return { category, file: undefined, line: undefined, message };
}

// ---------------------------------------------------------------------------
// Deduplication
// ---------------------------------------------------------------------------

/**
 * Deduplicate findings by category+file+line+normalised message.
 * Returns array of { category, file, line, message, count, locations: Set<"workflow/job">, jobUrls: Map<"workflow/job", jobUrl> }
 */
function dedupe(findings) {
  const map = new Map();
  for (const f of findings) {
    const normMsg = normalizeMessage(f.message);
    const key = `${f.category}|${f.file || ""}|${f.line || ""}|${normMsg}`;
    if (!map.has(key)) {
      map.set(key, {
        category: f.category,
        file: f.file,
        line: f.line,
        message: f.message, // Keep original first message for display
        count: 0,
        locations: new Set(),
        jobUrls: new Map(), // Map of location string -> jobUrl
      });
    }
    const entry = map.get(key);
    entry.count++;
    const loc = `${f.workflow}/${f.job}`;
    entry.locations.add(loc);
    if (f.jobUrl) {
      entry.jobUrls.set(loc, f.jobUrl);
    }
  }
  return Array.from(map.values());
}

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------

const CATEGORY_ORDER = [
  "actions-runtime",
  "deprecation",
  "gradle",
  "unsafe-call",
  "tautology",
  "redundant-cast",
  "unused",
  "rust",
  "node",
  "python",
  "script",
  "other",
  "kotlin-other",
  "non-exhaustive-when",
  "actions-input",
];

function categoryLabel(cat) {
  const labels = {
    "actions-runtime": "Actions runtime deprecation",
    deprecation: "Kotlin deprecation",
    gradle: "Gradle / AGP deprecation",
    "unsafe-call": "Unnecessary safe call / non-null assertion",
    tautology: "Always-true / tautology condition",
    "redundant-cast": "Redundant cast",
    unused: "Unused code",
    rust: "Rust warning",
    node: "Node / npm deprecation",
    python: "Python warning",
    script: "Shell / generic warning",
    other: "Other warning",
    "kotlin-other": "Other Kotlin warning",
    "non-exhaustive-when": "Non-exhaustive when",
    "actions-input": "Actions unexpected input",
  };
  return labels[cat] || cat;
}

function isGeneratedFile(file) {
  if (!file) return false;
  return file.endsWith("hesabyar_core.kt") || file.includes("/uniffi/");
}

/**
 * Render the markdown comment body.
 */
function renderComment({ findings, sha, repo, runsScanned, codeCompiled }) {
  const marker = "<!-- ci-warnings-report -->";
  const deduped = dedupe(findings);

  if (deduped.length === 0) {
    return `${marker}\n### ✅ No warnings in ${runsScanned} workflow runs for ${shortSha(sha)}`;
  }

  // Group by category
  const byCat = new Map();
  for (const f of deduped) {
    if (!byCat.has(f.category)) byCat.set(f.category, []);
    byCat.get(f.category).push(f);
  }

  let md = `${marker}\n### ⚠️ CI warnings report\n`;
  md += `**Commit:** ${shortSha(sha)}  \n`;
  md += `**Total:** ${findings.length} warnings (${deduped.length} unique) in ${runsScanned} workflows\n\n`;

  // Summary table
  md += "Category | Count\n";
  md += "---------|------\n";
  for (const cat of CATEGORY_ORDER) {
    if (byCat.has(cat)) {
      md += `${categoryLabel(cat)} | ${byCat.get(cat).reduce((sum, f) => sum + f.count, 0)}\n`;
    }
  }
  md += "\n";

  // Details per category
  for (const cat of CATEGORY_ORDER) {
    if (!byCat.has(cat)) continue;
    const items = byCat.get(cat);
    const total = items.reduce((sum, f) => sum + f.count, 0);
    md += `<details><summary>${categoryLabel(cat)} (${total})</summary>\n\n`;

    let displayed = 0;
    const MAX_ITEMS_PER_CAT = 50;
    for (const f of items) {
      if (displayed >= MAX_ITEMS_PER_CAT) {
        // Calculate the remaining weighted count to match the category total in header
        const remainingWeighted = items.slice(displayed).reduce((sum, item) => sum + item.count, 0);
        md += `…and ${remainingWeighted} more (see job logs)\n`;
        break;
      }
      displayed++;

      let loc = "";
      if (f.file) {
        const link = buildSourceLink(repo, sha, f.file, f.line || 1);
        const genTag = isGeneratedFile(f.file) ? " *(generated — fix in Rust/uniffi, not by hand)*" : "";
        loc = `[${escapeMarkdown(f.file)}${f.line ? `:${f.line}` : ""}](${link})${genTag}`;
      } else if (f.locations.size > 0) {
        // Link to the specific job corresponding to the location
        const firstLoc = f.locations.values().next().value;
        const jobUrl = f.jobUrls?.get(firstLoc);
        loc = jobUrl ? `[${escapeMarkdown(firstLoc)}](${jobUrl})` : codeSpan(firstLoc);
      }
      const countSuffix = f.count > 1 ? ` ×${f.count} · ${escapeMarkdown([...f.locations].join(", "))}` : "";
      md += `- ${loc}: ${codeSpan(f.message)}${countSuffix}\n`;
    }
    md += "\n</details>\n\n";
  }

  if (codeCompiled === false) {
    md += "> ℹ️ Kotlin compile was cached/up-to-date, so code warnings were not re-checked.\n\n";
  }

  // Truncate if over 60000 chars
  const MAX_COMMENT_LEN = 60000;
  if (md.length > MAX_COMMENT_LEN) {
    md = md.slice(0, MAX_COMMENT_LEN - 100) + "\n\n… (comment truncated, see job logs for full details)";
  }

  return md;
}

// ---------------------------------------------------------------------------
// GitHub API helper
// ---------------------------------------------------------------------------

const ALLOWED_API_PREFIXES = ["/repos/", "/repositories/"];
const ALLOWED_API_HOSTS = ["api.github.com"];

function buildApiUrl(path) {
  if (typeof path !== "string" || path.startsWith("//")) {
    throw new Error(`Invalid GitHub API path: ${path}`);
  }
  // Sanitize: normalize to absolute URL and strip any protocol/host prefix
  const cleaned = path.startsWith("https://api.github.com/")
    ? path.slice("https://api.github.com".length)
    : path;

  const normalized = new URL(cleaned, "https://api.github.com");
  if (normalized.origin !== "https://api.github.com") {
    throw new Error(`GitHub API path must stay on api.github.com: ${path}`);
  }

  // Enforce that all paths are restricted to /repos/ or /repositories/ prefixes
  // using the normalized pathname to prevent SSRF, path traversal, or non-repo endpoints.
  const isAllowedPath = ALLOWED_API_PREFIXES.find((p) => normalized.pathname.startsWith(p)) !== undefined;
  if (!isAllowedPath) {
    throw new Error(`GitHub API path must stay on api.github.com: ${path}`);
  }
  return normalized.href;
}

async function gh(path, opts = {}) {
  const token = process.env.GITHUB_TOKEN;
  if (!token) throw new Error("GITHUB_TOKEN not set");
  // Pin relative endpoint resolution to api.github.com to prevent SSRF if paths
  // contain unexpected host syntax. Note this validates the initial request URL;
  // standard redirect following is preserved for GitHub API responses.
  const url = buildApiUrl(path);
  const targetUrl = new URL(url);
  if (!ALLOWED_API_HOSTS.includes(targetUrl.hostname)) {
    throw new Error(`SSRF blocked: ${url}`);
  }
  // Send a JSON Content-Type whenever we are POSTing/PATCHing a body, so
  // GitHub parses it as JSON instead of rejecting it or guessing text/plain.
  const hasBody = Boolean(opts.body);

  const isWrite = opts.method && opts.method !== "GET" && opts.method !== "HEAD";

  let lastErr;
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const res = await fetch(targetUrl.href, {
        method: opts.method || "GET",
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: "application/vnd.github+json",
          "X-GitHub-Api-Version": "2022-11-28",
          ...(hasBody ? { "Content-Type": "application/json" } : {}),
          ...opts.headers,
        },
        body: hasBody ? JSON.stringify(opts.body) : undefined,
        redirect: "follow",
      });

      const rateLimited =
        res.status === 429 ||
        (res.status === 403 &&
          (res.headers.get("x-ratelimit-remaining") === "0" || res.headers.get("retry-after")));
      // Only retry 5xx errors for idempotent read requests (GET). For POST/PATCH,
      // retry only on rate-limiting so a 5xx does not duplicate a comment or issue.
      const shouldRetryStatus = rateLimited || (!isWrite && res.status >= 500 && res.status < 600);
      if (attempt < 2 && shouldRetryStatus) {
        const retryAfter = res.headers.get("retry-after");
        const wait = retryAfter ? parseInt(retryAfter, 10) * 1000 : 1000 * (attempt + 1);
        await new Promise((r) => setTimeout(r, wait));
        continue;
      }

      if (!res.ok) {
        const text = await res.text();
        throw new Error(`GitHub API ${res.status}: ${text}`);
      }

      if (opts.rawText) {
        const text = await res.text();
        return { data: text, link: null };
      }

      // Handle pagination via Link header
      const link = res.headers.get("link");
      const data = await res.json();
      if (!link || !link.includes('rel="next"')) {
        return { data, link: null };
      }
      // For simplicity, we only return first page; caller should handle pagination if needed
      return { data, link };
    } catch (e) {
      lastErr = e;
      if (attempt === 2 || /^GitHub API 4/.test(e.message) || (isWrite && /^GitHub API 5/.test(e.message))) throw e;
      await new Promise((r) => setTimeout(r, 1000 * (attempt + 1)));
    }
  }
  throw lastErr;
}

/**
 * Fetch all pages for a paginated endpoint.
 */
async function ghAllPages(path) {
  let all = [];
  let nextPath = path;
  while (nextPath) {
    const { data, link } = await gh(nextPath);
    // List endpoints such as /actions/runs and /runs/{id}/jobs wrap their
    // array in an object ({ total_count, workflow_runs }); unwrap it.
    const items = Array.isArray(data)
      ? data
      : Object.values(data).find((v) => Array.isArray(v)) || [];
    all = all.concat(items);
    // Parse Link header for next page
    let nextUrl = null;
    if (link) {
      const match = link.match(/<([^>]+)>;\s*rel="next"/);
      if (match) nextUrl = match[1];
    }
    if (nextUrl) {
      // Convert absolute URL to path
      const base = "https://api.github.com";
      nextPath = nextUrl.startsWith(base) ? nextUrl.slice(base.length) : nextUrl;
    } else {
      nextPath = null;
    }
  }
  return all;
}

// ---------------------------------------------------------------------------
// Main orchestration
// ---------------------------------------------------------------------------

/**
 * Detect whether Kotlin compilation actually ran based on Gradle log lines.
 * Returns true if a real compile occurred, false if only cached/up-to-date,
 * or preserves currentStatus if no compilation task line is present.
 */
function detectCodeCompiled(currentStatus, logText) {
  let status = currentStatus;
  const lines = logText.split(/\r?\n/);
  for (const l of lines) {
    if (l.includes(":app:compileDebugKotlin") || l.includes(":app:compileReleaseKotlin")) {
      const compiled = !l.includes("UP-TO-DATE") && !l.includes("FROM-CACHE");
      if (compiled) {
        return true; // a real compile overrides any prior cached signal
      }
      if (status === undefined) {
        status = false; // seen a cached compile, but none have compiled yet
      }
    }
  }
  return status;
}

/**
 * Collect warnings/deprecations from every selected workflow run: check-run
 * annotations plus the raw job logs. Also tracks whether any Android job
 * actually compiled Kotlin (vs. cache) so the report can note stale results.
 */
async function collectRunFindings(selectedRuns, repo) {
  const allFindings = [];
  let codeCompiled = undefined; // true/false/undefined

  for (const run of selectedRuns) {
    const jobs = await ghAllPages(`/repos/${repo}/actions/runs/${run.id}/jobs?filter=latest&per_page=100`);

    for (const job of jobs) {
      if (job.conclusion === null) continue;

      // Check-run annotations (job.id == check_run_id for workflow jobs)
      try {
        const annotations = await ghAllPages(`/repos/${repo}/check-runs/${job.id}/annotations?per_page=100`);
        for (const ann of annotations) {
          const cat = categoriseAnnotation(ann, repo);
          if (cat) {
            allFindings.push({
              ...cat,
              workflow: run.name,
              job: job.name,
              jobUrl: job.html_url,
            });
          }
        }
      } catch (e) {
        // Annotations may not exist for all jobs
        console.warn(`Failed to fetch annotations for job ${job.id}: ${e.message}`);
      }

      // Job logs
      try {
        const logPath = `/repos/${repo}/actions/jobs/${encodeURIComponent(job.id)}/logs`;
        const logRes = await gh(logPath, { rawText: true });
        const logText = logRes.data;
        const findings = parseLog(logText, repo, run.name, job.name);
        allFindings.push(...findings.map((f) => ({ ...f, jobUrl: job.html_url })));

        // Detect codeCompiled: look for compileDebugKotlin not UP-TO-DATE/FROM-CACHE.
        // If any Android job actually compiled, report true. Only report false
        // when an Android job existed but every compile was cached/UP-TO-DATE.
        if (run.name.includes("Android")) {
          codeCompiled = detectCodeCompiled(codeCompiled, logText);
        }
      } catch (e) {
        if (/GitHub API (?:404|410)/.test(e.message)) {
          // Log expired or not available
        } else {
          console.warn(`Failed to fetch logs for job ${job.id}: ${e.message}`);
        }
      }
    }
  }

  return { allFindings, codeCompiled };
}

/**
 * Resolve the PR number to report into.
 * Priority: explicit PR_NUMBER env, then a PR lookup by HEAD_SHA.
 * On the default branch (main) PR lookup is skipped and `null` is returned so
 * the report goes to the "CI warnings on main" issue instead.
 * Lookup fallbacks: an open PR whose head matches HEAD_BRANCH, then any open PR,
 * then the first returned PR. Lookup failures are logged and treated as no-PR.
 */
async function resolvePrNumber(repo, headSha, headBranch, prNumber) {
  if (prNumber) return prNumber;
  const isDefaultBranch = headBranch === "main" || process.env.GITHUB_REF_NAME === "main";
  if (isDefaultBranch) return null;
  try {
    const pulls = await ghAllPages(`/repos/${repo}/commits/${headSha}/pulls`);
    const matchingPull =
      pulls.find((p) => p.state === "open" && (!headBranch || p.head?.ref === headBranch)) ||
      pulls.find((p) => p.state === "open") ||
      pulls[0];
    if (matchingPull && matchingPull.number) {
      return matchingPull.number;
    }
  } catch (e) {
    console.warn(`Could not resolve PR number: ${e.message}`);
  }
  return null;
}

async function main() {
  const token = process.env.GITHUB_TOKEN;
  const repo = process.env.GITHUB_REPOSITORY;
  const headSha = process.env.HEAD_SHA;
  const initialPrNumber = process.env.PR_NUMBER ? parseInt(process.env.PR_NUMBER, 10) : null;
  const selfWorkflowName = process.env.SELF_WORKFLOW_NAME;

  if (!token || !repo || !headSha) {
    throw new Error("Missing required env: GITHUB_TOKEN, GITHUB_REPOSITORY, HEAD_SHA");
  }

  // 1. List workflow runs for HEAD_SHA
  const runs = await ghAllPages(`/repos/${repo}/actions/runs?head_sha=${headSha}&per_page=100`);

  // Keep latest attempt per workflow, skip self, skip incomplete
  const latestByWorkflow = new Map();
  for (const run of runs) {
    if (run.conclusion === null) continue; // Not completed
    if (selfWorkflowName && run.name === selfWorkflowName) continue;
    const existing = latestByWorkflow.get(run.workflow_id);
    if (!existing || run.run_attempt > existing.run_attempt) {
      latestByWorkflow.set(run.workflow_id, run);
    }
  }

  const selectedRuns = Array.from(latestByWorkflow.values());
  if (selectedRuns.length === 0) {
    console.log("No completed workflow runs found for this SHA");
    return;
  }

  // 2. Collect findings from all selected runs
  const { allFindings, codeCompiled } = await collectRunFindings(selectedRuns, repo);
  const runsScanned = selectedRuns.length;

  // 3. Resolve PR number (skips lookup on default branch)
  const prNumber = await resolvePrNumber(repo, headSha, process.env.HEAD_BRANCH || "", initialPrNumber);

  // 4. Render and post
  const commentBody = renderComment({
    findings: allFindings,
    sha: headSha,
    repo,
    runsScanned,
    codeCompiled,
  });

  if (prNumber) {
    // Find existing comment by marker
    const comments = await ghAllPages(`/repos/${repo}/issues/${prNumber}/comments?per_page=100`);
    const existing = comments.find(
      (c) => c.user?.login === "github-actions[bot]" && (c.body || "").includes("<!-- ci-warnings-report -->"),
    );
    if (existing) {
      await gh(`/repos/${repo}/issues/comments/${existing.id}`, {
        method: "PATCH",
        body: { body: commentBody },
      });
      console.log(`Updated existing comment #${existing.id}`);
    } else {
      await gh(`/repos/${repo}/issues/${prNumber}/comments`, {
        method: "POST",
        body: { body: commentBody },
      });
      console.log("Created new comment");
    }
  } else if (process.env.HEAD_BRANCH !== "main") {
    // Push to a feature branch with no PR yet: nothing to report into.
    console.log(`No PR for ${headSha} on ${process.env.HEAD_BRANCH}; skipping`);
  } else {
    // Find or create issue "CI warnings on main"
    let issue = null;
    const issues = await ghAllPages(`/repos/${repo}/issues?state=open&labels=ci&per_page=100`);
    issue = issues.find((i) => i.title === "CI warnings on main");
    if (issue) {
      await gh(`/repos/${repo}/issues/${issue.number}`, {
        method: "PATCH",
        body: { body: commentBody },
      });
      console.log(`Updated issue #${issue.number}`);
    } else {
      const created = await gh(`/repos/${repo}/issues`, {
        method: "POST",
        body: { title: "CI warnings on main", body: commentBody, labels: ["ci"] },
      });
      console.log(`Created issue #${created.data.number}`);
    }
  }
}

// Export for testing
export {
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
  codeSpan,
  buildSourceLink,
  buildApiUrl,
  detectCodeCompiled,
  isGeneratedFile,
  shortSha,
  collectRunFindings,
  resolvePrNumber,
  gh,
  ghAllPages,
  main,
};

// Run main only when executed directly
if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((e) => {
    console.error(e);
    process.exit(1);
  });
}