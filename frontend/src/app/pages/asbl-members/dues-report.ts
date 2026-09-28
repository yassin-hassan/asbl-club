import { Component, inject, input, signal, ChangeDetectionStrategy, DOCUMENT } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { TranslocoPipe } from '@jsverse/transloco';
import { DuesService } from '../../api/generated';
import { LanguageService } from '../../i18n/language';
import { errorMessageKey } from '../../services/problem';

// Who paid this year's dues: every current member, paid or not, and the same list as a spreadsheet (the API
// audits each download). For administrators and treasurers; the API refuses everyone else.
@Component({
  selector: 'app-dues-report',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule, MatTableModule],
  template: `
    @if (report.value(); as report) {
      <div class="toolbar">
        <p class="hint">{{ 'dues.summary' | transloco: { paid: report.paid, total: report.members.length, year: report.year } }}</p>
        <button mat-stroked-button type="button" (click)="download(report.year)" [disabled]="downloading()">
          {{ 'dues.download' | transloco }}
        </button>
      </div>
      @if (downloadError(); as error) {
        <p class="error" role="alert">{{ error | transloco }}</p>
      }
      <div class="data-table">
        <table mat-table [dataSource]="report.members" [attr.aria-label]="'dues.listLabel' | transloco">
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef>{{ 'attendees.name' | transloco }}</th>
            <td mat-cell *matCellDef="let m">{{ m.name }}</td>
          </ng-container>
          <ng-container matColumnDef="email">
            <th mat-header-cell *matHeaderCellDef>{{ 'attendees.email' | transloco }}</th>
            <td mat-cell *matCellDef="let m">{{ m.email }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>{{ 'attendees.status' | transloco }}</th>
            <td mat-cell *matCellDef="let m">
              <span class="tag" [class.ok]="m.paid" [class.warm]="!m.paid">
                {{ (m.paid ? 'dues.paid' : 'dues.unpaid') | transloco }}
              </span>
            </td>
          </ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef>{{ 'attendees.amount' | transloco }}</th>
            <td mat-cell *matCellDef="let m">{{ m.amount | currency: 'EUR' : 'symbol' : '1.2-2' : lang() }}</td>
          </ng-container>
          <ng-container matColumnDef="paidAt">
            <th mat-header-cell *matHeaderCellDef>{{ 'dues.paidAt' | transloco }}</th>
            <td mat-cell *matCellDef="let m">{{ m.paidAt | date: 'short' : undefined : lang() }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
      </div>
    } @else if (report.error(); as error) {
      <p class="error" role="alert">{{ errorKey(error) | transloco }}</p>
    } @else {
      <mat-spinner diameter="32" />
    }
  `,
})
export class DuesReport {
  private api = inject(DuesService);
  private document = inject(DOCUMENT);
  readonly lang = inject(LanguageService).active;
  readonly slug = input.required<string>();

  readonly report = rxResource({
    params: () => this.slug(),
    stream: ({ params }) => this.api.getDuesReport(params),
  });
  readonly columns = ['name', 'email', 'status', 'amount', 'paidAt'];
  readonly downloading = signal(false);
  readonly downloadError = signal<string | null>(null);

  download(year: number): void {
    const slug = this.slug();
    this.downloading.set(true);
    this.downloadError.set(null);
    this.api.exportDuesReport(slug, 'body', false, { httpHeaderAccept: 'text/csv' }).subscribe({
      next: (csv) => {
        const url = URL.createObjectURL(csv);
        const link = this.document.createElement('a');
        link.href = url;
        link.download = `dues-${slug}-${year}.csv`;
        link.click();
        URL.revokeObjectURL(url);
        this.downloading.set(false);
      },
      error: (err) => {
        this.downloading.set(false);
        this.downloadError.set(errorMessageKey(err));
      },
    });
  }

  errorKey(error: unknown): string {
    return errorMessageKey(error);
  }
}
