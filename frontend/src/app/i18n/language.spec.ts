import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslocoService } from '@jsverse/transloco';
import { LanguageService } from './language';
import { languageInterceptor } from './language.interceptor';
import { translationsForTests } from './translations.testing';

describe('LanguageService + languageInterceptor', () => {
  let language: LanguageService;
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [translationsForTests()],
      providers: [provideHttpClient(withInterceptors([languageInterceptor])), provideHttpClientTesting()],
    });
    language = TestBed.inject(LanguageService);
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('switches the whole app: translations, <html lang> and the remembered choice', () => {
    language.use('nl');

    expect(language.active()).toBe('nl');
    expect(TestBed.inject(TranslocoService).getActiveLang()).toBe('nl');
    expect(document.documentElement.lang).toBe('nl');
    expect(localStorage.getItem('language')).toBe('nl');
  });

  it('tells the API which language the user reads', () => {
    language.use('en');
    http.get('/api/v1/me').subscribe();

    const request = backend.expectOne('/api/v1/me');
    expect(request.request.headers.get('Accept-Language')).toBe('en');
    request.flush({});
  });

  it('does not add the header to requests for other sites', () => {
    http.get('https://example.org/data').subscribe();

    const request = backend.expectOne('https://example.org/data');
    expect(request.request.headers.has('Accept-Language')).toBe(false);
    request.flush({});
  });
});
