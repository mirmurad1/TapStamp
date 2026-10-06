package az.cafeloyalty.security.config;

import az.cafeloyalty.config.AppProperties;
import az.cafeloyalty.security.csrf.SpaCsrfTokenRequestHandler;
import az.cafeloyalty.security.web.OriginCheckFilter;
import az.cafeloyalty.security.web.ProblemSecurityHandlers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

/**
 * Əsas təhlükəsizlik konfiqurasiyası. Prinsip: hər şey standart olaraq qadağandır ({@code denyAll}),
 * açıq yollar ayrıca və açıq şəkildə sadalanır.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    public static final String CSRF_COOKIE = "__Host-XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    /** API cavabları üçün CSP: JSON cavablarında heç bir resurs yüklənməməlidir. SPA-nın CSP-si nginx-dədir. */
    static final String API_CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

    /** Swagger UI və OpenAPI sənədi — yalnız springdoc aktiv olanda (dev profili). */
    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
    SecurityFilterChain apiDocsSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/swagger-ui/**", "/api/v1/docs", "/api/v1/docs/**", "/api/v1/openapi.json", "/api/v1/openapi.json/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    @Order(10)
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http, AppProperties properties, ProblemSecurityHandlers problemHandlers) throws Exception {
        boolean secureCookies = properties.security().cookieSecure();

        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieName(CSRF_COOKIE);
        csrfRepository.setHeaderName(CSRF_HEADER);
        csrfRepository.setCookiePath("/");
        csrfRepository.setCookieCustomizer(cookie -> cookie.secure(secureCookies).sameSite("Lax"));

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterBefore(new OriginCheckFilter(problemHandlers), CsrfFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(headers -> configureHeaders(headers, properties))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/csrf", "/api/v1/public/**").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    private static void configureHeaders(HeadersConfigurer<HttpSecurity> headers, AppProperties properties) {
        headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(API_CSP))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY))
                .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin"))
                .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Resource-Policy", "same-origin"));
        if (properties.security().hstsEnabled()) {
            headers.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(false).maxAgeInSeconds(63_072_000));
        } else {
            headers.httpStrictTransportSecurity(HeadersConfigurer.HstsConfig::disable);
        }
    }

    /**
     * Spring Boot-un standart istifadəçi/şifrə yaratmasının qarşısını alır. Autentifikasiya öz sessiya
     * filtrlərimizlə aparılır (Mərhələ 3–4); AuthenticationManager istifadə olunmur.
     */
    @Bean
    AuthenticationManager noopAuthenticationManager() {
        return authentication -> {
            throw new ProviderNotFoundException("Username/password authentication is not used");
        };
    }
}
