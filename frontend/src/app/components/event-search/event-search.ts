import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'app-event-search',
  imports: [RouterLink, TranslocoPipe, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule],
  templateUrl: './event-search.html',
  styles: '.row { display: flex; gap: 16px; align-items: baseline; } .grow { flex: 1; }',
})
// Find an association's public events by its short name (used on the landing page).
export class EventSearch {
  // Defaults to the seeded demo association (DemoDataSeeder -> "club-demo").
  readonly slug = signal('club-demo');
}
