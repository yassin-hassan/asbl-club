import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth';
import { CurrentUser } from '../models/auth';

describe('AuthService', () => {
  const alice: CurrentUser = { id: '3f2b8c1e-0000-4000-8000-000000000001', email: 'alice@club.test', roles: ['USER'] };
  const tokenResponse = (accessToken: string) => ({ accessToken, tokenType: 'Bearer', expiresIn: 900 });

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

  describe('login', () => {
    it('keeps the access token, then loads the current user', () => {
      let result: CurrentUser | undefined;
      auth.login('alice@club.test', 'password123').subscribe((user) => (result = user));

      const login = http.expectOne('/api/v1/auth/login');
      expect(login.request.method).toBe('POST');
      expect(login.request.body).toEqual({ email: 'alice@club.test', password: 'password123' });
      login.flush(tokenResponse('token-123'));

      expect(auth.accessToken()).toBe('token-123');
      http.expectOne('/api/v1/me').flush(alice);

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
      expect(auth.accessToken()).toBeNull();
    });
  });

  describe('restoreSession (app start)', () => {
    it('logs the user back in when the refresh cookie is still valid', () => {
      auth.restoreSession().subscribe();

      http.expectOne('/api/v1/auth/refresh').flush(tokenResponse('token-456'));
      http.expectOne('/api/v1/me').flush(alice);

      expect(auth.accessToken()).toBe('token-456');
      expect(auth.user()).toEqual(alice);
    });

    it('quietly stays logged out when there is no valid refresh cookie', () => {
      let failed = false;
      auth.restoreSession().subscribe({ error: () => (failed = true) });

      http.expectOne('/api/v1/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(failed).toBe(false);
      expect(auth.isLoggedIn()).toBe(false);
    });
  });

  describe('refreshAccessToken', () => {
    it('shares one refresh between callers that arrive at the same time', () => {
      const received: string[] = [];
      auth.refreshAccessToken().subscribe((token) => received.push(token));
      auth.refreshAccessToken().subscribe((token) => received.push(token));

      http.expectOne('/api/v1/auth/refresh').flush(tokenResponse('token-789')); // one request, not two

      expect(received).toEqual(['token-789', 'token-789']);
      expect(auth.accessToken()).toBe('token-789');
    });
  });

  describe('logout', () => {
    it('revokes the session on the server and forgets the user', () => {
      logIn();

      auth.logout().subscribe();
      http.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });

      expect(auth.isLoggedIn()).toBe(false);
      expect(auth.accessToken()).toBeNull();
    });

    it('still forgets the user when the server cannot be reached', () => {
      logIn();

      auth.logout().subscribe();
      http.expectOne('/api/v1/auth/logout').error(new ProgressEvent('network error'));

      expect(auth.isLoggedIn()).toBe(false);
    });
  });

  function logIn(): void {
    auth.login('alice@club.test', 'password123').subscribe();
    http.expectOne('/api/v1/auth/login').flush(tokenResponse('token-123'));
    http.expectOne('/api/v1/me').flush(alice);
  }
});
