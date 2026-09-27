import { Component, effect, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { filter, switchMap } from 'rxjs';
import { AccountService } from '../../api/generated';
import { AuthService } from '../../services/auth';
import { errorMessageKey } from '../../services/problem';
import { ConfirmDialog, ConfirmDialogData } from '../../components/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-account',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [RouterLink, ReactiveFormsModule, TranslocoPipe, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './account.html',
})
export class Account {
  private auth = inject(AuthService);
  private api = inject(AccountService);
  private dialog = inject(MatDialog);
  private router = inject(Router);
  private document = inject(DOCUMENT);

  readonly user = this.auth.user;
  readonly error = signal<string | null>(null);

  // The name shown to association administrators and in emails.
  readonly nameForm = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
  });
  readonly nameSaved = signal(false);

  constructor() {
    // Fill the field with the current name once it's known (and again after a save).
    effect(() => {
      const user = this.user();
      if (user) {
        this.nameForm.setValue({ name: user.name });
      }
    });
  }

  saveName(): void {
    const name = this.nameForm.getRawValue().name.trim();
    if (this.nameForm.invalid || !name) {
      this.nameForm.markAllAsTouched();
      return;
    }
    this.nameSaved.set(false);
    this.api.changeMyName({ name }).pipe(switchMap(() => this.auth.reloadUser())).subscribe({
      next: () => this.nameSaved.set(true),
      error: (err) => this.error.set(errorMessageKey(err)),
    });
  }

  // GDPR right of access: the API answers with JSON; the browser saves it as a file.
  exportData(): void {
    this.api.exportMyData().subscribe({
      next: (data) => {
        const file = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const link = this.document.createElement('a');
        link.href = URL.createObjectURL(file);
        link.download = 'my-data.json';
        link.click();
        URL.revokeObjectURL(link.href);
      },
      error: (err) => this.error.set(errorMessageKey(err)),
    });
  }

  // GDPR right to erasure, irreversible: asks for confirmation first.
  deleteAccount(): void {
    const data: ConfirmDialogData = {
      title: 'account.deleteTitle',
      message: 'account.deleteConfirm',
      confirm: 'account.delete',
      cancel: 'account.cancel',
    };
    this.dialog.open(ConfirmDialog, { data }).afterClosed().pipe(
      filter((confirmed) => confirmed === true),
      switchMap(() => this.api.deleteMyAccount()),
    ).subscribe({
      next: () => {
        this.auth.clearSession();
        this.router.navigate(['/login'], { queryParams: { deleted: 1 } });
      },
      error: (err) => this.error.set(errorMessageKey(err)),
    });
  }
}
