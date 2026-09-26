import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { JoinInvitation, JoiningService } from '../../api/generated';
import { errorMessageKey, problemOf } from '../../services/problem';

// Where an association's join link leads (logged-in users only: the route guard sends visitors to log in or sign
// up, then back here). Shows which association it is, and lets the person ask to join; an administrator decides.
@Component({
  selector: 'app-join',
  imports: [RouterLink, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './join.html',
})
export class JoinPage {
  private readonly api = inject(JoiningService);
  private readonly token = inject(ActivatedRoute).snapshot.paramMap.get('token') ?? '';
  readonly invitation = signal<JoinInvitation | null>(null);
  readonly justAsked = signal(false);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    this.api.getJoinInvitation(this.token).subscribe({
      next: (invitation) => this.invitation.set(invitation),
      error: (err: HttpErrorResponse) => this.error.set(this.messageFor(err)),
    });
  }

  ask(): void {
    this.sending.set(true);
    this.error.set(null);
    this.api.requestToJoin(this.token).subscribe({
      next: (invitation) => {
        this.invitation.set(invitation);
        this.justAsked.set(true);
        this.sending.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.sending.set(false);
        this.error.set(this.messageFor(err));
      },
    });
  }

  private messageFor(err: HttpErrorResponse): string {
    if (err.status === 404) return 'join.invalid';
    if (problemOf(err)?.code === 'JOIN_REFUSED') return 'join.refused';
    return errorMessageKey(err);
  }
}
