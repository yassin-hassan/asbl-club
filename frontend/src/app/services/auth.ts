import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, switchMap, tap } from 'rxjs';
import { AuthenticationService, MeResponse } from '../api/generated';

export type CurrentUser = MeResponse;

// Owns the login state for the whole app (providedIn: 'root' = one shared instance).
// The HTTP calls themselves come from the client generated from the backend's OpenAPI contract.
// The refresh token never appears here: it's an HttpOnly cookie the browser sends to /api/v1/auth/*.
@Injectable({ providedIn: 'root' })
export class AuthService {
  private api = inject(AuthenticationService);

  // Kept in memory only, never in localStorage. A page reload forgets it; restoreSession() gets a new one.
  private token: string | null = null;
  private refreshInFlight: Observable<string> | null = null;

  private readonly currentUser = signal<CurrentUser | null>(null);
  readonly user = this.currentUser.asReadonly();
  readonly isLoggedIn = computed(() => this.currentUser() !== null);

  // Read by the auth interceptor, which attaches it to API calls. Nothing else should need it.
  accessToken(): string | null {
    return this.token;
  }

  login(email: string, password: string): Observable<CurrentUser> {
    return this.api.login({ email, password }).pipe(
      tap((response) => (this.token = response.accessToken)),
      switchMap(() => this.loadCurrentUser()),
    );
  }

  // Runs once at app start: if the browser still has a refresh cookie, the user is logged back in.
  // Never fails: no cookie (or an expired one) just means "not logged in".
  restoreSession(): Observable<unknown> {
    return this.refreshAccessToken().pipe(
      switchMap(() => this.loadCurrentUser()),
      catchError(() => {
        this.clearSession();
        return of(null);
      }),
    );
  }

  // Gets a new access token using the refresh cookie. Callers that arrive while a refresh is already
  // running share it: the backend accepts each refresh token once, so a second parallel refresh
  // would look like token theft and end the session.
  refreshAccessToken(): Observable<string> {
    this.refreshInFlight ??= this.api.refresh().pipe(
      map((response) => response.accessToken),
      tap((token) => (this.token = token)),
      finalize(() => (this.refreshInFlight = null)),
      shareReplay(1),
    );
    return this.refreshInFlight;
  }

  // Logged out locally even if the server can't be reached; the server call revokes the refresh token.
  logout(): Observable<void> {
    return this.api.logout().pipe(
      map(() => undefined),
      catchError(() => of(undefined)),
      tap(() => this.clearSession()),
    );
  }

  clearSession(): void {
    this.token = null;
    this.currentUser.set(null);
  }

  private loadCurrentUser(): Observable<CurrentUser> {
    return this.api.getCurrentUser().pipe(tap((user) => this.currentUser.set(user)));
  }
}
