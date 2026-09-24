import { Routes } from '@angular/router';
import { authGuard } from './services/auth.guard';

// Every page is lazy-loaded: its code (and the Material components it uses) is only downloaded the first
// time someone opens it, so the initial download stays small as more pages are migrated.
// Paths match the server-rendered pages they replace, so switching a path from Thymeleaf to Angular is
// only a routing change in front of the app.
const legal = () => import('./pages/legal/legal').then((m) => m.Legal);

export const routes: Routes = [
  { path: '', loadComponent: () => import('./pages/landing/landing').then((m) => m.Landing) },
  {
    path: 'asbls/:slug/events',
    loadComponent: () => import('./pages/event-list/event-list').then((m) => m.EventList),
  },
  {
    path: 'events/:id',
    loadComponent: () => import('./pages/event-detail/event-detail').then((m) => m.EventDetail),
  },
  { path: 'login', loadComponent: () => import('./pages/login/login').then((m) => m.Login) },
  {
    path: 'account',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/account/account').then((m) => m.Account),
  },
  { path: 'legal', loadComponent: legal, data: { page: 'notice' } },
  { path: 'privacy', loadComponent: legal, data: { page: 'privacy' } },
  { path: 'cookies', loadComponent: legal, data: { page: 'cookies' } },
  { path: '**', loadComponent: () => import('./pages/not-found/not-found').then((m) => m.NotFound) },
];
