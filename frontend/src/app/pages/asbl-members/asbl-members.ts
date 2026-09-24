import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { TranslocoPipe } from '@jsverse/transloco';
import { AsblMembers, AssociationsService } from '../../api/generated';
import { errorMessageKey } from '../../services/problem';

// An association's member area (members only; the API enforces it).
@Component({
  selector: 'app-asbl-members',
  imports: [RouterLink, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule, MatTableModule],
  templateUrl: './asbl-members.html',
})
export class AsblMembersPage {
  readonly slug = inject(ActivatedRoute).snapshot.paramMap.get('slug') ?? '';
  readonly asbl = signal<AsblMembers | null>(null);
  readonly error = signal<string | null>(null);
  readonly columns = ['name', 'email', 'role', 'status'];

  constructor() {
    inject(AssociationsService).getAsblMembers(this.slug).subscribe({
      next: (asbl) => this.asbl.set(asbl),
      error: (err: HttpErrorResponse) =>
        this.error.set(err.status === 403 ? 'members.noAccess' : errorMessageKey(err)),
    });
  }
}
