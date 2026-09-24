import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => backend.verify());

  // Logs in through the real AuthService, answering its two requests by hand.
  function logIn(): void {
    auth.login('alice@club.test', 'password123').subscribe();
    backend.expectOne('/api/v1/auth/login').flush({ accessToken: 'token-123', tokenType: 'Bearer', expiresIn: 900 });
    backend.expectOne('/api/v1/me').flush({ id: 'some-uuid', email: 'alice@club.test', roles: ['USER'] });
  }

  function authorizationHeaderSentTo(url: string): string | null {
    http.get(url).subscribe();
    const request = backend.expectOne(url);
    request.flush({});
    return request.request.headers.get('Authorization');
  }

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
