import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { LanguageService } from './language';

// Tells the API which language the user is reading, so server-side messages (e.g. validation errors)
// come back in the same language as the page.
export const languageInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { 'Accept-Language': inject(LanguageService).active() } }));
};
