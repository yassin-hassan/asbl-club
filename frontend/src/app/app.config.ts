import { ApplicationConfig, DOCUMENT, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import localeNl from '@angular/common/locales/nl';
import { provideRouter, withNavigationErrorHandler } from '@angular/router';
import { MatIconRegistry } from '@angular/material/icon';
import { provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './services/auth.interceptor';
import { AuthService } from './services/auth';
import { reloadForNewVersion } from './services/new-version';
// From its own file, not the generated index: the index lists every API service (APIS), which would pull all of
// them into the initial download instead of the pages that use them.
import { provideApi } from './api/generated/provide-api';
import { provideTranslations } from './i18n/transloco';
import { LanguageService } from './i18n/language';
import { languageInterceptor } from './i18n/language.interceptor';

// Date and number formats for French and Dutch (English is built in).
registerLocaleData(localeFr);
registerLocaleData(localeNl);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // A tab left open during a deploy can't load the new pages' code: reload once to get the new version.
    provideRouter(
      routes,
      withNavigationErrorHandler((error) => {
        const document = inject(DOCUMENT);
        reloadForNewVersion(error, (url) => document.location.assign(url));
      }),
    ),
    provideHttpClient(withXhr(), withInterceptors([languageInterceptor, authInterceptor])),
    // Generated API client: '' keeps URLs relative (/api/...), i.e. same origin, which the dev proxy
    // forwards to Spring and the auth interceptor recognises as our own API.
    provideApi(''),
    provideTranslations(),
    // The design system uses line icons: Material's "outlined" set is the closest standard match.
    provideAppInitializer(() => {
      inject(MatIconRegistry).setDefaultFontSetClass('material-icons-outlined');
    }),
    // The language loads before the first page (a static file from the CDN: fast), so it renders translated.
    provideAppInitializer(() => inject(LanguageService).init()),
    // The session restore only starts here: the page doesn't wait for the API (which may be waking up);
    // the route guards that need to know who is logged in wait for it instead.
    provideAppInitializer(() => {
      inject(AuthService).startSessionRestore();
    }),
  ]
};
