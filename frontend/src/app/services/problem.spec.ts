import { HttpErrorResponse } from '@angular/common/http';
import { errorMessage, problemOf } from './problem';

describe('Problem Details helpers', () => {
  const apiError = (status: number, body: unknown) =>
    new HttpErrorResponse({ status, error: body, url: '/api/v1/events/42' });

  it('reads the Problem Details body', () => {
    const error = apiError(400, { status: 400, title: 'Bad Request', errors: { password: 'must not be blank' } });

    expect(problemOf(error)?.errors).toEqual({ password: 'must not be blank' });
  });

  it('prefers the detail, then the title', () => {
    expect(errorMessage(apiError(404, { title: 'Not Found', detail: 'No such event.' }))).toBe('No such event.');
    expect(errorMessage(apiError(404, { title: 'Not Found' }))).toBe('Not Found');
  });

  it('explains when the server cannot be reached', () => {
    expect(errorMessage(apiError(0, null))).toContain("Can't reach the server");
  });

  it('falls back when there is no Problem Details body', () => {
    expect(errorMessage(apiError(502, 'Bad Gateway'), 'Could not load events.')).toBe('Could not load events.');
  });
});
