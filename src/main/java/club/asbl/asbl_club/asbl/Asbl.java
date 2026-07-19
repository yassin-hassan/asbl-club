package club.asbl.asbl_club.asbl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "asbls")
public class Asbl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String denomination;

    @Column(name = "bce_number", nullable = false, unique = true, length = 12)
    private String bceNumber;

    @Column(name = "ubo_number", length = 20)
    private String uboNumber;

    @Column(length = 255)
    private String address;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "default_language", nullable = false, length = 5)
    private String defaultLanguage;

    @Column(nullable = false, length = 20)
    private String status;

    protected Asbl() {
    }

    public Long getId() {
        return id;
    }

    public String getDenomination() {
        return denomination;
    }

    public void setDenomination(String denomination) {
        this.denomination = denomination;
    }

    public String getBceNumber() {
        return bceNumber;
    }

    public void setBceNumber(String bceNumber) {
        this.bceNumber = bceNumber;
    }

    public String getUboNumber() {
        return uboNumber;
    }

    public void setUboNumber(String uboNumber) {
        this.uboNumber = uboNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
