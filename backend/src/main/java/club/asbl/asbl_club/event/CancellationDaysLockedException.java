package club.asbl.asbl_club.event;

// Once a ticket is sold, the cancellation delay can only grow: buyers paid under the policy shown to them, and it
// can't be taken back from them.
public class CancellationDaysLockedException extends RuntimeException {
}
