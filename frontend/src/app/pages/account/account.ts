import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth';

@Component({
  selector: 'app-account',
  imports: [RouterLink],
  templateUrl: './account.html',
})
export class Account {
  readonly user = inject(AuthService).user;
}
