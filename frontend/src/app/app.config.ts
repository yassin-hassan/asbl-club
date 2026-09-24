import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './services/auth.interceptor';
import { AuthService } from './services/auth';
import { provideApi } from './api/generated';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    // Generated API client: '' keeps URLs relative (/api/...), i.e. same origin, which the dev proxy
    // forwards to Spring and the auth interceptor recognises as our own API.
    provideApi(''),
    // The app waits for this before the first navigation, so guards already know who is logged in.
    provideAppInitializer(() => inject(AuthService).restoreSession())
  ]
};
