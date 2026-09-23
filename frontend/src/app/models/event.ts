// TypeScript mirrors of the backend's Java records.
// Keep these in sync with the DTOs the JSON API returns.

// Java: record EventFeedItem(Long id, String title, String description, Instant startsAt)
export interface EventFeedItem {
  id: number;
  title: string;
  description: string;
  startsAt: string; // ISO-8601 string (Jackson serializes Instant this way)
}

// Java: record SeatAvailability(Long categoryId, int remaining)
export interface SeatAvailability {
  categoryId: number;
  remaining: number;
}

// Java: record AsblResource(String slug, String denomination)
export interface AsblResource {
  slug: string;
  denomination: string;
}
