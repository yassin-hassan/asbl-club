import { Component, inject } from '@angular/core';
import { UpperCasePipe } from '@angular/common';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from './services/auth';
import { LANGUAGES, LanguageService } from './i18n/language';
import { Logo } from './components/logo/logo';

// The app shell: a header and footer present on every page (navigation, language, login state), the routed page below.
@Component({
  selector: 'app-root',
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

  logout(): void {
    this.auth.logout().subscribe(() => this.router.navigate(['/']));
  }
}
