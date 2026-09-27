import { Component, inject, input, signal, ChangeDetectionStrategy } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { FormGroupDirective, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';
import { InvitationsService, PendingInvitation } from '../../api/generated';
import { LanguageService } from '../../i18n/language';
import { errorMessageKey, problemOf } from '../../services/problem';

// Administrators invite someone by email: the link joins them directly, and only them. Below the form, the
// invitations not accepted yet, which can be cancelled.
@Component({
  selector: 'app-invite-by-email',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DatePipe, ReactiveFormsModule, TranslocoPipe, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h3>{{ 'invite.byEmail' | transloco }}</h3>
    <p class="hint">{{ 'invite.byEmailHint' | transloco }}</p>
    <form [formGroup]="form" #formDirective="ngForm" (ngSubmit)="send(formDirective)" novalidate class="invite-form">
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>{{ 'invite.emailLabel' | transloco }}</mat-label>
        <input matInput type="email" formControlName="email" autocomplete="off" />
        @if (form.controls.email.invalid) {
          <mat-error>{{ 'login.invalidEmail' | transloco }}</mat-error>
        }
      </mat-form-field>
      <button mat-flat-button type="submit" [disabled]="sending()">{{ 'invite.send' | transloco }}</button>
    </form>
    @if (sentTo(); as email) {
      <p role="status">{{ 'invite.sent' | transloco: { email } }}</p>
    }
    @if (error(); as error) {
      <p class="error" role="alert">{{ error | transloco }}</p>
    }
    @if (pending.value(); as list) {
      @if (list.length > 0) {
        <h3>{{ 'invite.pending' | transloco }}</h3>
        <ul class="requests">
          @for (invitation of list; track invitation.id) {
            <li>
              <span class="who">
                <b>{{ invitation.email }}</b>
                <span class="muted">{{ 'invite.until' | transloco: { date: (invitation.expiresAt | date: 'longDate' : undefined : lang()) } }}</span>
              </span>
              <button mat-button type="button" (click)="cancel(invitation)"
                      [attr.aria-label]="('invite.cancel' | transloco) + ' ' + invitation.email">{{ 'invite.cancel' | transloco }}</button>
            </li>
          }
        </ul>
      }
    }
  `,
  styles: '.invite-form { display: flex; flex-wrap: wrap; gap: 12px; align-items: flex-start; } .invite-form mat-form-field { flex: 1 1 260px; }',
})
export class InviteByEmail {
  private api = inject(InvitationsService);
  readonly lang = inject(LanguageService).active;
  readonly slug = input.required<string>();

  readonly form = inject(NonNullableFormBuilder).group({ email: ['', [Validators.required, Validators.email]] });
  readonly sending = signal(false);
  readonly sentTo = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly pending = rxResource({
    params: () => this.slug(),
    stream: ({ params }) => this.api.listInvitations(params),
  });

  send(formDirective: FormGroupDirective): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const email = this.form.getRawValue().email.trim();
    this.sending.set(true);
    this.error.set(null);
    this.sentTo.set(null);
    this.api.inviteByEmail(this.slug(), { email }).subscribe({
      next: () => {
        this.sending.set(false);
        this.sentTo.set(email);
        formDirective.resetForm();
        this.pending.reload();
      },
      error: (err: HttpErrorResponse) => {
        this.sending.set(false);
        const code = problemOf(err)?.code;
        this.error.set(code === 'ALREADY_MEMBER' ? 'invite.alreadyMember'
          : code === 'INVITATION_LIMIT' ? 'invite.limit' : errorMessageKey(err));
      },
    });
  }

  cancel(invitation: PendingInvitation): void {
    this.api.cancelInvitation(this.slug(), invitation.id).subscribe({
      next: () => this.pending.reload(),
      error: (err) => this.error.set(errorMessageKey(err)),
    });
  }
}
