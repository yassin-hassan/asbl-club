import { HttpErrorResponse } from '@angular/common/http';

// The API answers every error with Problem Details (RFC 9457, application/problem+json).
export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: Record<string, string>; // validation errors: field -> message, in the request's language
  code?: string; // which of several errors with the same status, e.g. SOLD_OUT, PAYMENTS_DISABLED
}

export function problemOf(error: unknown): Problem | null {
  if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
    return error.error as Problem;
  }
  return null;
}

// The translation key of a message fit to show a person, chosen by what went wrong. The server's own
// "detail" text is for developers (and English only), so it isn't shown as is.
export function errorMessageKey(error: unknown, fallbackKey = 'errors.generic'): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallbackKey;
  }
  switch (error.status) {
    case 0:
      return 'errors.unreachable';
    case 403:
      return 'errors.forbidden';
    case 404:
      return 'errors.notFound';
    case 429:
      return 'errors.tooManyRequests';
    default:
      return fallbackKey;
  }
}
