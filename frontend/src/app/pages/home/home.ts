import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'app-home',
  imports: [RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule],
  templateUrl: './home.html',
  styles: '.row { display: flex; gap: 16px; align-items: baseline; } .grow { flex: 1; }',
})
export class Home {
  // Defaults to the seeded demo association (DemoDataSeeder -> "club-demo").
  readonly slug = signal('club-demo');
}
