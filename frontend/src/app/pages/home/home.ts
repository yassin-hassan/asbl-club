import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  templateUrl: './home.html',
})
export class Home {
  // Defaults to the seeded demo association (DemoDataSeeder -> "club-demo").
  // A signal is Angular's reactive value; the template re-renders when it changes.
  readonly slug = signal('club-demo');
}
