// Tests for lcov-to-cobertura.mjs — pure node:test, no frameworks.
// Run: node --test .github/scripts/lcov-to-cobertura.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  escapeXml,
  parseLcov,
  convertLcovToCobertura,
} from "./lcov-to-cobertura.mjs";

const SAMPLE = [
  "TN:",
  "SF:.github/scripts/a.js",
  "DA:1,1",
  "DA:2,0",
  "DA:3,5",
  "LF:3",
  "LH:2",
  "end_of_record",
  "SF:.github/scripts/b.js",
  "DA:10,0",
  "end_of_record",
  "",
].join("\n");

test("parseLcov returns files with sorted lines and max hits", () => {
  const files = parseLcov(SAMPLE);
  assert.equal(files.length, 2);
  assert.equal(files[0].filename, ".github/scripts/a.js");
  assert.deepEqual(files[0].lines, [
    { number: 1, hits: 1 },
    { number: 2, hits: 0 },
    { number: 3, hits: 5 },
  ]);
  assert.deepEqual(files[1].lines, [{ number: 10, hits: 0 }]);
});

test("parseLcov strips a repo prefix from SF entries", () => {
  const files = parseLcov("SF:/repo/.github/scripts/a.js\nDA:1,1\nend_of_record\n", "/repo");
  assert.equal(files[0].filename, ".github/scripts/a.js");
});

test("convertLcovToCobertura emits filenames, lines, hits and rates", () => {
  const xml = convertLcovToCobertura(SAMPLE);
  assert.match(xml, /filename="\.github\/scripts\/a\.js"/);
  assert.match(xml, /<line number="2" hits="0"\/>/);
  assert.match(xml, /<line number="3" hits="5"\/>/);
  // 2 of 4 lines covered overall.
  assert.match(xml, /<coverage line-rate="0\.5000"/);
  // a.js: 2 of 3 lines covered.
  assert.match(xml, /line-rate="0\.6667"/);
});

test("convertLcovToCobertura handles empty input without classes", () => {
  const xml = convertLcovToCobertura("");
  assert.match(xml, /<coverage line-rate="1"/);
  assert.doesNotMatch(xml, /<class /);
});

test("escapeXml escapes markup characters", () => {
  assert.equal(escapeXml('a&<b>"c'), "a&amp;&lt;b&gt;&quot;c");
});
