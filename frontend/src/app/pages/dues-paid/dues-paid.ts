import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { map, switchMap, take, takeWhile, timer } from 'rxjs';
import { DuesService } from '../../api/generated';
import { errorMessageKey } from '../../services/problem';

type Outcome = 'checking' | 'paid' | 'processing' | 'failed';

// Where Stripe sends the browser after paying dues. As for tickets, the redirect only suggests the outcome; the
// truth is on the server, once Stripe's signed webhook has arrived: ask every 2 s, for up to 30 s.
@Component({
  selector: 'app-dues-paid',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './dues-paid.html',
})
export class DuesPaid {
  private route = inject(ActivatedRoute);
  readonly slug = this.route.snapshot.paramMap.get('slug') ?? '';
  readonly outcome = signal<Outcome>('checking');
  readonly error = signal<string | null>(null);

  constructor() {
    if (this.route.snapshot.queryParamMap.get('redirect_status') === 'failed') {
      this.outcome.set('failed');
      return;
    }
    const api = inject(DuesService);
    timer(0, 2000).pipe(
      take(15),
      switchMap(() => api.listMyDues()),
      map((dues) => dues.find((due) => due.slug === this.slug)?.paid ?? false),
      takeWhile((paid) => !paid, true),
    ).subscribe({
      next: (paid) => this.outcome.set(paid ? 'paid' : 'checking'),
      error: (err) => this.error.set(errorMessageKey(err)),
      complete: () => {
        if (this.outcome() === 'checking') {
          this.outcome.set('processing'); // Stripe hasn't confirmed yet after 30 s
        }
      },
    });
  }
}
