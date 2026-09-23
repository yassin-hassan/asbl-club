import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { EventList } from './pages/event-list/event-list';
import { EventDetail } from './pages/event-detail/event-detail';

export const routes: Routes = [
  { path: '', component: Home },
  { path: 'asbls/:slug/events', component: EventList },
  { path: 'events/:id', component: EventDetail },
];
