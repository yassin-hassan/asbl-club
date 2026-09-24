import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth';

// Runs for every HttpClient request and adds "Authorization: Bearer <access token>" where it belongs.
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).accessToken();
  if (token === null || !needsAccessToken(req.url)) {
    return next(req);
  }
  // Requests are immutable: clone it with the extra header instead of changing it.
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};

// Only our own API (relative /api/ URLs, so never another site), and not the auth endpoints:
// login/logout don't need it, and an expired token sent to /refresh would get a 401 before the
// endpoint even runs.
function needsAccessToken(url: string): boolean {
  return url.startsWith('/api/') && !url.startsWith('/api/v1/auth/');
}
