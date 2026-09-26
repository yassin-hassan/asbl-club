package club.asbl.asbl_club.event;

// Published when an event is cancelled. Other parts react on their own terms (the bookings not yet paid are
// cancelled too) without the event code knowing about them. Listeners run inside the cancelling transaction.
public record EventCancelled(Long eventId) {
}
