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

    @Transactional(readOnly = true)
    public boolean isReady(Asbl asbl) throws StripeException {
        if (asbl.getStripeAccountId() == null) {
            return false;
        }
        Account account = stripe.accounts().retrieve(asbl.getStripeAccountId());
        return Boolean.TRUE.equals(account.getChargesEnabled());
    }
}
