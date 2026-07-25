package club.asbl.asbl_club.event;

public class TicketSoldOutException extends RuntimeException {

    public TicketSoldOutException(Long ticketCategoryId) {
        super("Ticket category " + ticketCategoryId + " is sold out");
    }
}
