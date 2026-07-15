import { readdirSync, readFileSync, statSync } from "node:fs";
import { resolve, extname } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(fileURLToPath(new URL("../src", import.meta.url)));
const supportedExtensions = new Set([".ts", ".tsx", ".js", ".jsx"]);
const offenders = [];

function stripComments(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .replace(/\/\/.*$/gm, "");
}

function walk(directory) {
  for (const entry of readdirSync(directory)) {
    const fullPath = resolve(directory, entry);
    const stats = statSync(fullPath);

    if (stats.isDirectory()) {
      walk(fullPath);
      continue;
    }

    if (!supportedExtensions.has(extname(fullPath))) {
      continue;
    }

    const source = stripComments(readFileSync(fullPath, "utf8"));
    if (/\b(?:localStorage|sessionStorage)\b/.test(source)) {
      offenders.push(fullPath);
    }
  }
}

walk(root);

if (offenders.length > 0) {
  console.error("Token storage guard failed. Storage APIs found in:");
  for (const offender of offenders) {
    console.error(`- ${offender}`);
  }
  process.exit(1);
}

console.log("Token storage guard passed.");
