import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink, TranslocoPipe, MatButtonModule],
  template: `
    <article class="stack">
      <h1>{{ 'error.404.title' | transloco }}</h1>
      <p>{{ 'error.404.body' | transloco }}</p>
      <a mat-flat-button routerLink="/">{{ 'events.back' | transloco }}</a>
    </article>
  `,
})
export class NotFound {}
