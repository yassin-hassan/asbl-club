import { Component, DOCUMENT, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable, filter } from 'rxjs';
import { AsblMember, AsblMembers, AssociationsService, MemberManagementService, RoleChange } from '../../api/generated';
import { errorMessageKey, problemOf } from '../../services/problem';
import { AuthService } from '../../services/auth';
import { ConfirmDialog, ConfirmDialogData } from '../../components/confirm-dialog/confirm-dialog';
import { InviteByEmail } from './invite-by-email';
import { DuesFee } from './dues-fee';

// An association's member area (members only; the API enforces it). Administrators also manage who joins (the
// invitation link and the requests it produces) and the members themselves (roles, exclusion). Anyone may leave.
// The API enforces every rule, including "at least one active administrator"; the page only offers what makes sense.
// Administrators get one tab per job (members, join requests, invitations, dues) rather than one long page.
@Component({
  selector: 'app-asbl-members',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InviteByEmail, DuesFee, NgTemplateOutlet, RouterLink, TranslocoPipe, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatProgressSpinnerModule, MatTableModule, MatTabsModule,
  ],
  templateUrl: './asbl-members.html',
  styleUrl: './asbl-members.css',
})
export class AsblMembersPage {
  private readonly associations = inject(AssociationsService);
  private readonly management = inject(MemberManagementService);
  private readonly document = inject(DOCUMENT);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  readonly slug = this.route.snapshot.paramMap.get('slug') ?? '';
  readonly asbl = signal<AsblMembers | null>(null);
  readonly error = signal<string | null>(null);
  readonly roles = ['ADMIN', 'TREASURER', 'VIEWER', 'MEMBER'] as const;

  readonly isAdmin = computed(() => this.asbl()?.myRole === 'ADMIN');
  readonly columns = computed(() =>
    this.isAdmin() ? ['name', 'email', 'role', 'status', 'actions'] : ['name', 'email', 'role', 'status'],
  );
  readonly myId = computed(() => this.auth.user()?.id);
  readonly members = computed(() => (this.asbl()?.members ?? []).filter((m) => m.status !== 'PENDING'));
  readonly requests = computed(() => (this.asbl()?.members ?? []).filter((m) => m.status === 'PENDING'));
  readonly activeCount = computed(() => this.members().filter((m) => m.status === 'ACTIVE').length);

  // The members list, narrowed by the search (name or email, accents and case ignored).
  readonly query = signal('');
  readonly shownMembers = computed(() => this.members().filter((m) => matchesSearch(`${m.name} ${m.email}`, this.query())));

  // The open tab, kept in the address (?tab=dues) so a reload or a shared link opens the same one.
  readonly tabs = ['members', 'requests', 'invitations', 'dues'] as const;
  readonly tabIndex = signal(Math.max(0, this.tabs.indexOf(this.route.snapshot.queryParamMap.get('tab') as never)));
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

  selectTab(index: number): void {
    this.tabIndex.set(index);
    this.router.navigate([], {
      relativeTo: this.route, queryParams: { tab: index === 0 ? null : this.tabs[index] },
      queryParamsHandling: 'merge', replaceUrl: true,
    });
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

  changeRole(member: AsblMember, role: string): void {
    this.act(this.management.changeMemberRole(this.slug, member.id, { role: role as RoleChange['role'] }), () => this.load());
  }

  exclude(member: AsblMember): void {
    this.confirm({
      title: 'members.excludeTitle',
      message: 'members.excludeConfirm',
      confirm: 'members.exclude',
      cancel: 'members.cancel',
      params: { name: member.name },
    }).subscribe(() => this.act(this.management.excludeMember(this.slug, member.id), () => this.load()));
  }

  leave(): void {
    this.confirm({
      title: 'members.leaveTitle',
      message: 'members.leaveConfirm',
      confirm: 'members.leave',
      cancel: 'members.cancel',
      params: { name: this.asbl()?.denomination ?? '' },
    }).subscribe(() => this.act(this.associations.leaveAsbl(this.slug), () => this.router.navigateByUrl('/')));
  }

  private confirm(data: ConfirmDialogData): Observable<true> {
    return this.dialog
      .open(ConfirmDialog, { data })
      .afterClosed()
      .pipe(filter((confirmed): confirmed is true => confirmed === true));
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
        this.error.set(this.messageFor(err));
        this.load(); // e.g. a refused role change: show the role the server kept
      },
    });
  }

  private messageFor(err: HttpErrorResponse): string {
    switch (problemOf(err)?.code) {
      case 'LAST_ADMIN':
        return 'members.lastAdmin';
      case 'NOT_ON_YOURSELF':
        return 'members.notOnYourself';
      default:
        return errorMessageKey(err);
    }
  }
}

// Whether a member's name and email match a search: every word typed must appear, case and accents ignored
// ("elise van" finds "Élise Van den Broeck"). An empty search matches everyone.
export function matchesSearch(text: string, query: string): boolean {
  const fold = (value: string) => value.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
  const haystack = fold(text);
  return fold(query).split(/\s+/).filter(Boolean).every((word) => haystack.includes(word));
}
