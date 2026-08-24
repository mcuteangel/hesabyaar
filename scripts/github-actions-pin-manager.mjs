#!/usr/bin/env node
// GitHub Actions pin manager for Hesabyar.
//
// Responsibilities:
//   1. Parse every `uses:` reference under .github/.
//   2. Classify each reference (third-party, GitHub-owned, reusable, local).
//   3. Check each immutable SHA pin against the latest stable release.
//   4. Check GitHub Security Advisories for the pinned repository.
//   5. Propose verified updates through one maintenance pull request.
//
// Modes:
//   (no flags)              Offline audit. Prints a compliance report.
//   --check                 Same as the offline audit. Kept for scripts.
//   --apply --mode=weekly   Propose stable updates. One batch PR.
//   --apply --mode=security Propose advisory-driven updates. Separate PR.
//   --json                  Machine-readable audit output.
//
// The tool never merges anything. It never executes code from a PR.
// It talks only to the GitHub REST API and edits workflow text lines.

import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const SHA40_RE = /^[0-9a-f]{40}$/i;
const STABLE_TAG_RE = /^v?(\d+)\.(\d+)\.(\d+)$/;
// Accepts full `vX.Y.Z` and partial `vX.Y` / `vX` baselines. The compliance
// audit and every replacement always use the full triple.
const VERSION_IN_COMMENT_RE = /v?\d+(?:\.\d+){0,2}(?:[+-][\w.]+)?/;
const PIN_MANAGER_BRANCHES = {
  weekly: 'ci/pin-updates',
  security: 'ci/pin-security',
};
const PR_TITLES = {
  weekly: 'chore(ci): update pinned GitHub Actions',
  security: 'security(ci): update vulnerable GitHub Actions',
};

// ---------------------------------------------------------------------------
// File discovery
// ---------------------------------------------------------------------------

export function findYamlFiles(dir, root = dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      out.push(...findYamlFiles(full, root));
    } else if (/\.ya?ml$/.test(entry)) {
      out.push(full);
    }
  }
  return out.sort();
}

export function workflowFilePaths(repoRoot) {
  const dirs = [
    join(repoRoot, '.github', 'workflows'),
    join(repoRoot, '.github', 'actions'),
  ];
  const files = [];
  for (const dir of dirs) {
    try {
      statSync(dir);
    } catch {
      continue;
    }
    files.push(...findYamlFiles(dir));
  }
  return files;
}

// ---------------------------------------------------------------------------
// Line parser
// ---------------------------------------------------------------------------

const USES_KEY_RE = /^(\s*)(-\s+)?(uses\s*:\s*)/;

// Split one YAML line into: key part, quoted-or-bare value, inline comment.
export function parseUsesLine(line) {
  const m = line.match(USES_KEY_RE);
  if (!m) return null;
  let rest = line.slice(m[0].length);
  let quote = '';
  let value = '';
  let tail = '';
  if (rest.startsWith('"') || rest.startsWith("'")) {
    quote = rest[0];
    const end = rest.indexOf(quote, 1);
    if (end === -1) return null;
    value = rest.slice(1, end);
    tail = rest.slice(end + 1);
  } else {
    const hashAt = rest.search(/\s#/);
    if (hashAt === -1) {
      value = rest.trimEnd();
      tail = '';
    } else {
      value = rest.slice(0, hashAt).trimEnd();
      tail = rest.slice(hashAt);
    }
  }
  if (tail && !/^\s*(#.*)?$/.test(tail)) return null;
  return {
    keyPart: m[0],
    dash: Boolean(m[2]),
    quote,
    value,
    comment: tail.trim(),
    raw: line,
  };
}

export function renderUsesLine(parsed, newValue, newComment) {
  const comment = newComment !== undefined ? newComment : parsed.comment;
  const sep = comment ? ' ' : '';
  const body = parsed.quote
    ? `${parsed.quote}${newValue}${parsed.quote}`
    : newValue;
  return `${parsed.keyPart}${body}${comment ? sep + comment : ''}`;
}

// ---------------------------------------------------------------------------
// Reference classification
// ---------------------------------------------------------------------------

export function parseTarget(value) {
  if (value.startsWith('./') || value.startsWith('.\\')) {
    return { kind: 'LOCAL', value };
  }
  if (value.startsWith('docker://')) {
    return { kind: 'DOCKER', value };
  }
  const at = value.lastIndexOf('@');
  if (at <= 0) return { kind: 'MALFORMED', value };
  const slug = value.slice(0, at);
  const ref = value.slice(at + 1);
  const parts = slug.split('/');
  if (parts.length < 2 || !parts[0] || !parts[1]) {
    return { kind: 'MALFORMED', value };
  }
  const subpath = parts.slice(2).join('/');
  const kind =
    subpath.startsWith('.github/workflows/') && subpath.endsWith('.yml')
      ? 'REUSABLE_WORKFLOW'
      : 'ACTION';
  const ownership = parts[0] === 'actions' ? 'GITHUB_OWNED' : 'THIRD_PARTY';
  return { kind, ownership, owner: parts[0], repo: parts[1], subpath, ref, slug };
}

export function refShape(ref) {
  if (SHA40_RE.test(ref)) return 'SHA';
  if (STABLE_TAG_RE.test(ref)) return 'STABLE_TAG';
  if (/^v?\d+(\.\d+)*$/.test(ref)) return 'FLOATING_TAG';
  if (/^(main|master|latest)$/.test(ref)) return 'BRANCH';
  return 'OTHER';
}

// Extract the version token from a same-line comment such as `# v7.1.0`.
export function versionFromComment(comment) {
  const m = comment.match(VERSION_IN_COMMENT_RE);
  return m ? m[0].replace(/^v/, '') : null;
}

// ---------------------------------------------------------------------------
// Version helpers
// ---------------------------------------------------------------------------

export function parseVersion(tag) {
  const m = String(tag).trim().match(STABLE_TAG_RE);
  if (!m) return null;
  return [Number(m[1]), Number(m[2]), Number(m[3])];
}

export function compareVersions(a, b) {
  const va = parseVersion(a);
  const vb = parseVersion(b);
  if (!va || !vb) return NaN;
  for (let i = 0; i < 3; i += 1) {
    if (va[i] !== vb[i]) return va[i] - vb[i];
  }
  return 0;
}

export function isStableRelease(release) {
  if (release.draft || release.prerelease) return false;
  return STABLE_TAG_RE.test(String(release.tag_name || '').trim());
}

export function majorOf(version) {
  const v = parseVersion(version);
  return v ? v[0] : null;
}

// Pad a partial version such as `4` or `4.2` to a full `X.Y.Z` triple so a
// floating tag like `@v4` still has a comparable baseline.
export function normalizeVersion(version) {
  const parts = String(version).replace(/^v/, '').split('.');
  while (parts.length < 3) parts.push('0');
  return parts.slice(0, 3).join('.');
}

// ---------------------------------------------------------------------------
// GitHub API client
// ---------------------------------------------------------------------------

export function createApi({ fetchImpl = globalThis.fetch, token, repo }) {
  const baseUrl = 'https://api.github.com';
  async function call(path, options = {}) {
    const headers = {
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
      ...(options.headers || {}),
    };
    if (token) headers.Authorization = `Bearer ${token}`;
    const res = await fetchImpl(`${baseUrl}${path}`, { ...options, headers });
    if (!res.ok) {
      const err = new Error(`GitHub API ${res.status} on ${path}`);
      err.status = res.status;
      throw err;
    }
    if (res.status === 204) return null;
    return res.json();
  }
  const repoPath = () => `/repos/${repo}`;
  return {
    repo,
    token,
    fetchImpl,
    call,
    getReleases: () => call(`${repoPath()}/releases?per_page=100`),
    getTags: () => call(`${repoPath()}/tags?per_page=100`),
    getTagRef: (tag) => call(`${repoPath()}/git/ref/tags/${encodeURIComponent(tag)}`),
    getTagObject: (sha) => call(`${repoPath()}/git/tags/${sha}`),
    getCommit: (sha) => call(`${repoPath()}/commits/${sha}`),
    getGlobalAdvisories: () =>
      call(`https://api.github.com/advisories?affects=${repo}`.replace(baseUrl, '')),
    getRepoAdvisories: () => call(`${repoPath()}/security-advisories?per_page=100`),
    getOpenPullRequest: (head) =>
      call(`${repoPath()}/pulls?head=${encodeURIComponent(`${repo.split('/')[0]}:${head}`)}&state=open&per_page=1`),
    createPullRequest: (body) => call(`${repoPath()}/pulls`, { method: 'POST', body: JSON.stringify(body) }),
    getBranch: (branch) => call(`${repoPath()}/git/ref/heads/${branch}`).catch(() => null),
    getDefaultBranchSha: async function () {
      const info = await call(repoPath());
      return this.getBranch(info.default_branch);
    },
    createBlob: (content) =>
      call(`${repoPath()}/git/blobs`, { method: 'POST', body: JSON.stringify({ content, encoding: 'utf-8' }) }),
    createTree: (baseTree, tree) =>
      call(`${repoPath()}/git/trees`, {
        method: 'POST',
        body: JSON.stringify({ base_tree: baseTree, tree }),
      }),
    createCommit: (message, treeSha, parents) =>
      call(`${repoPath()}/git/commits`, {
        method: 'POST',
        body: JSON.stringify({ message, tree: treeSha, parents }),
      }),
    createRef: (ref, sha) =>
      call(`${repoPath()}/git/refs`, { method: 'POST', body: JSON.stringify({ ref, sha }) }),
    updateRef: (ref, sha) =>
      call(`${repoPath()}/git/refs/${ref}`, {
        method: 'PATCH',
        body: JSON.stringify({ sha, force: true }),
      }),
  };
}

// ---------------------------------------------------------------------------
// Release + SHA resolution with immutability checks
// ---------------------------------------------------------------------------

// Stable releases for a repository, newest first. Falls back to tags when a
// repository publishes no GitHub Releases. Prereleases never pass.
export async function listStableVersions(api) {
  let releases = [];
  try {
    releases = (await api.getReleases()) || [];
  } catch (err) {
    if (err.status === 404) return { source: 'unknown', versions: [] };
    throw err;
  }
  const versions = releases
    .filter(isStableRelease)
    .map((r) => ({ tag: r.tag_name.trim(), version: r.tag_name.trim().replace(/^v/, '') }))
    .sort((a, b) => compareVersions(b.version, a.version));
  if (versions.length > 0) return { source: 'releases', versions };
  const tags = ((await api.getTags()) || [])
    .map((t) => t.name)
    .filter((n) => STABLE_TAG_RE.test(n))
    .sort((a, b) => compareVersions(b.replace(/^v/, ''), a.replace(/^v/, '')));
  return { source: 'tags', versions: tags.map((t) => ({ tag: t, version: t.replace(/^v/, '') })) };
}

// Resolve a tag to a commit SHA. Resolves twice. A mismatch means the tag
// moved during the process, so the caller must abort the candidate.
export async function resolveTagToCommitSha(api, tag) {
  const first = await resolveOnce(api, tag);
  const second = await resolveOnce(api, tag);
  if (first !== second) {
    throw Object.assign(new Error(`tag ${tag} moved during resolution`), { code: 'TAG_MOVED' });
  }
  if (!SHA40_RE.test(first)) {
    throw Object.assign(new Error(`resolved value for ${tag} is not a 40-hex SHA`), {
      code: 'INVALID_SHA',
    });
  }
  return first;
}

async function resolveOnce(api, tag) {
  const ref = await api.getTagRef(tag);
  if (!ref || !ref.object || !SHA40_RE.test(ref.object.sha || '')) {
    throw Object.assign(new Error(`cannot resolve tag ${tag}`), { code: 'INVALID_SHA' });
  }
  if (ref.object.type === 'commit') return ref.object.sha;
  const obj = await api.getTagObject(ref.object.sha);
  const commitSha = obj && obj.object && obj.object.sha;
  if (!commitSha || obj.object.type !== 'commit' || !SHA40_RE.test(commitSha)) {
    throw Object.assign(new Error(`annotated tag ${tag} does not deref to a commit`), {
      code: 'INVALID_SHA',
    });
  }
  return commitSha;
}

// ---------------------------------------------------------------------------
// Scan: turn workflow files into classified, checked occurrences
// ---------------------------------------------------------------------------

export function scanFiles(paths) {
  const files = [];
  for (const path of paths) {
    const text = readFileSync(path, 'utf8');
    const lines = text.split('\n');
    const occurrences = [];
    lines.forEach((line, index) => {
      const parsed = parseUsesLine(line);
      if (!parsed) return;
      const target = parseTarget(parsed.value);
      if (target.kind === 'MALFORMED') {
        occurrences.push({ line: index, parsed, target, problem: 'malformed uses reference' });
        return;
      }
      if (target.kind === 'LOCAL' || target.kind === 'DOCKER') {
        occurrences.push({ line: index, parsed, target, skip: true });
        return;
      }
      const shape = refShape(target.ref);
      const commentVersion = versionFromComment(parsed.comment);
      occurrences.push({
        line: index,
        parsed,
        target,
        shape,
        sha: shape === 'SHA' ? target.ref : null,
        commentVersion,
        pinned: shape === 'SHA',
        commentMissing: shape === 'SHA' && !commentVersion,
      });
    });
    files.push({ path, text, lines, occurrences });
  }
  return files;
}

// ---------------------------------------------------------------------------
// Update planning
// ---------------------------------------------------------------------------

function occurrenceKey(f) {
  return `${f.target.slug}@${f.target.ref}`;
}

async function repoFacts(api, slug, cache) {
  if (!cache.has(slug)) {
    const [owner, repo] = slug.split('/');
    // Propagate the injected transport so scans stay offline in tests.
    const subApi = createApi({ fetchImpl: api.fetchImpl, token: api.token, repo: slug });
    cache.set(slug, {
      api: subApi,
      owner,
      repo,
      stablePromise: listStableVersions(subApi),
    });
  }
  return cache.get(slug);
}

function cacheToken(api) {
  // Reuse whatever token context the caller gave; createApi only embeds it.
  return api.token;
}

// Pick the newest stable release that satisfies the constraint.
function pickTarget(stable, constraint) {
  const eligible = stable.filter(
    (v) => (!constraint.minVersion || compareVersions(v.version, constraint.minVersion) >= 0) &&
      (!constraint.maxMajor || majorOf(v.version) === constraint.maxMajor),
  );
  return eligible.length > 0 ? eligible[0] : null;
}

export async function planUpdates(files, api, mode) {
  const factsCache = new Map();
  const plans = { updates: [], reportOnly: [], needsHuman: [], errors: [] };

  for (const file of files) {
    for (const occ of file.occurrences) {
      if (occ.skip) continue;
      if (occ.target.kind === 'MALFORMED') {
        plans.needsHuman.push(fileRow(file, occ, 'malformed uses reference'));
        continue;
      }
      let facts;
      try {
        facts = await repoFacts(api, occ.target.slug, factsCache);
      } catch (err) {
        plans.errors.push(row(file, occ, `API error: ${err.message}`, 'UNKNOWN'));
        continue;
      }
      let stable;
      try {
        stable = await facts.stablePromise;
      } catch (err) {
        plans.errors.push(row(file, occ, `release lookup failed: ${err.message}`, 'UNKNOWN'));
        continue;
      }
      if (stable.source === 'unknown') {
        plans.needsHuman.push(fileRow(file, occ, 'no releases or tags found'));
        continue;
      }

      // Floating tag or branch: no trustworthy baseline. Branches are
      // reported only. A floating stable tag (checkout@v4) is converted to
      // an immutable pin of the newest release in the SAME major.
      if (occ.shape === 'BRANCH' || occ.shape === 'OTHER') {
        plans.needsHuman.push(fileRow(file, occ, `floating ref @${occ.target.ref}; human must pick a version`));
        continue;
      }

      const fromRef = occ.shape === 'STABLE_TAG' || occ.shape === 'FLOATING_TAG';
      let currentVersion = null;
      if (fromRef) {
        currentVersion = normalizeVersion(occ.target.ref.replace(/^v/, ''));
      } else if (occ.commentVersion) {
        // A partial comment such as `# v5` is a usable major-level baseline.
        // The replacement always writes a full vX.Y.Z tag.
        currentVersion = normalizeVersion(occ.commentVersion);
      }
      if (!currentVersion || !parseVersion(currentVersion)) {
        plans.needsHuman.push(fileRow(file, occ, 'SHA pin without a usable version comment; human must confirm the current version'));
        continue;
      }

      // Advisory-driven security planning.
      if (mode === 'security') {
        await planSecurityFor(plans, file, occ, facts, currentVersion, cacheToken(api));
        continue;
      }

      const latest = stable.versions[0];
      if (!latest || compareVersions(latest.version, currentVersion) <= 0) continue;

      const sameMajor = pickTarget(stable.versions, { maxMajor: majorOf(currentVersion) });
      const majorJump = compareVersions(latest.version, currentVersion) > 0 &&
        majorOf(latest.version) !== majorOf(currentVersion);

      if (sameMajor && compareVersions(sameMajor.version, currentVersion) > 0) {
        await pushCandidate(plans.updates, file, occ, facts, sameMajor, `stable update v${currentVersion} -> ${sameMajor.tag}`);
      } else if (majorJump) {
        plans.reportOnly.push(row(file, occ, `major update available: v${currentVersion} -> ${latest.tag} (not applied; needs human decision)`));
      }
    }
  }
  return dedupePlans(plans);
}

async function planSecurityFor(plans, file, occ, facts, currentVersion, token) {
  const advisories = await loadAdvisories(facts.api, facts.owner, facts.repo);
  if (!advisories.ok) {
    plans.errors.push(row(file, occ, 'advisory lookup failed; classification UNKNOWN'));
    return;
  }
  if (advisories.list.length === 0) return;
  if (occ.shape !== 'SHA') {
    plans.needsHuman.push(fileRow(file, occ, 'security advisory exists but the pin is not an immutable SHA'));
    return;
  }
  const commit = await facts.api.getCommit(occ.target.ref);
  const pinnedAt = commit && commit.commit && commit.commit.committer && commit.commit.committer.date;
  const relevant = advisories.list.filter((adv) => {
    const t = adv.published_at || adv.github_reviewed_at;
    return t && (!pinnedAt || new Date(t) > new Date(pinnedAt));
  });
  if (relevant.length === 0) return;
  const stable = await facts.stablePromise;
  const target = pickTarget(stable.versions, { minVersion: currentVersion });
  if (!target || compareVersions(target.version, currentVersion) <= 0) {
    plans.needsHuman.push(fileRow(file, occ, 'security advisory exists but no newer stable release was found'));
    return;
  }
  await pushCandidate(plans.updates, file, occ, facts, target, 'SECURITY UPDATE', {
    advisories: relevant.map((a) => ({ ghsa: a.ghsa_id, cve: a.cve_id, summary: a.summary, url: a.html_url })),
  });
}

async function loadAdvisories(api, owner, repo) {
  try {
    const repoList = (await api.getRepoAdvisories()) || [];
    let globalList = [];
    try {
      globalList = (await api.getGlobalAdvisories()) || [];
    } catch {
      // The global endpoint can be unavailable for some repos. Repo-published
      // advisories remain authoritative enough on their own.
    }
    const merged = new Map();
    for (const adv of [...repoList, ...globalList]) {
      if (adv && adv.ghsa_id) merged.set(adv.ghsa_id, adv);
    }
    return { ok: true, list: [...merged.values()] };
  } catch (err) {
    return { ok: false, error: err };
  }
}

async function pushCandidate(list, file, occ, facts, targetVersion, reason, extra = {}) {
  let sha;
  try {
    sha = await resolveTagToCommitSha(facts.api, targetVersion.tag);
  } catch (err) {
    list.push({
      aborted: true,
      file: file.path,
      line: occ.line,
      action: occ.target.slug,
      reason: `${reason} ABORTED: ${err.message}`,
    });
    return;
  }
  list.push({
    file: file.path,
    line: occ.line,
    action: occ.target.slug,
    ownership: occ.target.ownership,
    kind: occ.target.kind,
    currentValue: occ.shape === 'SHA' ? occ.commentVersion : `@${occ.target.ref}`,
    currentRef: occ.parsed.value,
    targetTag: targetVersion.tag,
    targetVersion: targetVersion.version,
    targetSha: sha,
    reason,
    ...extra,
  });
}

function fileRow(file, occ, note) {
  return row(file, occ, note);
}

function row(file, occ, note, status = 'NEEDS_HUMAN') {
  return {
    status,
    file: file.path,
    line: occ.line,
    action: occ.target.slug || occ.target.value,
    ref: occ.target.ref,
    note,
  };
}

function dedupePlans(plans) {
  for (const key of ['updates', 'reportOnly', 'needsHuman', 'errors']) {
    const seen = new Set();
    plans[key] = plans[key].filter((item) => {
      const id = `${item.file}:${item.line}:${item.action}:${item.note || item.reason || ''}`;
      if (seen.has(id)) return false;
      seen.add(id);
      return true;
    });
  }
  return plans;
}

// ---------------------------------------------------------------------------
// Apply + validate
// ---------------------------------------------------------------------------

export function applyUpdates(files, updates) {
  const byFile = new Map();
  for (const upd of updates) {
    if (!byFile.has(upd.file)) byFile.set(upd.file, []);
    byFile.get(upd.file).push(upd);
  }
  const changed = {};
  for (const [path, fileUpdates] of byFile) {
    const file = files.find((f) => f.path === path);
    if (!file) throw new Error(`unknown file in plan: ${path}`);
    const newLines = [...file.lines];
    for (const upd of fileUpdates) {
      const occ = file.occurrences.find((o) => o.line === upd.line);
      if (!occ || !occ.parsed) throw new Error(`lost occurrence ${path}:${upd.line}`);
      const newValue = occ.parsed.value.replace(/@[^@]*$/, `@${upd.targetSha}`);
      const newComment = `# ${upd.targetTag}`;
      newLines[upd.line] = renderUsesLine(occ.parsed, newValue, newComment);
    }
    changed[path] = newLines.join('\n');
  }
  return changed;
}

// Validation gate. A generated file must differ from the original only on
// the planned lines, and every changed line must stay an immutable SHA pin
// with a synchronized version comment.
export function validateChanges(files, changed, updates) {
  const problems = [];
  for (const [path, newText] of Object.entries(changed)) {
    const file = files.find((f) => f.path === path);
    const oldLines = file.lines;
    const newLines = newText.split('\n');
    if (oldLines.length !== newLines.length) {
      problems.push(`${path}: line count changed`);
      continue;
    }
    const plannedLines = new Set(updates.filter((u) => u.file === path).map((u) => u.line));
    for (let i = 0; i < oldLines.length; i += 1) {
      if (oldLines[i] === newLines[i]) continue;
      if (!plannedLines.has(i)) {
        problems.push(`${path}:${i + 1}: unplanned change`);
        continue;
      }
      const parsed = parseUsesLine(newLines[i]);
      if (!parsed) {
        problems.push(`${path}:${i + 1}: changed line no longer parses as a uses reference`);
        continue;
      }
      const target = parseTarget(parsed.value);
      if (!SHA40_RE.test(target.ref || '')) {
        problems.push(`${path}:${i + 1}: new ref is not a 40-hex SHA`);
      }
      const version = versionFromComment(parsed.comment);
      if (!version) {
        problems.push(`${path}:${i + 1}: missing version comment`);
      }
      const upd = updates.find((u) => u.file === path && u.line === i);
      if (upd && version && compareVersions(version, upd.targetVersion) !== 0) {
        problems.push(`${path}:${i + 1}: version comment not synchronized with target ${upd.targetTag}`);
      }
    }
  }
  return problems;
}

// ---------------------------------------------------------------------------
// Pull request lifecycle
// ---------------------------------------------------------------------------

export async function ensurePullRequest({ api, mode, changed, plan }) {
  const branch = PIN_MANAGER_BRANCHES[mode];
  const title = PR_TITLES[mode];
  const body = buildPullRequestBody(mode, plan);

  const head = await api.getDefaultBranchSha();
  const baseSha = head.object.sha;
  const baseTree = (await api.call(`/repos/${api.repo}/git/commits/${baseSha}`)).tree.sha;

  const entries = [];
  for (const [path, content] of Object.entries(changed)) {
    const blob = await api.createBlob(content);
    entries.push({ path, mode: '100644', type: 'blob', sha: blob.sha });
  }
  const tree = await api.createTree(baseTree, entries);
  const message = `${title}\n\nVerified immutable SHA updates generated by scripts/github-actions-pin-manager.mjs`;
  const commit = await api.createCommit(message, tree.sha, [baseSha]);

  const existingBranch = await api.getBranch(branch);
  const refPath = `heads/${branch}`;
  if (existingBranch) {
    await api.updateRef(refPath, commit.sha);
  } else {
    await api.createRef(`refs/${refPath}`, commit.sha);
  }

  const open = await api.getOpenPullRequest(branch);
  if (open && open.length > 0) {
    return { created: false, number: open[0].number, url: open[0].html_url };
  }
  const pr = await api.createPullRequest({
    title,
    head: branch,
    base: 'main',
    body,
  });
  return { created: true, number: pr.number, url: pr.html_url };
}

function esc(s) {
  return String(s === undefined || s === null ? '' : s).replace(/\|/g, '\\|');
}

export function buildPullRequestBody(mode, plan) {
  const lines = [];
  if (mode === 'security') {
    lines.push('## SECURITY UPDATE');
    lines.push('');
    lines.push('This PR proposes updates driven by GitHub Security Advisories.');
  } else {
    lines.push('Proposed stable updates for pinned GitHub Actions.');
  }
  lines.push('');
  lines.push('Every target below was verified:');
  lines.push('- release is stable (no draft, no prerelease)');
  lines.push('- tag resolved to a 40-character commit SHA');
  lines.push('- tag resolved identically twice (movement check)');
  lines.push('- immutable pin kept, version comment synchronized');
  lines.push('');
  if (plan.updates.length > 0) {
    lines.push('| Action | File:line | Current | Target | Current ref | Target SHA | Reason |');
    lines.push('|---|---|---|---|---|---|---|');
    for (const u of plan.updates) {
      lines.push(
        `| ${esc(u.action)} | ${esc(u.file)}:${u.line + 1} | ${esc(u.currentValue)} | ${esc(u.targetTag)} | \`${esc(u.currentRef)}\` | \`${esc(u.targetSha)}\` | ${esc(u.reason)} |`,
      );
      for (const adv of u.advisories || []) {
        lines.push(`| Advisory | ${esc(adv.ghsa)}${adv.cve ? ` / ${esc(adv.cve)}` : ''} | ${esc(adv.summary)} | ${esc(adv.url)} | | | |`);
      }
    }
  } else {
    lines.push('_No direct code updates in this batch; see the tables below._');
  }
  if (plan.reportOnly.length > 0) {
    lines.push('');
    lines.push('### Major updates needing a human decision');
    lines.push('');
    lines.push('| Action | File:line | Note |');
    lines.push('|---|---|---|');
    for (const rItem of plan.reportOnly) {
      lines.push(`| ${esc(rItem.action)} | ${esc(rItem.file)}:${rItem.line + 1} | ${esc(rItem.note)} |`);
    }
  }
  if (plan.needsHuman.length > 0) {
    lines.push('');
    lines.push('### Needs human review');
    lines.push('');
    lines.push('| Status | Action | File:line | Note |');
    lines.push('|---|---|---|---|');
    for (const rItem of plan.needsHuman) {
      lines.push(`| ${esc(rItem.status)} | ${esc(rItem.action)} | ${esc(rItem.file)}:${rItem.line + 1} | ${esc(rItem.note)} |`);
    }
  }
  if (plan.errors.length > 0) {
    lines.push('');
    lines.push('### Errors (left untouched)');
    lines.push('');
    lines.push('| Action | File:line | Error |');
    lines.push('|---|---|---|');
    for (const rItem of plan.errors) {
      lines.push(`| ${esc(rItem.action)} | ${esc(rItem.file)}:${rItem.line + 1} | ${esc(rItem.note)} |`);
    }
  }
  lines.push('');
  lines.push('Auto-merge is disabled by policy. A human reviews and merges this PR.');
  return lines.join('\n');
}

// ---------------------------------------------------------------------------
// Orchestration
// ---------------------------------------------------------------------------

export async function runApply({ repoRoot, repo, token, mode, fetchImpl, log = console.error }) {
  const api = createApi({ fetchImpl, token, repo });
  const files = scanFiles(workflowFilePaths(repoRoot));
  const plan = await planUpdates(files, api, mode);
  if (plan.updates.length === 0) {
    log(`pin-manager[${mode}]: no verified update candidates; no PR touched`);
    return { applied: false, plan };
  }
  const changed = applyUpdates(files, plan.updates);
  const problems = validateChanges(files, changed, plan.updates);
  if (problems.length > 0) {
    log(`pin-manager[${mode}]: validation failed; refusing to open a PR`);
    for (const p of problems) log(`  - ${p}`);
    return { applied: false, plan, problems };
  }
  const pr = await ensurePullRequest({ api, mode, changed, plan });
  log(`pin-manager[${mode}]: ${pr.created ? 'created' : 'updated'} PR #${pr.number}: ${pr.url}`);
  return { applied: true, plan, pr, changed };
}

// ---------------------------------------------------------------------------
// Offline audit CLI
// ---------------------------------------------------------------------------

export function auditFiles(repoRoot) {
  const files = scanFiles(workflowFilePaths(repoRoot));
  const findings = [];
  for (const file of files) {
    for (const occ of file.occurrences) {
      if (occ.skip) continue;
      let status = 'OK';
      let note = '';
      if (occ.target.kind === 'MALFORMED') {
        status = 'NEEDS_HUMAN';
        note = 'malformed uses reference';
      } else if (occ.shape === 'BRANCH') {
        status = 'NON_COMPLIANT';
        note = `floating branch/tag @${occ.target.ref}`;
      } else if (occ.shape === 'FLOATING_TAG' || occ.shape === 'STABLE_TAG') {
        status = 'NON_COMPLIANT';
        note = `mutable tag @${occ.target.ref}`;
      } else if (occ.shape === 'SHA' && occ.commentMissing) {
        status = 'NON_COMPLIANT';
        note = 'SHA pin missing version comment';
      } else if (occ.shape === 'SHA') {
        const fullVersion = parseVersion(occ.commentVersion || '');
        if (!fullVersion) {
          status = 'NON_COMPLIANT';
          note = occ.commentVersion
            ? 'version comment is not a full vX.Y.Z release tag'
            : 'SHA pin missing version comment';
        }
      } else if (occ.shape === 'OTHER') {
        status = 'NON_COMPLIANT';
        note = `unrecognized ref @${occ.target.ref}`;
      }
      findings.push({
        file: relativePath(repoRoot, file.path),
        line: occ.line + 1,
        action: occ.target.slug || occ.target.value,
        kind: occ.target.kind,
        ownership: occ.target.ownership || '-',
        ref: occ.target.ref,
        version: occ.commentVersion || '',
        status,
        note,
      });
    }
  }
  return findings;
}

function relativePath(repoRoot, fullPath) {
  return fullPath.startsWith(repoRoot) ? fullPath.slice(repoRoot.length + 1) : fullPath;
}

function printAudit(findings) {
  const bad = findings.filter((f) => f.status !== 'OK');
  console.log(`Scanned ${findings.length} uses references.`);
  console.log(`Compliant: ${findings.length - bad.length}. Non-compliant or flagged: ${bad.length}.`);
  console.log('');
  console.log('FILE:LINE | ACTION | KIND | REF | COMMENT VERSION | STATUS | NOTE');
  for (const f of findings) {
    console.log(`${f.file}:${f.line} | ${f.action} | ${f.kind}/${f.ownership} | ${f.ref} | ${f.version || '-'} | ${f.status} | ${f.note}`);
  }
  console.log('');
  console.log('Run with --apply --mode=weekly (scheduled) to propose fixes through a PR.');
}

function parseArgs(argv) {
  const args = { mode: null, apply: false, json: false };
  for (const arg of argv) {
    if (arg === '--apply') args.apply = true;
    else if (arg === '--json') args.json = true;
    else if (arg === '--check' || arg === '--audit') args.check = true;
    else if (arg.startsWith('--mode=')) args.mode = arg.slice('--mode='.length);
    else if (arg === '--help' || arg === '-h') args.help = true;
  }
  return args;
}

async function main(argv) {
  const args = parseArgs(argv);
  if (args.help) {
    printHelp();
    return 0;
  }
  const repoRoot = process.cwd();
  if (args.apply) {
    if (args.mode !== 'weekly' && args.mode !== 'security') {
      console.error('--mode must be weekly or security');
      return 2;
    }
    const token = process.env.GITHUB_TOKEN;
    const repo = process.env.GITHUB_REPOSITORY;
    if (!token || !repo) {
      console.error('GITHUB_TOKEN and GITHUB_REPOSITORY are required for --apply');
      return 2;
    }
    const result = await runApply({ repoRoot, repo, token, mode: args.mode });
    return result.problems && result.problems.length > 0 ? 1 : 0;
  }
  const findings = auditFiles(repoRoot);
  if (args.json) {
    console.log(JSON.stringify(findings, null, 2));
  } else {
    printAudit(findings);
  }
  return 0;
}

function printHelp() {
  console.log('Usage: node scripts/github-actions-pin-manager.mjs [options]');
  console.log('  (no flags)          Offline compliance audit');
  console.log('  --check             Same as the offline audit');
  console.log('  --json              JSON audit output');
  console.log('  --apply --mode=M    Propose PR updates. M is weekly or security.');
  console.log('Requires GITHUB_TOKEN and GITHUB_REPOSITORY for --apply.');
}

export const __testOnly = { PIN_MANAGER_BRANCHES, PR_TITLES };

/* eslint-disable no-undef */
const isMain = process.argv[1] && import.meta.url.endsWith(process.argv[1].replace(/\\/g, '/').split('/').pop());
if (isMain) {
  main(process.argv.slice(2)).then(
    (code) => process.exit(code),
    (err) => {
      console.error(err);
      process.exit(1);
    },
  );
}
