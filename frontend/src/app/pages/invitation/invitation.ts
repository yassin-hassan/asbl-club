import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Location } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { InvitationPreview, InvitationsService } from '../../api/generated';
import { AuthService, rememberAfterVerify } from '../../services/auth';
import { errorMessageKey, problemOf } from '../../services/problem';
import { tokenFromFragment } from '../reset-password/reset-password';

// The invitation link's page. The token (after "#") is kept in this browser while the person logs in or signs up,
// which can include a trip to their inbox to confirm their address, and removed from the address bar at once.
const STORED_TOKEN = 'asbl.invitation';

function storeToken(token: string | null): void {
  try {
    if (token) {
      localStorage.setItem(STORED_TOKEN, token);
    } else {
      localStorage.removeItem(STORED_TOKEN);
    }
  } catch {
    // storage unavailable: the person opens the link from the email again after logging in
  }
}

function storedToken(): string | null {
  try {
    return localStorage.getItem(STORED_TOKEN);
  } catch {
    return null;
  }
}

@Component({
  selector: 'app-invitation',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [RouterLink, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './invitation.html',
})
export class InvitationPage {
  private api = inject(InvitationsService);
  private router = inject(Router);
  readonly auth = inject(AuthService);
  private readonly token = tokenFromFragment(inject(ActivatedRoute).snapshot.fragment) ?? storedToken();

  readonly preview = signal<InvitationPreview | null>(null);
  readonly error = signal<string | null>(null);
  readonly joining = signal(false);

  constructor() {
    inject(Location).replaceState('/invitation');
    storeToken(this.token);
    if (!this.token) {
      this.error.set('invitation.invalid');
      return;
    }
    // After signing up and confirming the email, come back here to accept.
    rememberAfterVerify('/invitation');
    this.api.previewInvitation({ token: this.token }).subscribe({
      next: (preview) => this.preview.set(preview),
      error: (err: HttpErrorResponse) => {
        storeToken(null);
        this.error.set(err.status === 404 ? 'invitation.invalid' : errorMessageKey(err));
      },
    });
  }

  join(): void {
    this.joining.set(true);
    this.api.acceptInvitation({ token: this.token! }).subscribe({
      next: (accepted) => {
        storeToken(null);
        rememberAfterVerify(null);
        this.router.navigate(['/asbls', accepted.slug, 'members']);
      },
      error: (err: HttpErrorResponse) => {
        this.joining.set(false);
        const code = problemOf(err)?.code;
        if (code === 'INVALID_INVITATION') {
          storeToken(null);
        }
        this.error.set(code === 'WRONG_ACCOUNT' ? 'invitation.wrongAccount'
          : code === 'INVALID_INVITATION' ? 'invitation.invalid' : errorMessageKey(err));
      },
    });
  }
}
