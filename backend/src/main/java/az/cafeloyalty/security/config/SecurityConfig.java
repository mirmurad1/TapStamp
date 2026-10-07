package az.cafeloyalty.security.config;

import java.time.Clock;

import az.cafeloyalty.config.AppProperties;
import az.cafeloyalty.customer.auth.CustomerCookies;
import az.cafeloyalty.customer.auth.CustomerPrincipal;
import az.cafeloyalty.customer.auth.CustomerSessionFilter;
import az.cafeloyalty.customer.auth.CustomerSessionService;
import az.cafeloyalty.security.admin.AdminAuthProperties;
import az.cafeloyalty.security.admin.AdminAuthentication;
import az.cafeloyalty.security.admin.AdminRealm;
import az.cafeloyalty.security.admin.AdminRealms;
import az.cafeloyalty.security.admin.AdminSessionCookies;
import az.cafeloyalty.security.admin.AdminSessionFilter;
import az.cafeloyalty.security.csrf.SpaCsrfTokenRequestHandler;
import az.cafeloyalty.security.web.OriginCheckFilter;
import az.cafeloyalty.security.web.ProblemSecurityHandlers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
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
    private static final String STAFF = AdminRealm.STAFF.sessionAuthority();
    private static final String PLATFORM = AdminRealm.PLATFORM.sessionAuthority();
    private static final String CUSTOMER = CustomerPrincipal.AUTHORITY;

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
            HttpSecurity http, AppProperties properties, ProblemSecurityHandlers problemHandlers, AdminRealms realms,
            AdminSessionCookies sessionCookies, AdminAuthProperties authProperties, Clock clock,
            CustomerSessionService customerSessions, CustomerCookies customerCookies) throws Exception {
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
                .addFilterBefore(new AdminSessionFilter(realms, sessionCookies, authProperties, clock),
                        AnonymousAuthenticationFilter.class)
                .addFilterBefore(new CustomerSessionFilter(customerSessions, customerCookies),
                        AnonymousAuthenticationFilter.class)
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
                        // Daxili TLS domen siyahısı: nginx xaricə açmır, endpoint özü sirr token tələb edir
                        .requestMatchers(HttpMethod.GET, "/internal/tls/domains").permitAll()
                        .requestMatchers("/api/v1/csrf", "/api/v1/context", "/api/v1/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/manifest.webmanifest").permitAll()
                        // Admin girişi (işçi və platforma). Domen yoxlaması TenantResolutionFilter-dədir.
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/auth/session", "/api/v1/platform/auth/session").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/admin/auth/login", "/api/v1/admin/auth/logout",
                                "/api/v1/admin/auth/password/forgot", "/api/v1/admin/auth/password/reset",
                                "/api/v1/admin/auth/invitations/inspect", "/api/v1/admin/auth/invitations/accept",
                                "/api/v1/platform/auth/login", "/api/v1/platform/auth/logout",
                                "/api/v1/platform/auth/password/forgot", "/api/v1/platform/auth/password/reset").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/mfa/verify", "/api/v1/platform/auth/mfa/verify")
                        .hasAuthority(AdminAuthentication.MFA_PENDING)
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/mfa/enrollment", "/api/v1/admin/auth/mfa/enrollment/confirm")
                        .hasAnyAuthority(AdminAuthentication.MFA_ENROLLMENT, STAFF)
                        .requestMatchers(HttpMethod.POST, "/api/v1/platform/auth/mfa/enrollment", "/api/v1/platform/auth/mfa/enrollment/confirm")
                        .hasAnyAuthority(AdminAuthentication.MFA_ENROLLMENT, PLATFORM)
                        // Müştəri girişi (yalnız şirkət domenində)
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/session").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/otp/request", "/api/v1/auth/otp/verify",
                                "/api/v1/auth/register", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/consents", "/api/v1/auth/step-up/otp/request",
                                "/api/v1/auth/step-up/otp/verify").hasAuthority(CUSTOMER)
                        .requestMatchers("/api/v1/me", "/api/v1/me/**").hasAuthority(CUSTOMER)
                        // NFC toxunuşu (brauzer naviqasiyası) və toxunuşdan sonrakı səhifə
                        .requestMatchers(HttpMethod.GET, "/t/*").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/t/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/taps/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/taps/*/claim").hasAuthority(CUSTOMER)
                        .requestMatchers("/api/v1/admin/**").hasAuthority(STAFF)
                        .requestMatchers("/api/v1/platform/**").hasAuthority(PLATFORM)
                        // Dev alətləri (outbox): controller-lər yalnız dev profilində mövcuddur, prod-da simulyator söndürülüb
                        // Simulyator imzalı toxunuş yaradır: yalnız dev-də və yalnız daxil olmuş işçi üçün
                        .requestMatchers("/api/v1/dev/simulator/**").access((authentication, context) ->
                                new AuthorizationDecision(properties.dev().simulatorEnabled()
                                        && authentication.get().getAuthorities().stream()
                                        .anyMatch(authority -> STAFF.equals(authority.getAuthority()))))
                        .requestMatchers("/api/v1/dev/**").access((authentication, context) ->
                                new AuthorizationDecision(properties.dev().simulatorEnabled()))
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
     * filtrlərimizlə aparılır ({@link AdminSessionFilter}); AuthenticationManager istifadə olunmur.
     */
    @Bean
    AuthenticationManager noopAuthenticationManager() {
        return authentication -> {
            throw new ProviderNotFoundException("Username/password authentication is not used");
        };
    }
}
