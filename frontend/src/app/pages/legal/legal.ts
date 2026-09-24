import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';

// One component for the three legal pages; the route says which one (notice, privacy, cookies).
@Component({
  selector: 'app-legal',
  imports: [RouterLink, TranslocoPipe, MatButtonModule, MatIconModule],
  template: `
    <article class="page stack">
      <h1>{{ 'legal.' + page + '.title' | transloco }}</h1>
      <p>{{ 'legal.' + page + '.body' | transloco }}</p>
      <a mat-button routerLink="/"><mat-icon>arrow_back</mat-icon> {{ 'events.back' | transloco }}</a>
    </article>
  `,
})
export class Legal {
  readonly page: string = inject(ActivatedRoute).snapshot.data['page'];
}
