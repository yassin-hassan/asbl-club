import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'app-event-search',
  imports: [RouterLink, TranslocoPipe, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule],
  templateUrl: './event-search.html',
  styles: '.search { display: flex; gap: 16px; align-items: flex-start; background: #fff; border: 1px solid var(--asbl-slate-200); border-radius: 16px; padding: 20px; } .grow { flex: 1; } .search a { margin-top: 4px; height: 48px; }',
})
// Find an association's public events by its short name (used on the landing page).
export class EventSearch {
  // Defaults to the seeded demo association (DemoDataSeeder -> "club-demo").
  readonly slug = signal('club-demo');
}
