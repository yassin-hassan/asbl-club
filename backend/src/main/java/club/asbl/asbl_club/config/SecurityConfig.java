package club.asbl.asbl_club.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter, ProblemResponses problems) throws Exception {
        AuthenticationEntryPoint notAuthenticated = problemEntryPoint(problems);
        AccessDeniedHandler notAllowed = problemAccessDeniedHandler(problems);
        http
                .securityMatcher("/api/**")
                .cors(cors -> cors.configurationSource(apiCorsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public reads, listed one by one: anything else under /api needs a login (deny by default).
                        .requestMatchers(HttpMethod.GET, "/api/v1/asbls/*", "/api/v1/asbls/*/events",
                                "/api/v1/events/*", "/api/v1/events/*/availability").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh", "/api/v1/auth/logout")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("SUPERADMIN")
                        .anyRequest().authenticated())
                // Reads "Authorization: Bearer <jwt>" and verifies it with the JwtDecoder bean (JwtConfig),
                // then maps its "roles" claim to authorities.
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(notAuthenticated)
                        .accessDeniedHandler(notAllowed)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                // 401/403 decided by the security layer get the same Problem Details body as controller errors.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(notAuthenticated)
                        .accessDeniedHandler(notAllowed));
        return http.build();
    }

    // Keeps the standard Bearer behaviour (status + WWW-Authenticate header), then adds the error body.
    private static AuthenticationEntryPoint problemEntryPoint(ProblemResponses problems) {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (request, response, exception) -> {
            bearer.commence(request, response, exception);
            problems.write(request, response, HttpStatus.UNAUTHORIZED, "Authentication is required.");
        };
    }

    private static AccessDeniedHandler problemAccessDeniedHandler(ProblemResponses problems) {
        BearerTokenAccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
        return (request, response, exception) -> {
            bearer.handle(request, response, exception);
            problems.write(request, response, HttpStatus.FORBIDDEN, "You are not allowed to do this.");
        };
    }

    private CorsConfigurationSource apiCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(List.of("GET"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    // Everything outside /api: no pages any more (the Angular site is served by the CDN), only a few machine
    // endpoints. Stateless like the API: no login form, no session, so no CSRF token to protect (webhooks prove
    // themselves with Stripe's signature). Deny by default: anything not listed is refused.
    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/webhooks/stripe").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**",
                                "/.well-known/jwks.json", "/events/rss", "/asbls/*/events/rss").permitAll()
                        // Off unless API_DOCS_ENABLED=true (application.yaml); when on, readable like the API itself.
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    // Checks passwords for the JSON login endpoint (users from CustomUserDetailsService, Argon2 hashes).
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
