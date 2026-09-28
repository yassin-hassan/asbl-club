package club.asbl.asbl_club.dues;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.payment.Payable;
import club.asbl.asbl_club.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

// A member's dues for one year: something to pay, like a booking, so it goes through the same payment flow
// (Stripe, webhook, commission, audit). Whether it's paid is its payment's status, not a copy kept here.
@Entity
@Table(name = "dues")
@DiscriminatorValue("MEMBERSHIP")
public class Due extends Payable {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "asbl_id", nullable = false)
    private Asbl asbl;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int year;

    protected Due() {
    }

    Due(Asbl asbl, User user, int year, BigDecimal amount) {
        this.asbl = asbl;
        this.user = user;
        this.year = year;
        setAmount(amount);
        setCurrency("EUR");
    }

    public Asbl getAsbl() {
        return asbl;
    }

    public User getUser() {
        return user;
    }

    public int getYear() {
        return year;
    }
}
