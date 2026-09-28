import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-landing',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslocoPipe, MatButtonModule, MatIconModule],
  templateUrl: './landing.html',
  styleUrl: './landing.css',
})
export class Landing {
  readonly features = [
    { key: 'feature1', icon: 'group' },
    { key: 'feature2', icon: 'calendar_month' },
    { key: 'feature3', icon: 'credit_card' },
    { key: 'feature4', icon: 'verified_user' },
  ];
  readonly steps = ['step1', 'step2', 'step3'];

  // Illustration only (the hero's sample dashboard, as in the design mockup) — not real data.
  readonly sampleFees = [
    { initials: 'ML', name: 'Marie Laurent', colour: 'var(--asbl-indigo-600)', amount: '25,00 €' },
    { initials: 'TD', name: 'Thomas De Vries', colour: 'var(--asbl-amber-500)', amount: null },
    { initials: 'SK', name: 'Sofie Klein', colour: 'var(--asbl-green-700)', amount: '25,00 €' },
    { initials: 'AB', name: 'Ahmed Bensaïd', colour: 'var(--asbl-indigo-900)', amount: null },
  ];
}
