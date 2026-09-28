import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { AccountService, DuesService, MyAssociation, MyDues } from '../../api/generated';
import { LanguageService } from '../../i18n/language';
import { AuthService } from '../../services/auth';
import { errorMessageKey } from '../../services/problem';

// The home page of a logged-in user: their associations and their role in each, and this year's dues.
@Component({
  selector: 'app-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, RouterLink, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './dashboard.html',
})
export class Dashboard {
  readonly associations = signal<MyAssociation[] | null>(null);
  readonly error = signal<string | null>(null);
  // Only associations that collect dues are listed; empty (and hidden) otherwise, or if they can't be loaded.
  readonly dues = signal<MyDues[]>([]);
  readonly lang = inject(LanguageService).active;
  private readonly auth = inject(AuthService);
  // Only shows the link; the API checks the role again.
  readonly superAdmin = computed(() => this.auth.user()?.roles.includes('SUPERADMIN') ?? false);

  constructor() {
    inject(AccountService).listMyAssociations().subscribe({
      next: (list) => this.associations.set(list),
      error: (err) => this.error.set(errorMessageKey(err)),
    });
    inject(DuesService).listMyDues().subscribe({ next: (dues) => this.dues.set(dues), error: () => undefined });
  }
}
