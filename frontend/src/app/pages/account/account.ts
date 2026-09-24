import { Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { AuthService } from '../../services/auth';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-account',
  imports: [TranslocoPipe, MatCardModule, MatListModule, MatChipsModule, MatIconModule],
  templateUrl: './account.html',
})
export class Account {
  readonly user = inject(AuthService).user;
}
