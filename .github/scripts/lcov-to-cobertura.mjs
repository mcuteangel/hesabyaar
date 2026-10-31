// Convert LCOV coverage to Cobertura XML for CodeAnt (XML only).
// Usage: node .github/scripts/lcov-to-cobertura.mjs <input.lcov> <output.xml>
// Only line data (DA) is converted; branch data (BRDA) is ignored, so
// branch-rate is always 0. CodeAnt reports line coverage from this file.
import { readFileSync, writeFileSync } from "node:fs";

export function escapeXml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

// Parse LCOV text into [{ filename, lines: [{ number, hits }] }].
// `stripPrefix` (e.g. the repo checkout path) is removed from SF entries
// that start with it, so filenames stay repo-relative.
export function parseLcov(text, stripPrefix = "") {
  const files = [];
  let current = null;
  const finish = () => {
    if (current) {
      current.lines = [...current.byNumber.entries()]
        .map(([number, hits]) => ({ number, hits }))
        .sort((a, b) => a.number - b.number);
      delete current.byNumber;
      files.push(current);
      current = null;
    }
  };
  for (const raw of String(text).split("\n")) {
    const line = raw.trim();
    if (line.startsWith("SF:")) {
      finish();
      let filename = line.slice(3);
      if (stripPrefix && filename.startsWith(stripPrefix + "/")) {
        filename = filename.slice(stripPrefix.length + 1);
      }
      current = { filename, byNumber: new Map() };
    } else if (line.startsWith("DA:") && current) {
      const [number, hits] = line.slice(3).split(",");
      const n = Number(number);
      const h = Number(hits);
      if (Number.isInteger(n) && Number.isInteger(h)) {
        current.byNumber.set(n, Math.max(current.byNumber.get(n) ?? 0, h));
      }
    } else if (line === "end_of_record") {
      finish();
    }
  }
  finish();
  return files.map(({ filename, lines }) => ({ filename, lines }));
}

function rate(covered, total) {
  return total === 0 ? "1" : (covered / total).toFixed(4);
}

export function convertLcovToCobertura(text, stripPrefix = "") {
  const files = parseLcov(text, stripPrefix).sort((a, b) =>
    a.filename.localeCompare(b.filename),
  );
  let totalLines = 0;
  let totalCovered = 0;
  const classes = files.map((file) => {
    const covered = file.lines.filter((l) => l.hits > 0).length;
    totalLines += file.lines.length;
    totalCovered += covered;
    const linesXml = file.lines
      .map((l) => `          <line number="${l.number}" hits="${l.hits}"/>`)
      .join("\n");
    return [
      `      <class name="${escapeXml(file.filename)}" filename="${escapeXml(file.filename)}" line-rate="${rate(covered, file.lines.length)}" branch-rate="0">`,
      "        <methods/>",
      "        <lines>",
      linesXml,
      "        </lines>",
      "      </class>",
    ].join("\n");
  });
  const timestamp = Math.floor(Date.now() / 1000);
  return [
    `<?xml version="1.0"?>`,
    `<coverage line-rate="${rate(totalCovered, totalLines)}" branch-rate="0" version="1.0" timestamp="${timestamp}">`,
    "  <sources>",
    "    <source>.</source>",
    "  </sources>",
    "  <packages>",
    `    <package name="." line-rate="${rate(totalCovered, totalLines)}" branch-rate="0">`,
    "      <classes>",
    ...classes,
    "      </classes>",
    "    </package>",
    "  </packages>",
    "</coverage>",
    "",
  ].join("\n");
}

const isMain = process.argv[1] && import.meta.url.endsWith(process.argv[1].split("/").pop());
if (isMain) {
  const [input, output] = process.argv.slice(2);
  if (!input || !output) {
    console.error("Usage: node lcov-to-cobertura.mjs <input.lcov> <output.xml>");
    process.exit(2);
  }
  const text = readFileSync(input, "utf8");
  if (!text.trim()) {
    console.error(`Empty LCOV input: ${input}`);
    process.exit(1);
  }
  writeFileSync(output, convertLcovToCobertura(text, process.cwd()));
}
