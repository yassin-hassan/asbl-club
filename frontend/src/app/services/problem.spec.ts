import { HttpErrorResponse } from '@angular/common/http';
import { errorMessageKey, problemOf } from './problem';

describe('Problem Details helpers', () => {
  const apiError = (status: number, body: unknown) =>
    new HttpErrorResponse({ status, error: body, url: '/api/v1/events/42' });

  it('reads the Problem Details body, including field errors', () => {
    const error = apiError(400, { status: 400, title: 'Bad Request', errors: { password: 'must not be blank' } });

    expect(problemOf(error)?.errors).toEqual({ password: 'must not be blank' });
  });

  it('picks a message for the common failures', () => {
    expect(errorMessageKey(apiError(0, null))).toBe('errors.unreachable');
    expect(errorMessageKey(apiError(403, {}))).toBe('errors.forbidden');
    expect(errorMessageKey(apiError(404, {}))).toBe('errors.notFound');
    expect(errorMessageKey(apiError(429, {}))).toBe('errors.tooManyRequests');
  });

  it("falls back to the page's own message otherwise", () => {
    expect(errorMessageKey(apiError(500, {}), 'events.loadError')).toBe('events.loadError');
    expect(errorMessageKey(new Error('boom'))).toBe('errors.generic');
  });
});
