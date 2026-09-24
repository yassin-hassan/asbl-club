import { Component, inject } from '@angular/core';
import { AuthService } from '../../services/auth';

@Component({
  selector: 'app-account',
  templateUrl: './account.html',
})
export class Account {
  readonly user = inject(AuthService).user;
}
