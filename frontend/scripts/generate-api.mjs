import { mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "../..");
const spec = resolve(root, "openapi/openapi.yaml");
const out = resolve(here, "../src/generated/openapi.ts");

mkdirSync(dirname(out), { recursive: true });

const bin = resolve(here, "../node_modules/.bin/openapi-typescript");
const result = spawnSync(bin, [spec, "-o", out], { stdio: "inherit" });
if (result.status !== 0) {
  process.exit(result.status ?? 1);
}

console.log(`Generated ${out}`);
