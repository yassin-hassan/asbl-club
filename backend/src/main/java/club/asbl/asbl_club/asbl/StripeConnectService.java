package club.asbl.asbl_club.asbl;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StripeConnectService {

    private final StripeClient stripe;
    private final AsblRepository asblRepository;

    StripeConnectService(StripeClient stripe, AsblRepository asblRepository) {
        this.stripe = stripe;
        this.asblRepository = asblRepository;
    }

    @Transactional
    public String startOnboarding(Asbl asbl, String refreshUrl, String returnUrl) throws StripeException {
        if (asbl.getStripeAccountId() == null) {
            Account account = stripe.accounts().create(AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS)
                    .setCountry("BE")
                    .setBusinessType(AccountCreateParams.BusinessType.NON_PROFIT)
                    .setCapabilities(AccountCreateParams.Capabilities.builder()
                            .setCardPayments(AccountCreateParams.Capabilities.CardPayments.builder()
                                    .setRequested(true)
                                    .build())
                            .setTransfers(AccountCreateParams.Capabilities.Transfers.builder()
                                    .setRequested(true)
                                    .build())
                            .build())
                    .build());
            asbl.setStripeAccountId(account.getId());
            asblRepository.save(asbl);
        }

        AccountLink link = stripe.accountLinks().create(AccountLinkCreateParams.builder()
                .setAccount(asbl.getStripeAccountId())
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .setRefreshUrl(refreshUrl)
                .setReturnUrl(returnUrl)
                .build());
        return link.getUrl();
    }

    // Asked of Stripe each time: Stripe decides when an account may take payments (identity checks, bank
    // details...), and that can change at any moment on its side.
    @Transactional(readOnly = true)
    public ConnectStatus status(Asbl asbl) throws StripeException {
        if (asbl.getStripeAccountId() == null) {
            return ConnectStatus.NOT_CONNECTED;
        }
        Account account = stripe.accounts().retrieve(asbl.getStripeAccountId());
        return Boolean.TRUE.equals(account.getChargesEnabled()) ? ConnectStatus.READY : ConnectStatus.PENDING;
    }

    public enum ConnectStatus {
        NOT_CONNECTED, // no Stripe account yet
        PENDING,       // account created, onboarding unfinished or under review at Stripe
        READY          // Stripe accepts card payments for this association
    }
}
