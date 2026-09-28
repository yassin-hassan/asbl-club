// Before `sentry-cli sourcemaps inject` (deploy job in .github/workflows/ci.yml).
//
// Sentry matches a minified stack frame to its source map by a Debug ID, which the browser SDK reads at runtime
// from a small snippet at the top of each file (`_sentryDebugIds`). Angular 22.1+ already writes its own ECMA-426
// Debug IDs (a `//# debugId=` comment and a "debugId" field in the map) but no snippet; sentry-cli then takes the
// files as done and skips them, and the uploaded maps never match (https://github.com/getsentry/cli/issues/1629).
// Removing Angular's IDs lets sentry-cli inject the complete set. Remove this step once sentry-cli handles it.
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const dir = process.argv[2];
if (!dir) {
  console.error('usage: node strip-angular-debug-ids.mjs <build directory>');
  process.exit(1);
}

let files = 0;
for (const name of readdirSync(dir)) {
  const path = join(dir, name);
  if (name.endsWith('.js.map')) {
    const map = JSON.parse(readFileSync(path, 'utf8'));
    delete map.debugId;
    writeFileSync(path, JSON.stringify(map));
  } else if (name.endsWith('.js')) {
    const code = readFileSync(path, 'utf8');
    const stripped = code.replace(/\n?\/\/# debugId=[0-9a-fA-F-]+\n?/g, '\n');
    if (stripped !== code) {
      writeFileSync(path, stripped);
      files++;
    }
  }
}
console.log(`Removed Angular's debug IDs from ${files} files`);
