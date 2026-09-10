// Fetches the live OpenAPI spec from a running backend and writes it to openapi-spec.json.
// Run this after changing any backend controller/DTO, then `npm run generate:api` to refresh
// the generated TS client from that (now up to date) static file.
import { writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";
const outPath = join(dirname(fileURLToPath(import.meta.url)), "..", "openapi-spec.json");

const response = await fetch(`${backendUrl}/v3/api-docs`);
if (!response.ok) {
  throw new Error(`Failed to fetch OpenAPI spec from ${backendUrl}: ${response.status} ${response.statusText}`);
}

const spec = await response.json();
writeFileSync(outPath, JSON.stringify(spec, null, 2) + "\n");
console.log(`Wrote ${outPath} (${Object.keys(spec.paths ?? {}).length} paths)`);
