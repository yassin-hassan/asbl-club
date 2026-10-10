import { HttpErrorResponse } from '@angular/common/http';
import { ErrorHandler } from '@angular/core';
import type { ErrorEvent } from '@sentry/angular';
import {
  DeferredErrorHandler,
  errorTrackingOptions,
  withoutApiErrorsOrFragments,
  withoutConsoleOrFragments,
} from './error-tracking';

describe('Error tracking', () => {
  const dsn = 'https://key@o1.ingest.de.sentry.io/2';

  it('is on only for the live site, and only with a DSN', () => {
    expect(errorTrackingOptions(dsn, 'asbl-club.example.workers.dev')?.dsn).toBe(dsn);
    expect(errorTrackingOptions(dsn, 'localhost')).toBeNull(); // local runs and e2e tests
    expect(errorTrackingOptions(dsn, '127.0.0.1')).toBeNull();
    expect(errorTrackingOptions('', 'asbl-club.example.workers.dev')).toBeNull();
  });

  it('collects nothing identifying the visitor', () => {
    expect(errorTrackingOptions(dsn, 'asbl-club.example.workers.dev')?.dataCollection).toEqual({
      userInfo: false,
      cookies: false,
      httpHeaders: false,
      httpBodies: [],
      urlQueryParams: false,
      stackFrameVariables: false,
    });
  });

  // A crash on a one-time link's page, before it removed the token from the address.
  it('never sends the fragment, where one-time links keep their token', () => {
    const event = { request: { url: 'https://site.test/reset-password#token=secret' } } as ErrorEvent;
    expect(withoutApiErrorsOrFragments(event, { originalException: new Error('boom') })?.request?.url)
      .toBe('https://site.test/reset-password');

    const navigation = withoutConsoleOrFragments({
      category: 'navigation',
      data: { from: '/verify-email#token=secret', to: '/' },
    });
    expect(navigation?.data).toEqual({ from: '/verify-email', to: '/' });
  });

  it('leaves out the missing-code error of a tab reloading into a new version, but not a really broken file', () => {
    const missing = { exception: { values: [{ value: 'Failed to fetch dynamically imported module' }] } } as ErrorEvent;
    const hint = { originalException: new TypeError('Failed to fetch dynamically imported module') };
    expect(withoutApiErrorsOrFragments(missing, hint, true)).toBeNull();
    expect(withoutApiErrorsOrFragments(missing, hint, false)).toBe(missing);
  });

  it('leaves API errors to the server, and console messages out', () => {
    const apiError = new HttpErrorResponse({ status: 500, url: '/api/v1/me' });
    expect(withoutApiErrorsOrFragments({} as ErrorEvent, { originalException: apiError })).toBeNull();
    expect(withoutConsoleOrFragments({ category: 'console', message: 'alice@club.test' })).toBeNull();
  });

  describe('while Sentry is still loading', () => {
    beforeEach(() => vi.spyOn(console, 'error').mockImplementation(() => undefined));

    it('keeps errors and hands them over once it has loaded, then reports directly', async () => {
      const sentry = { handleError: vi.fn() };
      let loaded!: (handler: ErrorHandler) => void;
      const handler = new DeferredErrorHandler(new Promise((resolve) => (loaded = resolve)));

      handler.handleError('early');
      expect(sentry.handleError).not.toHaveBeenCalled();
      loaded(sentry);
      await Promise.resolve();
      handler.handleError('later');

      expect(sentry.handleError.mock.calls).toEqual([['early'], ['later']]);
      expect(console.error).toHaveBeenCalledTimes(2); // every error still logged, once
    });

    it('just logs if it never loads (an ad blocker)', async () => {
      const handler = new DeferredErrorHandler(Promise.reject(new Error('blocked')));
      await Promise.resolve();

      handler.handleError('after');
      expect(console.error).toHaveBeenCalledWith('after');
    });
  });
});
