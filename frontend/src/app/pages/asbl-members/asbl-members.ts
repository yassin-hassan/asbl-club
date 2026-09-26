import { Component, DOCUMENT, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable } from 'rxjs';
import { AsblMember, AsblMembers, AssociationsService, MemberManagementService } from '../../api/generated';
import { errorMessageKey } from '../../services/problem';

// An association's member area (members only; the API enforces it). Administrators also manage who joins: the
// invitation link they share, and the requests it produces.
@Component({
  selector: 'app-asbl-members',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [RouterLink, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule, MatTableModule],
  templateUrl: './asbl-members.html',
  styleUrl: './asbl-members.css',
})
export class AsblMembersPage {
  private readonly associations = inject(AssociationsService);
  private readonly management = inject(MemberManagementService);
  private readonly document = inject(DOCUMENT);
  readonly slug = inject(ActivatedRoute).snapshot.paramMap.get('slug') ?? '';
  readonly asbl = signal<AsblMembers | null>(null);
  readonly error = signal<string | null>(null);
  readonly columns = ['name', 'email', 'role', 'status'];

  readonly isAdmin = computed(() => this.asbl()?.myRole === 'ADMIN');
  readonly members = computed(() => (this.asbl()?.members ?? []).filter((m) => m.status !== 'PENDING'));
  readonly requests = computed(() => (this.asbl()?.members ?? []).filter((m) => m.status === 'PENDING'));
  readonly joinToken = signal<string | null>(null);
  readonly joinUrl = computed(() => {
    const token = this.joinToken();
    return token ? `${this.document.location.origin}/join/${token}` : null;
  });
  readonly copied = signal(false);
  readonly busy = signal(false);

  constructor() {
    this.load();
  }

  newLink(): void {
    this.act(this.management.newJoinLink(this.slug), (link) => this.joinToken.set(link.token ?? null));
  }

  disableLink(): void {
    this.act(this.management.disableJoinLink(this.slug), () => this.joinToken.set(null));
  }

  approve(member: AsblMember): void {
    this.act(this.management.approveJoinRequest(this.slug, member.id), () => this.load());
  }

  decline(member: AsblMember): void {
    this.act(this.management.declineJoinRequest(this.slug, member.id), () => this.load());
  }

  // The clipboard can be refused (older browsers, some embedded views): the link stays visible to copy by hand.
  copy(): void {
    const url = this.joinUrl();
    if (!url) return;
    navigator.clipboard?.writeText(url).then(
      () => {
        this.copied.set(true);
        setTimeout(() => this.copied.set(false), 2000);
      },
      () => undefined,
    );
  }

  private load(): void {
    this.associations.getAsblMembers(this.slug).subscribe({
      next: (asbl) => {
        this.asbl.set(asbl);
        if (asbl.myRole === 'ADMIN') {
          this.management.getJoinLink(this.slug).subscribe((link) => this.joinToken.set(link.token ?? null));
        }
      },
      error: (err: HttpErrorResponse) =>
        this.error.set(err.status === 403 ? 'members.noAccess' : errorMessageKey(err)),
    });
  }

  private act<T>(request: Observable<T>, done: (result: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: (result) => {
        this.busy.set(false);
        done(result);
      },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(errorMessageKey(err));
      },
    });
  }
}
