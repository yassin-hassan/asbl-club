import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { AccountService, MyAssociation } from '../../api/generated';
import { errorMessageKey } from '../../services/problem';

// The home page of a logged-in user: their associations and their role in each.
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './dashboard.html',
})
export class Dashboard {
  readonly associations = signal<MyAssociation[] | null>(null);
  readonly error = signal<string | null>(null);

  constructor() {
    inject(AccountService).listMyAssociations().subscribe({
      next: (list) => this.associations.set(list),
      error: (err) => this.error.set(errorMessageKey(err)),
    });
  }
}
