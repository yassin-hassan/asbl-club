package club.asbl.asbl_club.event;

// Only drafts and published events can be changed; cancelled and past events stay as they were (and only drafts can be published or deleted, only published events cancelled).
public class EventNotEditableException extends RuntimeException {
}
