package club.asbl.asbl_club.payment;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "asbl_id", nullable = false)
    private Asbl asbl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "payer_name", nullable = false)
    private String payerName;

    @Column(name = "payer_email", nullable = false)
    private String payerEmail;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "payable_id", nullable = false, unique = true)
    private Payable payable;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal commission;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected Payment() {
    }

    public Long getId() {
        return id;
    }

    public Asbl getAsbl() {
        return asbl;
    }

    public void setAsbl(Asbl asbl) {
        this.asbl = asbl;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getPayerName() {
        return payerName;
    }

    public void setPayerName(String payerName) {
        this.payerName = payerName;
    }

    public String getPayerEmail() {
        return payerEmail;
    }

    public void setPayerEmail(String payerEmail) {
        this.payerEmail = payerEmail;
    }

    public Payable getPayable() {
        return payable;
    }

    public void setPayable(Payable payable) {
        this.payable = payable;
    }

    public String getStripePaymentIntentId() {
        return stripePaymentIntentId;
    }

    public void setStripePaymentIntentId(String stripePaymentIntentId) {
        this.stripePaymentIntentId = stripePaymentIntentId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }
}
