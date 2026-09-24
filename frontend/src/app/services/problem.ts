import { HttpErrorResponse } from '@angular/common/http';

// The API answers every error with Problem Details (RFC 9457, application/problem+json).
export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: Record<string, string>; // validation errors: field -> message
}

export function problemOf(error: unknown): Problem | null {
  if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
    return error.error as Problem;
  }
  return null;
}

// A message fit to show a person, whatever went wrong.
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (error instanceof HttpErrorResponse && error.status === 0) {
    return "Can't reach the server. Check your connection and try again.";
  }
  const problem = problemOf(error);
  return problem?.detail ?? problem?.title ?? fallback;
}
