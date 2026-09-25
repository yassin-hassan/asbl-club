import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, GuardResult, MaybeAsync, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable } from 'rxjs';
import { authGuard } from './auth.guard';
import { AuthService } from './auth';
import { provideApi } from '../api/generated';

describe('authGuard', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideApi('')],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  // The guard answers once the start-up session restore is over; null = no answer yet.
  function runGuard(url: string): GuardResult | null {
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    ) as MaybeAsync<GuardResult>;
    let answer: GuardResult | null = null;
    (result as Observable<GuardResult>).subscribe((value) => (answer = value));
    return answer;
  }

  it('sends a logged-out visitor to the login page, remembering where they were going', () => {
    const result = runGuard('/account') as UrlTree;

    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/login?returnUrl=%2Faccount');
  });

  it('lets a logged-in user through', () => {
    const auth = TestBed.inject(AuthService);
    auth.login('alice@club.test', 'password123').subscribe();
    backend.expectOne('/api/v1/auth/login').flush({ accessToken: 't', tokenType: 'Bearer', expiresIn: 900 });
    backend.expectOne('/api/v1/me').flush({ id: 'some-uuid', email: 'alice@club.test', roles: ['USER'] });

    expect(runGuard('/account')).toBe(true);
  });

  // A returning user opening /account directly isn't bounced to the login form while being logged back in.
  it('waits for the start-up session restore before deciding', () => {
    localStorage.setItem('asbl.hasSession', '1');
    TestBed.inject(AuthService).startSessionRestore();
    let answer: GuardResult | null = null;
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url: '/account' } as RouterStateSnapshot),
    ) as Observable<GuardResult>;
    result.subscribe((value) => (answer = value));
    expect(answer).toBeNull();

    backend.expectOne('/api/v1/auth/refresh').flush({ accessToken: 't', tokenType: 'Bearer', expiresIn: 900 });
    backend.expectOne('/api/v1/me').flush({ id: 'some-uuid', email: 'alice@club.test', roles: ['USER'] });

    expect(answer).toBe(true);
  });
});
