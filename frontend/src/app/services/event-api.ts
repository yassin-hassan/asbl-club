import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { EventFeedItem, SeatAvailability } from '../models/event';

// A service is where HTTP lives. Components inject it and stay thin.
// Paths are relative — the dev proxy (proxy.conf.json) forwards them to Spring.
@Injectable({ providedIn: 'root' })
export class EventApiService {
  private http = inject(HttpClient);

  listEvents(slug: string): Observable<EventFeedItem[]> {
    return this.http.get<EventFeedItem[]>(`/api/v1/asbls/${slug}/events`);
  }

  getEvent(eventId: number): Observable<EventFeedItem> {
    return this.http.get<EventFeedItem>(`/api/v1/events/${eventId}`);
  }

  availability(eventId: number): Observable<SeatAvailability[]> {
    // Note: this endpoint lives outside /api/v1 in the backend.
    return this.http.get<SeatAvailability[]>(`/events/${eventId}/availability`);
  }
}
