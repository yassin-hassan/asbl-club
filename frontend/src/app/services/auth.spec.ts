import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService, CurrentUser } from './auth';
import { provideApi } from '../api/generated';

describe('AuthService', () => {
  const alice: CurrentUser = { id: '3f2b8c1e-0000-4000-8000-000000000001', email: 'alice@club.test', roles: ['USER'] };
  const tokenResponse = (accessToken: string) => ({ accessToken, tokenType: 'Bearer', expiresIn: 900 });

  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi('')],
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

  describe('register', () => {
    it('creates the account and logs straight in', () => {
      auth.register('Alice', 'alice@club.test', 'password123').subscribe();

      const register = http.expectOne('/api/v1/auth/register');
      expect(register.request.body).toEqual({ name: 'Alice', email: 'alice@club.test', password: 'password123' });
      register.flush(tokenResponse('token-new'), { status: 201, statusText: 'Created' });
      http.expectOne('/api/v1/me').flush(alice);

      expect(auth.accessToken()).toBe('token-new');
      expect(auth.user()).toEqual(alice);
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

  // The first page mustn't wait for the API: only a browser that had a session asks the server at start-up.
  describe('startSessionRestore (app start, in the background)', () => {
    it('asks nothing of the server for a visitor who never logged in here', () => {
      auth.startSessionRestore();

      http.expectNone('/api/v1/auth/refresh');
      let restored = false;
      auth.whenRestored().subscribe(() => (restored = true));
      expect(restored).toBe(true);
    });

    it('logs a returning user back in, and makes waiting guards wait until then', () => {
      localStorage.setItem('asbl.hasSession', '1');
      auth.startSessionRestore();
      let restored = false;
      auth.whenRestored().subscribe(() => (restored = true));
      expect(auth.restoringSession()).toBe(true);
      expect(restored).toBe(false);

      http.expectOne('/api/v1/auth/refresh').flush(tokenResponse('token-456'));
      http.expectOne('/api/v1/me').flush(alice);

      expect(restored).toBe(true);
      expect(auth.restoringSession()).toBe(false);
      expect(auth.user()).toEqual(alice);
    });

    it('forgets the session when the server rejects the refresh cookie', () => {
      localStorage.setItem('asbl.hasSession', '1');
      auth.startSessionRestore();

      http.expectOne('/api/v1/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(auth.isLoggedIn()).toBe(false);
      expect(localStorage.getItem('asbl.hasSession')).toBeNull();
    });

    // E.g. the free API server still waking up: that's "not now", not "logged out".
    it('keeps the hint when the server cannot answer, so the next visit tries again', () => {
      localStorage.setItem('asbl.hasSession', '1');
      auth.startSessionRestore();

      http.expectOne('/api/v1/auth/refresh').flush(null, { status: 524, statusText: 'Origin timeout' });

      expect(auth.isLoggedIn()).toBe(false);
      expect(localStorage.getItem('asbl.hasSession')).toBe('1');
    });

    it('remembers the session at login and forgets it at logout', () => {
      logIn();
      expect(localStorage.getItem('asbl.hasSession')).toBe('1');

      auth.logout().subscribe();
      http.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
      expect(localStorage.getItem('asbl.hasSession')).toBeNull();
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
