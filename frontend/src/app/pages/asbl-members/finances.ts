import { Component, inject, input, signal, ChangeDetectionStrategy, DOCUMENT } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { TranslocoPipe } from '@jsverse/transloco';
import { FinancesService } from '../../api/generated';
import { LanguageService } from '../../i18n/language';
import { errorMessageKey } from '../../services/problem';

// An association's money, year by year: what came in (tickets, dues), what went back to buyers, the platform's
// commission and what is left; then every payment, and the whole year as a spreadsheet for the accounts (the API
// audits each download). For administrators and treasurers; the API refuses everyone else.
@Component({
  selector: 'app-finances',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule, MatTableModule],
  template: `
    @if (report.value(); as report) {
      <div class="toolbar head">
        <span class="year">
          <label for="finances-year">{{ 'finances.year' | transloco }}</label>
          <select id="finances-year" [value]="report.year" (change)="showYear(+$any($event.target).value)">
            @for (y of report.years; track y) {
              <option [value]="y" [selected]="y === report.year">{{ y }}</option>
            }
          </select>
        </span>
        <button mat-stroked-button type="button" (click)="download(report.year)"
                [disabled]="downloading() || report.totals.payments === 0">
          {{ 'finances.download' | transloco }}
        </button>
      </div>
      @if (downloadError(); as error) {
        <p class="error" role="alert">{{ error | transloco }}</p>
      }

      <dl class="totals">
        <div>
          <dt>{{ 'finances.collected' | transloco }}</dt>
          <dd>{{ report.totals.collected | currency: 'EUR' : 'symbol' : '1.2-2' : lang() }}</dd>
          <dd class="detail">{{ 'finances.collectedDetail' | transloco: {
            tickets: (report.totals.tickets | currency: 'EUR' : 'symbol' : '1.2-2' : lang()),
            dues: (report.totals.dues | currency: 'EUR' : 'symbol' : '1.2-2' : lang()) } }}</dd>
        </div>
        <div>
          <dt>{{ 'finances.refunded' | transloco }}</dt>
          <dd>{{ 0 - report.totals.refunded | currency: 'EUR' : 'symbol' : '1.2-2' : lang() }}</dd>
        </div>
        <div>
          <dt>{{ 'finances.commission' | transloco }}</dt>
          <dd>{{ 0 - report.totals.commission | currency: 'EUR' : 'symbol' : '1.2-2' : lang() }}</dd>
        </div>
        <div class="net">
          <dt>{{ 'finances.net' | transloco }}</dt>
          <dd>{{ report.totals.net | currency: 'EUR' : 'symbol' : '1.2-2' : lang() }}</dd>
        </div>
      </dl>
      <p class="hint">{{ 'finances.stripeFees' | transloco }}</p>

      @if (report.payments.length > 0) {
        <p class="hint">{{ 'finances.count' | transloco: { count: report.totals.payments, year: report.year } }}</p>
        <div class="data-table">
          <table mat-table [dataSource]="report.payments" [attr.aria-label]="'finances.listLabel' | transloco">
            <ng-container matColumnDef="date">
              <th mat-header-cell *matHeaderCellDef>{{ 'finances.date' | transloco }}</th>
              <td mat-cell *matCellDef="let p" class="number">{{ p.paidAt | date: 'short' : undefined : lang() }}</td>
            </ng-container>
            <ng-container matColumnDef="payer">
              <th mat-header-cell *matHeaderCellDef>{{ 'finances.payer' | transloco }}</th>
              <!-- The email in a tooltip (and in the spreadsheet): in a column, it squeezed the table. -->
              <td mat-cell *matCellDef="let p" [title]="p.payerEmail">{{ p.payerName }}</td>
            </ng-container>
            <ng-container matColumnDef="what">
              <th mat-header-cell *matHeaderCellDef>{{ 'finances.what' | transloco }}</th>
              <td mat-cell *matCellDef="let p" class="what">
                @if (p.kind === 'TICKET') {
                  {{ p.eventTitle }}<span class="muted small">{{ p.ticketLabel }}</span>
                } @else {
                  {{ 'finances.dues' | transloco: { year: p.duesYear } }}
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="amount">
              <th mat-header-cell *matHeaderCellDef>{{ 'finances.amount' | transloco }}</th>
              <td mat-cell *matCellDef="let p" class="number">{{ p.amount | currency: 'EUR' : 'symbol' : '1.2-2' : lang() }}</td>
            </ng-container>
            <ng-container matColumnDef="commission">
              <th mat-header-cell *matHeaderCellDef>{{ 'finances.commission' | transloco }}</th>
              <td mat-cell *matCellDef="let p" class="number">
                {{ p.commission | currency: 'EUR' : 'symbol' : '1.2-2' : lang() }}
                @if (p.status === 'REFUNDED' && p.commission === 0) {
                  <span class="muted small">{{ 'finances.commissionBack' | transloco }}</span>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>{{ 'finances.status' | transloco }}</th>
              <td mat-cell *matCellDef="let p">
                @if (p.status === 'REFUNDED') {
                  <span class="tag warm">{{ 'finances.refundedOn' | transloco: { date: (p.refundedAt | date: 'shortDate' : undefined : lang()) } }}</span>
                } @else {
                  <span class="tag ok">{{ 'finances.paid' | transloco }}</span>
                }
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>
        </div>
        @if (report.totalPages > 1) {
          <div class="toolbar">
            <button mat-stroked-button type="button" (click)="page.set(report.page - 1)" [disabled]="report.page === 0">
              {{ 'finances.previous' | transloco }}
            </button>
            <span class="hint">{{ 'finances.page' | transloco: { page: report.page + 1, total: report.totalPages } }}</span>
            <button mat-stroked-button type="button" (click)="page.set(report.page + 1)"
                    [disabled]="report.page + 1 >= report.totalPages">
              {{ 'finances.next' | transloco }}
            </button>
          </div>
        }
      } @else {
        <p class="empty">{{ 'finances.none' | transloco: { year: report.year } }}</p>
      }
    } @else if (report.error(); as error) {
      <p class="error" role="alert">{{ errorKey(error) | transloco }}</p>
    } @else {
      <mat-spinner diameter="32" />
    }
  `,
  styles: `
    .head { margin-top: 0; justify-content: space-between; }
    .year { display: flex; align-items: center; gap: 8px; font-size: 14px; color: var(--asbl-slate-600); }
    .year select {
      font: inherit; color: var(--asbl-slate-900, inherit); padding: 6px 8px;
      border: 1px solid var(--asbl-slate-200); border-radius: 8px; background: var(--asbl-slate-50);
    }
    .totals {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; margin: 16px 0 8px;
    }
    .totals > div {
      border: 1px solid var(--asbl-slate-200); border-radius: var(--asbl-radius-lg); padding: 14px 16px; background: #fff;
    }
    .totals dt { font-size: 13px; color: var(--asbl-slate-600); }
    .totals dd { margin: 4px 0 0; font-size: 22px; font-weight: 600; font-variant-numeric: tabular-nums; }
    .totals dd.detail { font-size: 12.5px; font-weight: 400; color: var(--asbl-slate-600); }
    .totals .net { border-color: var(--asbl-green-700); }
    .totals .net dd { color: var(--asbl-green-700); }
    /* On a phone, the table scrolls sideways rather than squeezing its columns. */
    table { min-width: 680px; }
    .small { font-size: 12.5px; }
    .what { min-width: 240px; }
    .small { display: block; }
    .number { font-variant-numeric: tabular-nums; white-space: nowrap; }
  `,
})
export class Finances {
  private api = inject(FinancesService);
  private document = inject(DOCUMENT);
  readonly lang = inject(LanguageService).active;
  readonly slug = input.required<string>();

  // No year chosen yet: the API answers with the current one.
  readonly year = signal<number | undefined>(undefined);
  readonly page = signal(0);
  readonly report = rxResource({
    params: () => ({ slug: this.slug(), year: this.year(), page: this.page() }),
    stream: ({ params }) => this.api.getFinances(params.slug, params.year, params.page),
  });
  readonly columns = ['date', 'payer', 'what', 'amount', 'commission', 'status'];
  readonly downloading = signal(false);
  readonly downloadError = signal<string | null>(null);

  showYear(year: number): void {
    this.page.set(0);
    this.year.set(year);
  }

  download(year: number): void {
    const slug = this.slug();
    this.downloading.set(true);
    this.downloadError.set(null);
    this.api.exportFinances(slug, year, 'body', false, { httpHeaderAccept: 'text/csv' }).subscribe({
      next: (csv) => {
        const url = URL.createObjectURL(csv);
        const link = this.document.createElement('a');
        link.href = url;
        link.download = `finances-${slug}-${year}.csv`;
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
