// CI Warnings Report — parse GitHub Actions logs and annotations into a single PR comment.
// Pure ESM, Node 20+ built-ins only. Export pure functions for testability.
import { createRequire } from "node:module";
const require_ = createRequire(import.meta.url);

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
  // Remove ANSI escape sequences
  s = s.replace(/\x1b\[[0-9;]*m/g, "");
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
function makeRepoRelative(path, repo) {
  // Runner checkouts live at /home/runner/work/<name>/<name>/; <name> is the
  // repo name (not owner/repo), so match any two segments rather than `repo`.
  return path.replace(/^(?:file:\/\/)?\/?home\/runner\/work\/[^/]+\/[^/]+\//, "");
}

/**
 * Escape characters that break markdown tables or links.
 */
function escapeMarkdown(s) {
  return s.replace(/\|/g, "\\|").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

/**
 * Render text as an inline code span that survives backticks in the text
 * (Rust/Kotlin messages quote identifiers with them). Code spans need no
 * HTML escaping; long messages are cut to keep the comment scannable.
 */
function codeSpan(s, max = 300) {
  const t = normalizeMessage(s).slice(0, max) + (s.length > max ? "…" : "");
  return t.includes("`") ? `\`\` ${t} \`\`` : `\`${t}\``;
}

/**
 * Build a GitHub source link for a file at a specific line on a given SHA.
 */
function buildSourceLink(repo, sha, file, line) {
  return `https://github.com/${repo}/blob/${sha}/${file}#L${line}`;
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
  const raw = stripLogPrefix(line);
  // `##[warning]` lines are also published as check-run annotations, which
  // carry file/line; `##[group]Run ...` and `+ cmd` are command echoes whose
  // text (e.g. `--warning-mode all`) is not a warning.
  if (/^##\[/.test(raw) || /^\+ /.test(raw) || /--warning-mode/.test(raw)) return null;
  const clean = stripLogPrefix(line);
  if (!clean) return null;

  // Kotlin compiler warnings: w: file:///home/.../Foo.kt:142:23 Unnecessary safe call ...
  // Also non-file:// form: w: /home/.../Foo.kt:142:23 ...
  const kotlinMatch = clean.match(
    /^w:\s+(?:file:\/\/\/)?([^\s:]+):(\d+):\d+\s+(.+)$/
  );
  if (kotlinMatch) {
    const [, file, lineStr, message] = kotlinMatch;
    const relFile = makeRepoRelative(file, repo);
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
  if (clean.match(/^WARNING:/) && clean.match(/\b(AGP|Android Gradle|android\.\w+|Gradle)\b/)) {
    return { category: "gradle", file: undefined, line: undefined, message: clean, workflow, job };
  }

  // Generic workflow command warnings echoed in logs: ::warning file=...,line=...::message
  const workflowCmdMatch = clean.match(/^::warning\s+(?:file=([^,]+),)?(?:line=(\d+),?)?(?:col=(\d+),?)?::(.+)$/);
  if (workflowCmdMatch) {
    const [, file, lineStr, , message] = workflowCmdMatch;
    return {
      category: "script",
      file: file ? makeRepoRelative(file, repo) : undefined,
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

  // Lines starting with warning:/Warning:/WARN
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
  // Heuristic: contains "warning" or "deprecat" (case-insensitive) and not an error
  if (
    clean.match(/\b(warning|warn)\b\s*[:\]!]|\bdeprecat(ed|ion)\b/i) &&
    !clean.match(/^(error|Error|ERROR)\b/)
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
      // Look ahead for the location line
      if (i + 1 < lines.length) {
        const nextLine = stripLogPrefix(lines[i + 1]);
        const locMatch = nextLine.match(/^\s*-->\s*([^:]+):(\d+):\d+/);
        if (locMatch) {
          const [, file, lineStr] = locMatch;
          const relFile = makeRepoRelative(file, repo);
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
      // No location line found, treat as rust without location
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
  if (level === "failure") return null; // Exclude failures

  const message = annotation.message || "";
  const path = annotation.path;
  const line = annotation.start_line;

  // Actions runtime deprecations
  if (
    message.includes("Node.js 20 actions are deprecated") ||
    message.includes("actions are deprecated") ||
    message.includes("The `set-output` command is deprecated") ||
    message.includes("The `set-env` command is deprecated") ||
    message.includes("command is deprecated") ||
    message.includes("deprecated") && message.includes("action")
  ) {
    return { category: "actions-runtime", file: undefined, line: undefined, message };
  }

  // Unexpected input warnings
  if (message.includes("Unexpected input")) {
    return { category: "actions-input", file: undefined, line: undefined, message };
  }

  // If annotation points to a source file, categorise by file extension/content
  if (path) {
    const relPath = makeRepoRelative(path, repo);
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
  // `::warning::`; only the former is an actions-runtime problem.
  const category = /deprecat|node\.js|runner|will be removed/i.test(message) ? "actions-runtime" : "script";
  return { category, file: undefined, line: undefined, message };
}

// ---------------------------------------------------------------------------
// Deduplication
// ---------------------------------------------------------------------------

/**
 * Deduplicate findings by category+file+line+normalised message.
 * Returns array of { category, file, line, message, count, locations: Set<"workflow/job"> }
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
        jobUrl: f.jobUrl,
      });
    }
    const entry = map.get(key);
    entry.count++;
    entry.locations.add(`${f.workflow}/${f.job}`);
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
  return file && file.endsWith("hesabyar_core.kt");
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
        const remaining = items.length - displayed;
        md += `…and ${remaining} more (see job logs)\n`;
        break;
      }
      displayed++;

      let loc = "";
      if (f.file) {
        const link = buildSourceLink(repo, sha, f.file, f.line || 1);
        const genTag = isGeneratedFile(f.file) ? " *(generated — fix in Rust/uniffi, not by hand)*" : "";
        loc = `[${escapeMarkdown(f.file)}${f.line ? `:${f.line}` : ""}](${link})${genTag}`;
      } else if (f.locations.size > 0) {
        // Link to first job
        const firstLoc = f.locations.values().next().value;
        loc = f.jobUrl ? `[${escapeMarkdown(firstLoc)}](${f.jobUrl})` : codeSpan(firstLoc);
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

async function gh(path, opts = {}) {
  const token = process.env.GITHUB_TOKEN;
  if (!token) throw new Error("GITHUB_TOKEN not set");
  const base = "https://api.github.com";
  const url = `${base}${path}`;

  let lastErr;
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const res = await fetch(url, {
        method: opts.method || "GET",
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: "application/vnd.github+json",
          "X-GitHub-Api-Version": "2022-11-28",
          ...opts.headers,
        },
        body: opts.body ? JSON.stringify(opts.body) : undefined,
      });

      const rateLimited =
        res.status === 429 ||
        (res.status === 403 &&
          (res.headers.get("x-ratelimit-remaining") === "0" || res.headers.get("retry-after")));
      if (attempt < 2 && (rateLimited || (res.status >= 500 && res.status < 600))) {
        const retryAfter = res.headers.get("retry-after");
        const wait = retryAfter ? parseInt(retryAfter, 10) * 1000 : 1000 * (attempt + 1);
        await new Promise((r) => setTimeout(r, wait));
        continue;
      }

      if (!res.ok) {
        const text = await res.text();
        throw new Error(`GitHub API ${res.status}: ${text}`);
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
      if (attempt === 2 || /^GitHub API 4/.test(e.message)) throw e;
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

async function main() {
  const token = process.env.GITHUB_TOKEN;
  const repo = process.env.GITHUB_REPOSITORY;
  const headSha = process.env.HEAD_SHA;
  let prNumber = process.env.PR_NUMBER ? parseInt(process.env.PR_NUMBER, 10) : null;
  const selfWorkflowName = process.env.SELF_WORKFLOW_NAME;
  const eventType = process.env.EVENT; // triggering run's event: pull_request, push, schedule...

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

  let allFindings = [];
  let codeCompiled = undefined; // true/false/undefined
  const runsScanned = selectedRuns.length;

  // 2. For each run, fetch jobs, annotations, logs
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
        const logRes = await fetch(`https://api.github.com/repos/${repo}/actions/jobs/${job.id}/logs`, {
          headers: {
            Authorization: `Bearer ${token}`,
            Accept: "application/vnd.github+json",
          },
          redirect: "follow",
        });
        if (logRes.ok) {
          const logText = await logRes.text();
          const findings = parseLog(logText, repo, run.name, job.name);
          allFindings.push(...findings.map((f) => ({ ...f, jobUrl: job.html_url })));

          // Detect codeCompiled: look for compileDebugKotlin not UP-TO-DATE/FROM-CACHE
          if (codeCompiled === undefined && run.name.includes("Android")) {
            const lines = logText.split(/\r?\n/);
            for (const l of lines) {
              if (l.includes(":app:compileDebugKotlin") || l.includes(":app:compileReleaseKotlin")) {
                if (!l.includes("UP-TO-DATE") && !l.includes("FROM-CACHE")) {
                  codeCompiled = true;
                } else {
                  codeCompiled = false;
                }
                break;
              }
            }
          }
        } else if (logRes.status === 404 || logRes.status === 410) {
          // Log expired or not available
        } else {
          console.warn(`Failed to fetch logs for job ${job.id}: ${logRes.status}`);
        }
      } catch (e) {
        console.warn(`Error fetching logs for job ${job.id}: ${e.message}`);
      }
    }
  }

  // 3. Resolve PR number if not provided (fork PRs)
  if (!prNumber && eventType === "pull_request") {
    // Try to get from workflow_run event payload (would need to be passed differently)
    // Fallback: query commits/{sha}/pulls
    try {
      const pulls = await ghAllPages(`/repos/${repo}/commits/${headSha}/pulls`);
      if (pulls.length > 0) {
        prNumber = pulls[0].number;
      }
    } catch (e) {
      console.warn(`Could not resolve PR number: ${e.message}`);
    }
  }

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
  buildSourceLink,
  shortSha,
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