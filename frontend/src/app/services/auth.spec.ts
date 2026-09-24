import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth';
import { CurrentUser } from '../models/auth';

describe('AuthService', () => {
  const alice: CurrentUser = { id: '3f2b8c1e-0000-4000-8000-000000000001', email: 'alice@club.test', roles: ['USER'] };

  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  // Fails the test if a request was made that the test didn't expect.
  afterEach(() => http.verify());

  it('logs in, then loads the current user with the new access token', () => {
    let result: CurrentUser | undefined;
    auth.login('alice@club.test', 'password123').subscribe((user) => (result = user));

    const login = http.expectOne('/api/v1/auth/login');
    expect(login.request.method).toBe('POST');
    expect(login.request.body).toEqual({ email: 'alice@club.test', password: 'password123' });
    login.flush({ accessToken: 'token-123', tokenType: 'Bearer', expiresIn: 900 });

    const me = http.expectOne('/api/v1/me');
    expect(me.request.headers.get('Authorization')).toBe('Bearer token-123');
    me.flush(alice);

    expect(result).toEqual(alice);
    expect(auth.user()).toEqual(alice);
    expect(auth.isLoggedIn()).toBe(true);
  });

  it('stays logged out when the credentials are rejected', () => {
    let status: number | undefined;
    auth.login('alice@club.test', 'wrong').subscribe({ error: (err) => (status = err.status) });

    http.expectOne('/api/v1/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    http.expectNone('/api/v1/me');
    expect(status).toBe(401);
    expect(auth.isLoggedIn()).toBe(false);
  });
});
