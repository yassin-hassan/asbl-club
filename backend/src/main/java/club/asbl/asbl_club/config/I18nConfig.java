package club.asbl.asbl_club.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import org.springframework.web.util.UrlPathHelper;

@Configuration
public class I18nConfig implements WebMvcConfigurer {

    private static final List<Locale> SUPPORTED = List.of(Locale.FRENCH, Locale.of("nl"), Locale.ENGLISH);

    /**
     * Server-rendered pages: the language is chosen with ?lang= and kept in the session.
     * JSON API: stateless (no session), so the language comes with every request in the Accept-Language
     * header, which the Angular app sets. French when nothing (supported) is asked for.
     */
    @Bean
    LocaleResolver localeResolver() {
        SessionLocaleResolver pages = new SessionLocaleResolver();
        pages.setDefaultLocale(Locale.FRENCH);
        AcceptHeaderLocaleResolver api = new AcceptHeaderLocaleResolver();
        api.setSupportedLocales(SUPPORTED);
        api.setDefaultLocale(Locale.FRENCH);
        return new LocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                return isApi(request) ? api.resolveLocale(request) : pages.resolveLocale(request);
            }

            @Override
            public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
                if (!isApi(request)) {
                    pages.setLocale(request, response, locale);
                }
            }
        };
    }

    @Bean
    LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // ?lang= only means something for the session-based pages.
        registry.addInterceptor(localeChangeInterceptor()).excludePathPatterns("/api/**");
        registry.addInterceptor(new CurrentPathInterceptor());
    }

    private static boolean isApi(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request).startsWith("/api/");
    }
}
