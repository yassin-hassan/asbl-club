import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, switchMap, tap } from 'rxjs';
import { CurrentUser, TokenResponse } from '../models/auth';

// Owns the login state for the whole app (providedIn: 'root' = one shared instance).
// The refresh token never appears here: it's an HttpOnly cookie the browser handles on its own.
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);

  // Kept in memory only, never in localStorage: an injected script can't read it back later,
  // and a page reload forgets it (the refresh cookie gets a new one, in a later slice).
  private accessToken: string | null = null;

  private readonly currentUser = signal<CurrentUser | null>(null);
  readonly user = this.currentUser.asReadonly();
  readonly isLoggedIn = computed(() => this.currentUser() !== null);

  login(email: string, password: string): Observable<CurrentUser> {
    return this.http.post<TokenResponse>('/api/v1/auth/login', { email, password }).pipe(
      tap((response) => (this.accessToken = response.accessToken)),
      switchMap(() => this.loadCurrentUser()),
    );
  }

  private loadCurrentUser(): Observable<CurrentUser> {
    // Temporary: the token is attached by hand. The next slice's interceptor does this for every call.
    const headers = new HttpHeaders({ Authorization: `Bearer ${this.accessToken}` });
    return this.http
      .get<CurrentUser>('/api/v1/me', { headers })
      .pipe(tap((user) => this.currentUser.set(user)));
  }
}
