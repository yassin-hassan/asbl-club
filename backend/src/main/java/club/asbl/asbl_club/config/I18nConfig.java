package club.asbl.asbl_club.config;

import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class I18nConfig {

    private static final List<Locale> SUPPORTED = List.of(Locale.FRENCH, Locale.of("nl"), Locale.ENGLISH);

    /**
     * The API is stateless (no session), so the language comes with every request in the Accept-Language header,
     * which the Angular app sets from the user's choice. French when nothing (supported) is asked for.
     * It picks the language of server messages such as validation errors.
     */
    @Bean
    LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(SUPPORTED);
        resolver.setDefaultLocale(Locale.FRENCH);
        return resolver;
    }
}
