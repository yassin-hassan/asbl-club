import { Component, inject } from '@angular/core';
import { UpperCasePipe } from '@angular/common';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from './services/auth';
import { LANGUAGES, LanguageService } from './i18n/language';

// The app shell: a toolbar present on every page (navigation, language, login state), the routed page below.
@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet, RouterLink, UpperCasePipe, TranslocoPipe,
    MatToolbarModule, MatButtonModule, MatIconModule,
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
