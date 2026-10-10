import { HttpErrorResponse } from '@angular/common/http';
import { ErrorHandler } from '@angular/core';
import type { Breadcrumb, BrowserOptions, ErrorEvent, EventHint } from '@sentry/angular';
import { isReloadingForNewVersion } from './services/new-version';

// Error tracking in the browser (Sentry, EU region): an Angular crash becomes an issue in Sentry, which emails an
// alert. The backend reports its own errors (with the request ID), so API errors aren't reported again from here.
//
// The DSN only says where to send reports: it's public by design (it ships in every visitor's JavaScript). Empty
// means off. The asbl-club-frontend project's DSN (Sentry → Project settings → Client Keys).
export const SENTRY_DSN =
  'https://e2d3a7d29df89cf586de7354246ba6ba@o4512162632368128.ingest.de.sentry.io/4512162662449232';

// Where reports are sent from: only the live site. Local runs and the e2e tests (localhost) report nothing.
export function errorTrackingOptions(dsn: string, hostname: string): BrowserOptions | null {
  if (!dsn || hostname === 'localhost' || hostname === '127.0.0.1') {
    return null;
  }
  return {
    dsn,
    environment: 'production',
    // Nothing that identifies the visitor or their session: no IP or user details, cookies, headers, bodies or
    // query strings. The stack trace, the page and the browser are enough to find a bug.
    dataCollection: {
      userInfo: false,
      cookies: false,
      httpHeaders: false,
      httpBodies: [],
      urlQueryParams: false,
      stackFrameVariables: false,
    },
    beforeSend: withoutApiErrorsOrFragments,
    beforeBreadcrumb: withoutConsoleOrFragments,
  };
}

// A one-time link carries its token in the fragment (#token=…): the page removes it at once, but a crash before
// that would report the address with it. Fragments are never needed to find a bug, so they never leave.
export function withoutFragment(url: string): string {
  const hash = url.indexOf('#');
  return hash === -1 ? url : url.slice(0, hash);
}

export function withoutApiErrorsOrFragments(
  event: ErrorEvent,
  hint: EventHint,
  reloadingForNewVersion = isReloadingForNewVersion(),
): ErrorEvent | null {
  if (hint.originalException instanceof HttpErrorResponse) {
    return null; // the server's to report (or not a bug: offline, 401, 404…)
  }
  if (reloadingForNewVersion) {
    // A tab left open during a deploy asked for code that is gone, and the page is already reloading into the
    // new version (services/new-version.ts): nothing broken. When the reload doesn't fix it (twice in a row, a
    // really broken file), the page doesn't reload, and the error is reported.
    return null;
  }
  if (event.request?.url) {
    event.request.url = withoutFragment(event.request.url);
  }
  return event;
}

export function withoutConsoleOrFragments(breadcrumb: Breadcrumb): Breadcrumb | null {
  if (breadcrumb.category === 'console') {
    return null; // console messages can hold anything
  }
  for (const key of ['url', 'from', 'to']) {
    const value = breadcrumb.data?.[key];
    if (typeof value === 'string') {
      breadcrumb.data![key] = withoutFragment(value);
    }
  }
  return breadcrumb;
}

// Sentry is downloaded after the app has started (its own file, about 30 kB), so no visitor waits for it. Errors
// meanwhile are kept and handed over once it's there; if it never comes (an ad blocker), they're just logged.
export class DeferredErrorHandler implements ErrorHandler {
  private reporter: ErrorHandler | null = null;
  private waiting: unknown[] | null = [];

  constructor(reporter: Promise<ErrorHandler>) {
    reporter.then(
      (loaded) => {
        this.reporter = loaded;
        this.waiting?.forEach((error) => loaded.handleError(error));
        this.waiting = null;
      },
      () => (this.waiting = null),
    );
  }

  handleError(error: unknown): void {
    console.error(error); // as Angular's own handler does
    if (this.reporter) {
      this.reporter.handleError(error);
    } else {
      this.waiting?.push(error);
    }
  }
}
