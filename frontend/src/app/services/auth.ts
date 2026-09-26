import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, finalize, map, of, shareReplay, switchMap, tap } from 'rxjs';
// Specific files, not the generated index (which would pull every API service into the initial download).
import { AuthenticationService } from '../api/generated/api/authentication.service';
import { MeResponse } from '../api/generated/model/meResponse';

export type CurrentUser = MeResponse;

// Not a secret, only "this browser had a session", so it's worth asking the server at start-up. The refresh
// cookie itself is HttpOnly (invisible to the app); without this hint, every visitor's first page would wait
// for the API, which on the free hosting can take minutes to wake up.
const SESSION_HINT_KEY = 'asbl.hasSession';

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

  // True while the session is being restored at start-up (the page shows, logged-in areas wait).
  private readonly restoring = signal(false);
  readonly restoringSession = this.restoring.asReadonly();
  private restored$: Observable<void> = of(undefined);

  // Read by the auth interceptor, which attaches it to API calls. Nothing else should need it.
  accessToken(): string | null {
    return this.token;
  }

  login(email: string, password: string): Observable<CurrentUser> {
    return this.api.login({ email, password }).pipe(
      tap((response) => (this.token = response.accessToken)),
      switchMap(() => this.loadCurrentUser()),
      tap(() => setSessionHint(true)),
    );
  }

  // A new account is logged in straight away: the API answers with the same tokens as a login.
  register(name: string, email: string, password: string): Observable<CurrentUser> {
    return this.api.register({ name, email, password }).pipe(
      tap((response) => (this.token = response.accessToken)),
      switchMap(() => this.loadCurrentUser()),
      tap(() => setSessionHint(true)),
    );
  }

  // App start, without holding up the first page: if this browser had a session, log the user back in in the
  // background. Anything that needs to know who is logged in waits for whenRestored() (the route guards).
  startSessionRestore(): void {
    if (!hasSessionHint()) {
      return; // a visitor: no need to ask the server
    }
    this.restoring.set(true);
    this.restored$ = this.restoreSession().pipe(
      finalize(() => this.restoring.set(false)),
      shareReplay(1),
    );
    this.restored$.subscribe();
  }

  // Completes once the start-up restore is over (at once if there was none).
  whenRestored(): Observable<void> {
    return this.restored$;
  }

  // Logs the user back in if the browser still has a valid refresh cookie. Never fails: a rejected cookie
  // means "logged out" (and the hint is dropped); a server that can't answer (asleep, down) just means
  // "not now" — the hint stays, so the next visit tries again.
  restoreSession(): Observable<void> {
    return this.refreshAccessToken().pipe(
      switchMap(() => this.loadCurrentUser()),
      map(() => undefined),
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 401) {
          this.clearSession();
        } else {
          this.token = null;
          this.currentUser.set(null);
        }
        return of(undefined);
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

  // The session is over (logout, rejected refresh, account deleted).
  clearSession(): void {
    this.token = null;
    this.currentUser.set(null);
    setSessionHint(false);
  }

  private loadCurrentUser(): Observable<CurrentUser> {
    return this.api.getCurrentUser().pipe(tap((user) => this.currentUser.set(user)));
  }
}

// Storage can be unavailable (private mode, blocked site data): the hint is only an optimisation.
function hasSessionHint(): boolean {
  try {
    return localStorage.getItem(SESSION_HINT_KEY) === '1';
  } catch {
    return false;
  }
}

function setSessionHint(present: boolean): void {
  try {
    if (present) {
      localStorage.setItem(SESSION_HINT_KEY, '1');
    } else {
      localStorage.removeItem(SESSION_HINT_KEY);
    }
  } catch {
    // ignore: see hasSessionHint
  }
}
