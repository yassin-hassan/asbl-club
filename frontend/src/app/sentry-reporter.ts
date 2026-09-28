import { ErrorHandler } from '@angular/core';
import { BrowserOptions, createErrorHandler, init } from '@sentry/angular';

// Loaded in the background (see main.ts): only what's used is imported, so the download stays small.
export function startReporting(options: BrowserOptions): ErrorHandler {
  init(options);
  return createErrorHandler({ logErrors: false }); // DeferredErrorHandler already logs
}
