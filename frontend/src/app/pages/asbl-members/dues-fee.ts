import { Component, inject, input, signal, ChangeDetectionStrategy } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { CurrencyPipe } from '@angular/common';
import { FormGroupDirective, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';
import { DuesService } from '../../api/generated';
import { DuesReport } from './dues-report';
import { LanguageService } from '../../i18n/language';
import { errorMessageKey } from '../../services/problem';

// Administrators set the yearly membership fee (or stop collecting dues). Members then see it on their dashboard
// and pay it online, which needs the association's Stripe account first.
@Component({
  selector: 'app-dues-fee',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DuesReport, ReactiveFormsModule, RouterLink, TranslocoPipe, MatButtonModule, MatFormFieldModule,
    MatInputModule],
  template: `
    @if (settings.value(); as settings) {
      @if (!settings.paymentsEnabled) {
        <p>{{ 'dues.needsPayments' | transloco }}</p>
        <div><a mat-stroked-button [routerLink]="['/asbls', slug(), 'manage', 'payments']">{{ 'dues.setUpPayments' | transloco }}</a></div>
      } @else {
        @if (settings.annualFee; as fee) {
          <p>{{ 'dues.current' | transloco: { amount: (fee | currency: 'EUR' : 'symbol' : '1.2-2' : lang()), year: settings.year } }}</p>
        } @else {
          <p>{{ 'dues.off' | transloco }}</p>
        }
        <form [formGroup]="form" #formDirective="ngForm" (ngSubmit)="save(formDirective)" novalidate class="fee-form">
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>{{ 'dues.feeLabel' | transloco }}</mat-label>
            <input matInput type="number" inputmode="decimal" min="1" max="9999.99" step="0.01" formControlName="fee" />
            <span matTextSuffix>€</span>
            @if (form.controls.fee.invalid) {
              <mat-error>{{ 'dues.feeInvalid' | transloco }}</mat-error>
            }
          </mat-form-field>
          <button mat-flat-button type="submit" [disabled]="saving()">{{ 'dues.save' | transloco }}</button>
          @if (settings.annualFee) {
            <button mat-button type="button" (click)="stop(formDirective)" [disabled]="saving()">{{ 'dues.stop' | transloco }}</button>
          }
        </form>
      }
    }
    @if (saved()) {
      <p role="status">{{ 'dues.saved' | transloco }}</p>
    }
    @if (error(); as error) {
      <p class="error" role="alert">{{ error | transloco }}</p>
    }
    @if (settings.value()?.annualFee) {
      <app-dues-report [slug]="slug()" />
    }
  `,
  styles: '.fee-form { display: flex; flex-wrap: wrap; gap: 12px; align-items: flex-start; } .fee-form mat-form-field { flex: 0 1 200px; }',
})
export class DuesFee {
  private api = inject(DuesService);
  readonly lang = inject(LanguageService).active;
  readonly slug = input.required<string>();

  readonly form = inject(NonNullableFormBuilder).group({
    fee: [null as number | null, [Validators.required, Validators.min(1), Validators.max(9999.99)]],
  });
  readonly saving = signal(false);
  readonly saved = signal(false);
  readonly error = signal<string | null>(null);
  readonly settings = rxResource({
    params: () => this.slug(),
    stream: ({ params }) => this.api.getDuesSettings(params),
  });

  save(formDirective: FormGroupDirective): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.update(this.form.getRawValue().fee, formDirective);
  }

  stop(formDirective: FormGroupDirective): void {
    this.update(null, formDirective);
  }

  private update(fee: number | null, formDirective: FormGroupDirective): void {
    this.saving.set(true);
    this.saved.set(false);
    this.error.set(null);
    this.api.setDuesFee(this.slug(), fee === null ? {} : { annualFee: fee }).subscribe({
      next: () => {
        this.saving.set(false);
        this.saved.set(true);
        formDirective.resetForm(); // also clears "submitted", so the empty field shows no error
        this.settings.reload();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(errorMessageKey(err));
      },
    });
  }
}
