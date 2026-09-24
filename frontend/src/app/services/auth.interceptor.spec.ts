import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth';
import { provideApi } from '../api/generated';

describe('authInterceptor', () => {
  const tokenResponse = (accessToken: string) => ({ accessToken, tokenType: 'Bearer', expiresIn: 900 });
  const unauthorized = { status: 401, statusText: 'Unauthorized' };

  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        provideApi(''),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => backend.verify());

  // Logs in through the real AuthService, answering its two requests by hand.
  function logIn(): void {
    auth.login('alice@club.test', 'password123').subscribe();
    backend.expectOne('/api/v1/auth/login').flush(tokenResponse('token-123'));
    backend.expectOne('/api/v1/me').flush({ id: 'some-uuid', email: 'alice@club.test', roles: ['USER'] });
  }

  function authorizationHeaderSentTo(url: string): string | null {
    http.get(url).subscribe();
    const request = backend.expectOne(url);
    request.flush({});
    return request.request.headers.get('Authorization');
  }

  describe('attaching the token', () => {
    it('attaches the access token to API calls once logged in', () => {
      logIn();
      expect(authorizationHeaderSentTo('/api/v1/me')).toBe('Bearer token-123');
    });

    it('sends no token while logged out', () => {
      expect(authorizationHeaderSentTo('/api/v1/me')).toBeNull();
    });

    it('never sends the token to the auth endpoints', () => {
      logIn();
      expect(authorizationHeaderSentTo('/api/v1/auth/refresh')).toBeNull();
    });

    it('never sends the token to another site', () => {
      logIn();
      expect(authorizationHeaderSentTo('https://evil.example/api/v1/me')).toBeNull();
    });
  });

  describe('expired access token (401)', () => {
    it('refreshes the token and retries the request once', () => {
      logIn();
      let body: unknown;
      http.get('/api/v1/me').subscribe((response) => (body = response));

      backend.expectOne('/api/v1/me').flush(null, unauthorized);
      backend.expectOne('/api/v1/auth/refresh').flush(tokenResponse('token-new'));
      const retry = backend.expectOne('/api/v1/me');
      expect(retry.request.headers.get('Authorization')).toBe('Bearer token-new');
      retry.flush({ ok: true });

      expect(body).toEqual({ ok: true });
    });

    it('shares a single refresh between requests that fail at the same time', () => {
      logIn();
      http.get('/api/v1/me').subscribe();
      http.get('/api/v1/other').subscribe();

      backend.expectOne('/api/v1/me').flush(null, unauthorized);
      backend.expectOne('/api/v1/other').flush(null, unauthorized);
      backend.expectOne('/api/v1/auth/refresh').flush(tokenResponse('token-new')); // exactly one refresh

      backend.expectOne('/api/v1/me').flush({});
      backend.expectOne('/api/v1/other').flush({});
    });

    it('logs out and goes to the login page when the refresh fails too', () => {
      logIn();
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      let status: number | undefined;
      http.get('/api/v1/me').subscribe({ error: (err) => (status = err.status) });

      backend.expectOne('/api/v1/me').flush(null, unauthorized);
      backend.expectOne('/api/v1/auth/refresh').flush(null, unauthorized);

      expect(status).toBe(401);
      expect(auth.isLoggedIn()).toBe(false);
      expect(navigate).toHaveBeenCalledWith(['/login']);
    });

    it('does not retry forever when the retried request is refused again', () => {
      logIn();
      let status: number | undefined;
      http.get('/api/v1/admin/x').subscribe({ error: (err) => (status = err.status) });

      backend.expectOne('/api/v1/admin/x').flush(null, unauthorized);
      backend.expectOne('/api/v1/auth/refresh').flush(tokenResponse('token-new'));
      backend.expectOne('/api/v1/admin/x').flush(null, unauthorized);

      expect(status).toBe(401); // verify() in afterEach proves no third attempt was made
    });

    it('leaves other errors alone', () => {
      logIn();
      let status: number | undefined;
      http.get('/api/v1/me').subscribe({ error: (err) => (status = err.status) });

      backend.expectOne('/api/v1/me').flush(null, { status: 500, statusText: 'Server Error' });

      backend.expectNone('/api/v1/auth/refresh');
      expect(status).toBe(500);
    });
  });
});
