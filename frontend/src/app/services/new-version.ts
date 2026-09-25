import { NavigationError } from '@angular/router';

// After a deploy, a tab still running the previous version asks for code files that no longer exist (every
// build names them after their content, and a deploy replaces them). The next page then can't load, and the
// app would be stuck. Reloading once gets the new version; the page the user was going to is kept.
// Twice within a few seconds means the file is really broken: then the error is left alone (no reload loop).

const LAST_RELOAD_KEY = 'asbl.reloadedForNewVersion';
const LOOP_GUARD_MS = 10_000;

// The browsers' wordings for "a lazily loaded page's code couldn't be fetched".
const MISSING_CODE = /dynamically imported module|Importing a module script failed/i;

export function reloadForNewVersion(error: NavigationError, reloadAt: (url: string) => void, now = Date.now()): boolean {
  const message = error.error instanceof Error ? error.error.message : String(error.error);
  if (!MISSING_CODE.test(message)) {
    return false;
  }
  try {
    const last = Number(sessionStorage.getItem(LAST_RELOAD_KEY));
    if (now - last < LOOP_GUARD_MS) {
      return false;
    }
    sessionStorage.setItem(LAST_RELOAD_KEY, String(now));
  } catch {
    return false; // storage unavailable: no way to guard against a loop, so don't reload
  }
  reloadAt(error.url);
  return true;
}
