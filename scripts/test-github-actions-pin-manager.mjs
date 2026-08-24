#!/usr/bin/env node
// Tests for scripts/github-actions-pin-manager.mjs.
// Run: node --test scripts/test-github-actions-pin-manager.mjs

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import {
  parseUsesLine,
  renderUsesLine,
  parseTarget,
  refShape,
  versionFromComment,
  parseVersion,
  compareVersions,
  normalizeVersion,
  listStableVersions,
  resolveTagToCommitSha,
  scanFiles,
  planUpdates,
  applyUpdates,
  validateChanges,
  buildPullRequestBody,
  ensurePullRequest,
  createApi,
} from './github-actions-pin-manager.mjs';

const SHA_A = 'a'.repeat(40);
const SHA_B = 'b'.repeat(40);
const SHA_C = 'c'.repeat(40);

function json(body, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

// Minimal routing mock of the GitHub REST surface used by the planner.
function makeFetch(routes) {
  const calls = [];
  const impl = async (url, options = {}) => {
    calls.push({ method: options.method || 'GET', url });
    for (const r of routes) {
      const m = url.match(r.url);
      if (m && (r.method || 'GET') === (options.method || 'GET')) {
        return typeof r.reply === 'function' ? r.reply(m, ++r.hits || (r.hits = 1)) : r.reply;
      }
    }
    return json({ message: 'not found' }, 404);
  };
  return { impl, calls };
}

const releasesRoute = (repo, versions) => ({
  method: 'GET',
  url: new RegExp(`/repos/${repo}/releases`.replace('/', '/')),
  reply: json(
    versions.map((v) => ({
      tag_name: `v${v}`,
      draft: false,
      prerelease: /-/.test(v),
    })),
  ),
});

const tagRefRoute = (repo, tag, sha) => ({
  method: 'GET',
  url: new RegExp(`/repos/${repo}/git/ref/tags/${tag}$`),
  reply: json({ object: { type: 'commit', sha } }),
});

function repoRoutes(repo, versions) {
  const rel = releasesRoute(repo, versions);
  // Route matching must be specific enough that two repos never collide.
  return [
    {
      method: 'GET',
      url: new RegExp(`/repos/${repo}/releases\\?`),
      reply: rel.reply,
    },
    tagRefRoute(repo, 'v1.0.0', SHA_A),
    tagRefRoute(repo, 'v1.1.0', SHA_B),
    tagRefRoute(repo, 'v2.0.0', SHA_C),
  ];
}

// ---------------------------------------------------------------------------
// Detection: line parsing and classification
// ---------------------------------------------------------------------------

test('detects sha pinned action with version comment', () => {
  const parsed = parseUsesLine('      - uses: actions/checkout@' + SHA_A + ' # v5.0.0');
  assert.ok(parsed);
  const target = parseTarget(parsed.value);
  assert.equal(target.owner, 'actions');
  assert.equal(target.repo, 'checkout');
  assert.equal(refShape(target.ref), 'SHA');
  assert.equal(versionFromComment(parsed.comment), '5.0.0');
});

test('detects floating major tag as mutable', () => {
  const parsed = parseUsesLine('- uses: actions/cache@v4');
  const target = parseTarget(parsed.value);
  assert.equal(refShape(target.ref), 'FLOATING_TAG');
  assert.equal(target.ref, 'v4');
});

test('detects branch reference', () => {
  const t1 = refShape('main');
  const t2 = refShape('master');
  const t3 = refShape('latest');
  assert.deepEqual([t1, t2, t3], ['BRANCH', 'BRANCH', 'BRANCH']);
});

test('flags missing version comment on sha pin', () => {
  const parsed = parseUsesLine('uses: actions/checkout@' + SHA_A);
  assert.ok(parsed);
  assert.equal(parsed.comment, '');
  assert.equal(versionFromComment(''), null);
});

test('rejects malformed short sha', () => {
  assert.notEqual(refShape('a'.repeat(39)), 'SHA');
  assert.equal(refShape('a'.repeat(39)), 'OTHER');
});

test('classifies local action and skips it', () => {
  const parsed = parseUsesLine('      - name: x\n');
  assert.equal(parsed, null);
  const local = parseTarget('./.github/actions/setup-rust-android');
  assert.equal(local.kind, 'LOCAL');
});

test('classifies reusable workflow from another repository', () => {
  const parsed = parseUsesLine('  uses: owner/repo/.github/workflows/ci.yml@' + SHA_B + ' # v1.2.3');
  const target = parseTarget(parsed.value);
  assert.equal(target.kind, 'REUSABLE_WORKFLOW');
  assert.equal(target.owner, 'owner');
  assert.equal(target.repo, 'repo');
  assert.equal(refShape(target.ref), 'SHA');
});

test('parses quoted values and preserves quote on render', () => {
  const parsed = parseUsesLine(`        uses: "actions/checkout@${SHA_A}" # v5.0.0`);
  assert.equal(parsed.quote, '"');
  assert.equal(parseTarget(parsed.value).ref, SHA_A);
  const rendered = renderUsesLine(parsed, `actions/checkout@${SHA_B}`, '# v6.0.0');
  assert.equal(rendered, `        uses: "actions/checkout@${SHA_B}" # v6.0.0`);
});

test('handles inline comment without space after hash', () => {
  const parsed = parseUsesLine('uses: dtolnay/rust-toolchain@' + SHA_C + ' #stable');
  assert.equal(versionFromComment(parsed.comment), null);
});

test('ignores non-uses lines including run steps containing word uses', () => {
  assert.equal(parseUsesLine('  run: echo uses: nothing'), null);
  assert.equal(parseUsesLine('# uses: actions/checkout@v4'), null);
});

test('scanFiles reports duplicate action references per occurrence', () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  writeFileSync(
    join(dir, 'w.yml'),
    [
      'jobs:',
      '  a:',
      '    steps:',
      '      - uses: actions/checkout@v4',
      '  b:',
      '    steps:',
      '      - uses: actions/checkout@v4',
    ].join('\n'),
  );
  const [file] = scanFiles([join(dir, 'w.yml')]);
  assert.equal(file.occurrences.length, 2);
  assert.ok(file.occurrences.every((o) => o.target.slug === 'actions/checkout'));
});

// ---------------------------------------------------------------------------
// Version handling
// ---------------------------------------------------------------------------

test('version helpers pad compare and reject', () => {
  assert.deepEqual(normalizeVersion('4'), '4.0.0');
  assert.deepEqual(normalizeVersion('4.2'), '4.2.0');
  assert.deepEqual(parseVersion('v7.10.1'), [7, 10, 1]);
  assert.ok(compareVersions('7.1.0', '7.0.1') > 0);
  assert.ok(compareVersions('7.0.1', '7.1.0') < 0);
  assert.ok(Number.isNaN(compareVersions('abc', '1.0.0')));
});

test('replacement synchronizes sha and version comment together', () => {
  const original = `        uses: actions/github-script@${SHA_A} # v7.0.1`;
  const parsed = parseUsesLine(original);
  const rendered = renderUsesLine(
    parsed,
    parsed.value.replace(/@[^@]*$/, `@${SHA_B}`),
    '# v7.1.0',
  );
  assert.equal(rendered, `        uses: actions/github-script@${SHA_B} # v7.1.0`);
  assert.ok(!rendered.includes(SHA_A));
  assert.ok(rendered.endsWith('# v7.1.0'));
});

test('applyUpdates rewrites only planned lines byte-exact elsewhere', () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  const lines = [
    'name: Demo',
    'on: push',
    'jobs:',
    '  j:',
    '    runs-on: ubuntu-latest   ',
    '    steps:',
    '      - uses: actions/checkout@v4',
    '        with:',
    '          fetch-depth: 0 # keep history',
    '      - run: echo "uses: fake"',
    '',
  ];
  const path = join(dir, 'demo.yml');
  writeFileSync(path, lines.join('\n'));
  const files = scanFiles([path]);
  const updates = [
    {
      file: path,
      line: 6,
      action: 'actions/checkout',
      currentRef: 'actions/checkout@v4',
      currentValue: '4',
      targetTag: 'v4.2.2',
      targetVersion: '4.2.2',
      targetSha: SHA_B,
      reason: 'stable update',
    },
  ];
  const changed = applyUpdates(files, updates);
  const problems = validateChanges(files, changed, updates);
  assert.deepEqual(problems, []);
  const newLines = changed[path].split('\n');
  assert.equal(newLines[6], `      - uses: actions/checkout@${SHA_B} # v4.2.2`);
  newLines.forEach((l, i) => {
    if (i !== 6) assert.equal(l, lines[i], `line ${i} must stay untouched`);
  });
});

test('validateChanges rejects unsynchronized comment', () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  const path = join(dir, 'x.yml');
  const text = `- uses: actions/checkout@${SHA_A} # v5.0.0\n`;
  writeFileSync(path, text);
  const files = scanFiles([path]);
  const bad = { ...applyUpdates(files, [{ file: path, line: 0, targetTag: 'v6.0.0', targetVersion: '6.0.0', targetSha: SHA_B }]) };
  // Simulate a stale comment by editing outside of applyUpdates.
  bad[path] = `- uses: actions/checkout@${SHA_B} # v5.0.0\n`;
  const problems = validateChanges(files, bad, [{ file: path, line: 0, targetTag: 'v6.0.0', targetVersion: '6.0.0', targetSha: SHA_B }]);
  assert.ok(problems.some((p) => p.includes('not synchronized')));
});

// ---------------------------------------------------------------------------
// Safety
// ---------------------------------------------------------------------------

async function setupRepo(tmpRoot, content) {
  mkdirSync(join(tmpRoot, '.github', 'workflows'), { recursive: true });
  const wf = join(tmpRoot, '.github', 'workflows', 'w.yml');
  writeFileSync(wf, content);
  return wf;
}

test('weekly plan converts floating tag to same major immutable pin', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, '- uses: actions/checkout@v4\n');
  const { impl } = makeFetch([
    {
      method: 'GET',
      url: /\/repos\/actions\/checkout\/releases\?/,
      reply: json([
        { tag_name: 'v6.0.0', draft: false, prerelease: false },
        { tag_name: 'v4.2.2', draft: false, prerelease: false },
      ]),
    },
    tagRefRoute('actions/checkout', 'v4.2.2', SHA_B),
  ]);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  assert.equal(plan.updates.length, 1);
  assert.equal(plan.updates[0].targetTag, 'v4.2.2');
  assert.equal(plan.updates[0].targetSha, SHA_B);
  assert.equal(plan.reportOnly.filter((rItem) => rItem.note.includes('major')).length >= 0, true);
});

test('weekly plan reports major bump instead of applying it', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: actions/checkout@${SHA_A} # v5.0.0\n`);
  const { impl } = makeFetch([
    {
      method: 'GET',
      url: /\/repos\/actions\/checkout\/releases\?/,
      reply: json([{ tag_name: 'v7.0.0', draft: false, prerelease: false }]),
    },
    tagRefRoute('actions/checkout', 'v7.0.0', SHA_C),
  ]);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  assert.equal(plan.updates.length, 0);
  assert.equal(plan.reportOnly.length, 1);
  assert.ok(plan.reportOnly[0].note.includes('major'));
});

test('prerelease and drafts are rejected as targets', async () => {
  const stable = listStableVersions;
  const api = {
    getReleases: async () => [
      { tag_name: 'v9.9.9-rc1', draft: false, prerelease: true },
      { tag_name: 'v9.9.8', draft: true, prerelease: false },
      { tag_name: 'v9.9.7', draft: false, prerelease: false },
    ],
    getTags: async () => [],
  };
  const result = await stable(api);
  assert.deepEqual(result.versions.map((vItem) => vItem.tag), ['v9.9.7']);
});

test('unknown release source is reported not guessed', async () => {
  const api = {
    getReleases: async () => {
      const err = new Error('nf');
      err.status = 404;
      throw err;
    },
    getTags: async () => [],
  };
  const result = await listStableVersions(api);
  assert.equal(result.source, 'unknown');
  assert.equal(result.versions.length, 0);
});

test('invalid resolved sha aborts candidate', async () => {
  const api = {
    getReleases: async () => [{ tag_name: 'v2.0.0', draft: false, prerelease: false }],
    getTags: async () => [],
    getTagRef: async () => ({ object: { type: 'commit', sha: 'z'.repeat(39) } }),
  };
  await assert.rejects(() => resolveTagToCommitSha(api, 'v2.0.0'), /not a 40-hex SHA|cannot resolve/);
});

test('annotated tag dereferences to commit sha', async () => {
  const api = {
    getReleases: async () => [],
    getTags: async () => [{ name: 'v1.2.3' }],
    getTagRef: async () => ({ object: { type: 'tag', sha: SHA_A } }),
    getTagObject: async () => ({ object: { type: 'commit', sha: SHA_B } }),
  };
  const result = await listStableVersions(api);
  assert.equal(result.source, 'tags');
  const sha = await resolveTagToCommitSha(api, 'v1.2.3');
  assert.equal(sha, SHA_B);
});

test('tag movement between resolutions aborts candidate', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: evil/action@${SHA_A} # v1.0.0\n`);
  let n = 0;
  const { impl } = makeFetch([
    {
      method: 'GET',
      url: /\/repos\/evil\/action\/releases\?/,
      reply: json([{ tag_name: 'v1.1.0', draft: false, prerelease: false }]),
    },
    {
      method: 'GET',
      url: /\/repos\/evil\/action\/git\/ref\/tags\/v1\.1\.0$/,
      reply: () => json({ object: { type: 'commit', sha: ++n === 1 ? SHA_B : SHA_C } }),
    },
  ]);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  // The candidate must be aborted and surfaced, never silently applied.
  assert.equal(plan.updates.length, 1);
  assert.equal(plan.updates[0].aborted, true);
  assert.ok(plan.updates[0].reason.includes('moved during resolution'));
  assert.equal(plan.updates[0].targetSha, undefined);
});

test('branch reference goes to needsHuman not auto updated', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, '- uses: some/action@main\n');
  const { impl } = makeFetch([
    { method: 'GET', url: /\/repos\/some\/action\/releases\?/, reply: json([{ tag_name: 'v1.0.0', draft: false, prerelease: false }]) },
  ]);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  assert.equal(plan.updates.length, 0);
  assert.equal(plan.needsHuman.length, 1);
});

test('sha pin missing version comment goes to needsHuman', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: some/action@${SHA_A}\n`);
  const { impl } = makeFetch([
    { method: 'GET', url: /\/repos\/some\/action\/releases\?/, reply: json([{ tag_name: 'v2.0.0', draft: false, prerelease: false }]) },
  ]);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  assert.equal(plan.updates.length, 0);
  assert.equal(plan.needsHuman.length, 1);
  assert.ok(plan.needsHuman[0].note.includes('version comment'));
});

test('partial major comment is a usable baseline but channel comment is not', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(
    dir,
    [
      `- uses: Swatinem/rust-cache@${SHA_A} # v2`,
      `- uses: dtolnay/rust-toolchain@${SHA_B} # stable`,
    ].join('\n') + '\n',
  );
  const { impl } = makeFetch([
    { method: 'GET', url: /\/repos\/Swatinem\/rust-cache\/releases\?/, reply: json([{ tag_name: 'v2.8.0', draft: false, prerelease: false }]) },
    tagRefRoute('Swatinem/rust-cache', 'v2.8.0', SHA_C),
    { method: 'GET', url: /\/repos\/dtolnay\/rust-toolchain\/releases\?/, reply: json([]) },
    { method: 'GET', url: /\/repos\/dtolnay\/rust-toolchain\/tags\?/, reply: json([]) },
  ]);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  assert.equal(plan.updates.length, 1);
  assert.equal(plan.updates[0].action, 'Swatinem/rust-cache');
  assert.equal(plan.updates[0].targetTag, 'v2.8.0');
  assert.equal(plan.needsHuman.length, 1);
  assert.ok(plan.needsHuman[0].action.startsWith('dtolnay/'));
});

// ---------------------------------------------------------------------------
// PR lifecycle
// ---------------------------------------------------------------------------

function lifecycleRoutes({ existingPr = [], branchExists = false } = {}) {
  const state = {};
  return {
    state,
    routes: [
      { method: 'GET', url: /\/repos\/o\/r$/, reply: json({ default_branch: 'main' }) },
      { method: 'GET', url: /\/repos\/o\/r\/git\/ref\/heads\/main$/, reply: json({ object: { sha: SHA_A } }) },
      { method: 'GET', url: /\/repos\/o\/r\/git\/commits\/[0-9a-f]{40}$/, reply: json({ tree: { sha: 'tree0'.padEnd(40, '0') } }) },
      { method: 'POST', url: /\/repos\/o\/r\/git\/blobs$/, reply: json({ sha: SHA_B }) },
      { method: 'POST', url: /\/repos\/o\/r\/git\/trees$/, reply: json({ sha: SHA_C }) },
      { method: 'POST', url: /\/repos\/o\/r\/git\/commits$/, reply: json({ sha: SHA_A }) },
      {
        method: 'GET',
        url: /\/repos\/o\/r\/git\/ref\/heads\/ci\/pin-updates$/,
        reply: () => json(branchExists ? { object: { sha: SHA_B } } : { message: 'nf' }, branchExists ? 200 : 404),
      },
      {
        method: 'PATCH',
        url: /\/repos\/o\/r\/git\/refs\/heads\/ci\/pin-updates$/,
        reply: () => {
          state.updatedRef = true;
          return json({});
        },
      },
      {
        method: 'POST',
        url: /\/repos\/o\/r\/git\/refs$/,
        reply: () => {
          state.createdRef = true;
          return json({});
        },
      },
      { method: 'GET', url: /\/repos\/o\/r\/pulls\?head=/, reply: json(existingPr) },
      {
        method: 'POST',
        url: /\/repos\/o\/r\/pulls$/,
        reply: (m) => {
          state.createdPull = true;
          return json({ number: 42, html_url: `https://example.test/pull/${m ? '' : ''}42` });
        },
      },
    ],
  };
}

function samplePlan() {
  return {
    updates: [
      {
        file: 'wf.yml',
        line: 0,
        action: 'actions/checkout',
        ownership: 'GITHUB_OWNED',
        kind: 'ACTION',
        currentValue: 'v5.0.0',
        currentRef: `actions/checkout@${SHA_A}`,
        targetTag: 'v5.1.0',
        targetVersion: '5.1.0',
        targetSha: SHA_B,
        reason: 'stable update v5.0.0 -> v5.1.0',
      },
    ],
    reportOnly: [],
    needsHuman: [],
    errors: [],
  };
}

async function runLifecycle(mode, opts) {
  const { impl, calls } = makeFetch(lifecycleRoutes(opts).routes);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const pr = await ensurePullRequest({
    api,
    mode,
    changed: { 'wf.yml': 'new content' },
    plan: samplePlan(),
  });
  return { pr, calls };
}

test('no existing pr creates exactly one pull request', async () => {
  const { pr, calls } = await runLifecycle('weekly', {});
  assert.equal(pr.created, true);
  const created = calls.filter((c) => c.method === 'POST' && /\/pulls$/.test(c.url));
  assert.equal(created.length, 1);
  assert.ok(calls.some((c) => c.method === 'POST' && /git\/refs$/.test(c.url)));
});

test('existing open pr is updated not duplicated', async () => {
  const { pr, calls } = await runLifecycle('weekly', {
    existingPr: [{ number: 7, html_url: 'u7' }],
    branchExists: true,
  });
  assert.equal(pr.created, false);
  assert.equal(pr.number, 7);
  assert.ok(calls.some((c) => c.method === 'PATCH' && /refs\/heads/.test(c.url)));
  const created = calls.filter((c) => c.method === 'POST' && /\/pulls$/.test(c.url));
  assert.equal(created.length, 0);
});

test('security mode uses its own branch title and body header', async () => {
  const { pr, calls } = await runLifecycle('security', {});
  assert.ok(calls.some((c) => c.url.includes('ci/pin-security')));
  assert.ok(!calls.some((c) => c.url.includes('ci/pin-updates')));
  const body = buildPullRequestBody('security', samplePlan());
  assert.ok(body.startsWith('## SECURITY UPDATE'));
  assert.ok(body.includes(SHA_B));
});

test('pr body lists verification steps and disables auto merge', () => {
  const body = buildPullRequestBody('weekly', samplePlan());
  assert.ok(body.includes('stable (no draft, no prerelease)'));
  assert.ok(body.includes('twice'));
  assert.ok(body.includes('Auto-merge is disabled'));
  assert.ok(body.includes(`\`${SHA_B}\``));
});
