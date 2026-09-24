import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import localeNl from '@angular/common/locales/nl';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './services/auth.interceptor';
import { AuthService } from './services/auth';
import { provideApi } from './api/generated';
import { provideTranslations } from './i18n/transloco';
import { LanguageService } from './i18n/language';
import { languageInterceptor } from './i18n/language.interceptor';

// Date and number formats for French and Dutch (English is built in).
registerLocaleData(localeFr);
registerLocaleData(localeNl);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([languageInterceptor, authInterceptor])),
    // Generated API client: '' keeps URLs relative (/api/...), i.e. same origin, which the dev proxy
    // forwards to Spring and the auth interceptor recognises as our own API.
    provideApi(''),
    provideTranslations(),
    // Both run before the first navigation: the page renders in the right language, and guards already
    // know who is logged in.
    provideAppInitializer(() => inject(LanguageService).init()),
    provideAppInitializer(() => inject(AuthService).restoreSession())
  ]
};
