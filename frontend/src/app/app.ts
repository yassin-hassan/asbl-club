import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { UpperCasePipe } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, take } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from './services/auth';
import { LANGUAGES, LanguageService } from './i18n/language';
import { Logo } from './components/logo/logo';

// The app shell: a header and footer present on every page (navigation, language, login state), the routed page below.
@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    RouterOutlet, RouterLink, UpperCasePipe, TranslocoPipe, Logo, MatButtonModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  readonly auth = inject(AuthService);
  readonly language = inject(LanguageService);
  readonly languages = LANGUAGES;
  private router = inject(Router);

  // False until the first page is on screen. That page may wait for the session restore (a returning user),
  // and the API may be waking up: show that something is happening instead of an empty page.
  readonly firstPageShown = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      take(1),
      map(() => true),
    ),
    { initialValue: false },
  );

  // Back to "/", which shows the landing page once logged out. Forced even when already on "/" (the dashboard):
  // by default the router ignores a navigation to the current URL, and the dashboard would stay on screen.
  logout(): void {
    this.auth.logout().subscribe(() => this.router.navigateByUrl('/', { onSameUrlNavigation: 'reload' }));
  }
}
