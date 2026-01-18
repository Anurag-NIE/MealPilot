import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";
import openapiTS from "openapi-typescript";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

const apiBase =
  process.env.MEALPILOT_API_BASE ||
  process.env.VITE_API_BASE ||
  "http://localhost:9000";
const docsUrl = apiBase.replace(/\/$/, "") + "/v3/api-docs";
const docsFile = process.env.MEALPILOT_API_DOCS_FILE;

const outFile = path.join(root, "src", "gen", "api-types.ts");

const timeoutMs = Number(process.env.MEALPILOT_API_GEN_TIMEOUT_MS || 30_000);

function withTimeout(promise, label) {
  let timeoutId;
  const timeoutPromise = new Promise((_, reject) => {
    timeoutId = setTimeout(() => {
      reject(new Error(`Timed out after ${timeoutMs}ms during ${label}`));
    }, timeoutMs);
  });

  return Promise.race([promise, timeoutPromise]).finally(() =>
    clearTimeout(timeoutId),
  );
}

async function main() {
  let schema;

  if (docsFile) {
    // eslint-disable-next-line no-console
    console.log(`Reading OpenAPI docs from file ${docsFile}...`);
    const text = await withTimeout(
      fs.readFile(docsFile, "utf8"),
      "read OpenAPI docs file",
    );
    // eslint-disable-next-line no-console
    console.log("Parsing OpenAPI JSON...");
    schema = await withTimeout(
      Promise.resolve(JSON.parse(text)),
      "parse OpenAPI JSON",
    );
  } else {
    // eslint-disable-next-line no-console
    console.log(`Fetching OpenAPI docs from ${docsUrl}...`);

    const res = await withTimeout(
      fetch(docsUrl, { headers: { Accept: "application/json" } }),
      "fetch OpenAPI docs",
    );
    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(
        `Failed to fetch OpenAPI docs from ${docsUrl} (${res.status})\n${text}`,
      );
    }

    // eslint-disable-next-line no-console
    console.log("Parsing OpenAPI JSON...");
    schema = await withTimeout(res.json(), "parse OpenAPI JSON");
  }

  // eslint-disable-next-line no-console
  console.log("Generating TypeScript types (openapi-typescript)...");
  const types = await withTimeout(
    openapiTS(schema),
    "openapi-typescript generation",
  );

  await fs.mkdir(path.dirname(outFile), { recursive: true });
  await fs.writeFile(outFile, types, "utf8");

  // eslint-disable-next-line no-console
  console.log(`Generated ${path.relative(root, outFile)} from ${docsUrl}`);
}

main().catch((err) => {
  // eslint-disable-next-line no-console
  console.error(err);
  process.exit(1);
});
