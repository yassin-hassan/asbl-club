import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth';
import { provideApi } from '../api/generated';

describe('authGuard', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideApi('')],
    });
  });

  function runGuard(url: string) {
    return TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );
  }

  it('sends a logged-out visitor to the login page, remembering where they were going', () => {
    const result = runGuard('/account') as UrlTree;

    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/login?returnUrl=%2Faccount');
  });

  it('lets a logged-in user through', () => {
    const auth = TestBed.inject(AuthService);
    const backend = TestBed.inject(HttpTestingController);
    auth.login('alice@club.test', 'password123').subscribe();
    backend.expectOne('/api/v1/auth/login').flush({ accessToken: 't', tokenType: 'Bearer', expiresIn: 900 });
    backend.expectOne('/api/v1/me').flush({ id: 'some-uuid', email: 'alice@club.test', roles: ['USER'] });

    expect(runGuard('/account')).toBe(true);
  });
});
