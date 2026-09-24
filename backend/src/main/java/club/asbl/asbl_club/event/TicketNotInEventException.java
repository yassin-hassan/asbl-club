package club.asbl.asbl_club.event;

// The ticket category doesn't exist, or belongs to a different event than the one being booked.
public class TicketNotInEventException extends RuntimeException {

    public TicketNotInEventException(Long ticketCategoryId) {
        super("Ticket category " + ticketCategoryId + " is not part of this event");
    }
}
