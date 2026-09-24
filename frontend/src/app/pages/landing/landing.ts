import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { EventSearch } from '../../components/event-search/event-search';

@Component({
  selector: 'app-landing',
  imports: [RouterLink, TranslocoPipe, MatButtonModule, MatCardModule, MatIconModule, EventSearch],
  templateUrl: './landing.html',
  styleUrl: './landing.css',
})
export class Landing {
  readonly features = [
    { key: 'feature1', icon: 'groups' },
    { key: 'feature2', icon: 'event' },
    { key: 'feature3', icon: 'volunteer_activism' },
    { key: 'feature4', icon: 'verified_user' },
  ];
  readonly steps = ['step1', 'step2', 'step3'];
}
