package com.example.sso.config;

import com.example.sso.filter.LegacySessionHandoverFilter;
import com.example.sso.web.AppAwareAuthenticationEntryPoint;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(SsoProperties.class)
/**
 * Central Spring Security configuration for the SSO connector.
 *
 * <p>This class keeps the connector's security rules in one place so it is
 * clear which routes stay public, where OAuth login lands after a successful
 * identity-provider round trip, and how logout returns control to the calling
 * legacy application.
 */
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * Connector settings loaded from application configuration.
     *
     * <p>These properties let operations adjust public paths and other
     * connector behavior per environment without changing code.
     */
    private final SsoProperties props;
    private final ClientRegistrationRepository clientRegistrationRepository;

    public SecurityConfig(SsoProperties props,
                          ClientRegistrationRepository clientRegistrationRepository) {
        this.props = props;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    /**
     * Builds the connector's Spring Security filter chain.
     *
     * <p>The connector keeps a small public surface for health checks and the
     * tenant-discovery login entry point, requires authentication everywhere
     * else, sends successful OAuth logins to the post-auth landing page, and
     * runs the legacy handover filter only after Spring Security has created an
     * authenticated principal.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   LegacySessionHandoverFilter handoverFilter,
                                                   AppAwareAuthenticationEntryPoint entryPoint) throws Exception {
        // Start with connector-owned public endpoints, then allow operators to
        // append environment-specific paths from configuration.
        List<String> publicPaths = new ArrayList<>(List.of(
            "/",
            "/health",
            "/error",
            "/sso/login",
            "/apps/*/sso/login",
            // The /sso/logout intermediate page renders a CSRF-protected POST
            // form that targets the Spring Security logout endpoint. It must
            // be reachable even when the connector session has already been
            // invalidated so that legacy apps can complete the logout flow.
            "/sso/logout"));
        if (props.getPublicPaths() != null) {
            publicPaths.addAll(props.getPublicPaths());
        }

        http
            .authorizeHttpRequests(auth -> auth
            .requestMatchers(publicPaths.toArray(new String[0])).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth -> oauth
                // alwaysUse=false: if a saved request exists (user was redirected
                // here from a protected page) Spring Security replays it directly
                // instead of always landing on the success page.
                .defaultSuccessUrl("/sso/success", false)
                .authorizationEndpoint(authorization -> authorization
                    .authorizationRequestResolver(authorizationRequestResolver())
                )
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(entryPoint)
            )
            .logout(logout -> logout
                // Stay on Spring Security's default POST /logout with CSRF
                // protection. Legacy apps reach this endpoint via the
                // connector-hosted /sso/logout intermediate page, which posts
                // the CSRF token issued for the current session.
                .logoutSuccessHandler(logoutSuccessHandler())
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            // Place handover after auth so we have an authenticated principal
            .addFilterAfter(handoverFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    OAuth2AuthorizationRequestResolver authorizationRequestResolver() {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository, "/oauth2/authorization");
        SsoProperties.Login login = props.getLogin() == null ? new SsoProperties.Login() : props.getLogin();
        String prompt = login.getPrompt();
        if (prompt != null && !prompt.isBlank()) {
            resolver.setAuthorizationRequestCustomizer(builder ->
                    builder.additionalParameters(params -> params.put("prompt", prompt)));
        }
        return resolver;
    }

    LogoutSuccessHandler logoutSuccessHandler() {
        SsoProperties.Logout logout = props.getLogout() == null ? new SsoProperties.Logout() : props.getLogout();
        log.info("[logout] configuring logout success handler: singleLogoutEnabled={}, postLogoutRedirectUri={}",
                logout.isSingleLogoutEnabled(), logout.getPostLogoutRedirectUri());
        if (logout.isSingleLogoutEnabled()) {
            return this::handleSingleLogoutSuccess;
        }
        return this::handleLocalLogoutSuccess;
    }

    /**
     * Performs OIDC RP-initiated logout at the identity provider, then returns
     * the browser to the application the user logged out from.
     *
     * <p>The {@code returnTo} value (sent by the legacy app, e.g. its login
     * page) is validated against the configured legacy app URLs and used as the
     * IdP {@code post_logout_redirect_uri} so each app/tenant lands back on its
     * own page. When {@code returnTo} is missing or untrusted, the configured
     * {@code post-logout-redirect-uri} is used as a fallback.
     *
     * <p>Note: the resolved URI must also be registered as an allowed
     * post-logout/redirect URI with the identity provider.
     */
    private void handleSingleLogoutSuccess(jakarta.servlet.http.HttpServletRequest request,
                                           jakarta.servlet.http.HttpServletResponse response,
                                           Authentication authentication)
            throws java.io.IOException, jakarta.servlet.ServletException {
        String returnTo = request.getParameter("returnTo");
        boolean safe = isSafeReturnTo(returnTo);
        String target = safe ? returnTo : props.getLogout().getPostLogoutRedirectUri();

        ClientRegistration registration = resolveRegistration(authentication);
        String endSession = endSessionEndpoint(registration);
        log.info("[logout] single logout: method={}, returnTo='{}', safe={}, target='{}', registrationId={}, endSessionEndpoint={}",
                request.getMethod(), returnTo, safe, target,
                registration == null ? null : registration.getRegistrationId(), endSession);

        if (endSession != null && !endSession.isBlank()) {
            // Standard OIDC RP-initiated logout (e.g. Azure Entra ID).
            OidcClientInitiatedLogoutSuccessHandler handler =
                    new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
            handler.setPostLogoutRedirectUri(target);
            handler.onLogoutSuccess(request, response, authentication);
            log.info("[logout] single logout: OIDC end-session redirect, Location='{}'",
                    response.getHeader("Location"));
            return;
        }

        // Provider without a discoverable end_session_endpoint (e.g. Auth0).
        String providerLogoutUrl = buildProviderLogoutUrl(registration, target);
        if (providerLogoutUrl != null) {
            log.info("[logout] single logout: provider-specific logout redirect, Location='{}'", providerLogoutUrl);
            response.sendRedirect(providerLogoutUrl);
            return;
        }

        // No way to reach the IdP; fall back to a local redirect so the user
        // at least lands back on the application.
        log.warn("[logout] single logout: no end-session endpoint or provider logout URL available, "
                + "falling back to local redirect target='{}'", target);
        response.sendRedirect(target);
    }

    private ClientRegistration resolveRegistration(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken token) {
            return clientRegistrationRepository.findByRegistrationId(token.getAuthorizedClientRegistrationId());
        }
        return null;
    }

    private String endSessionEndpoint(ClientRegistration registration) {
        if (registration == null) {
            return null;
        }
        Map<String, Object> metadata = registration.getProviderDetails().getConfigurationMetadata();
        Object value = metadata == null ? null : metadata.get("end_session_endpoint");
        return value == null ? null : value.toString();
    }

    /**
     * Builds a provider-specific logout URL for identity providers that do not
     * advertise an OIDC {@code end_session_endpoint}. Currently supports Auth0,
     * which uses {@code {issuer}v2/logout?client_id=...&returnTo=...}.
     *
     * <p>The {@code returnTo} value must be registered in the provider's allowed
     * logout URLs (Auth0: Application &gt; Allowed Logout URLs).
     */
    private String buildProviderLogoutUrl(ClientRegistration registration, String target) {
        if (registration == null) {
            return null;
        }
        String issuer = registration.getProviderDetails().getIssuerUri();
        if (issuer == null || issuer.isBlank()) {
            return null;
        }
        if (issuer.contains("auth0.com")) {
            String base = issuer.endsWith("/") ? issuer : issuer + "/";
            return base + "v2/logout"
                    + "?client_id=" + URLEncoder.encode(registration.getClientId(), StandardCharsets.UTF_8)
                    + "&returnTo=" + URLEncoder.encode(target, StandardCharsets.UTF_8);
        }
        return null;
    }

    private void handleLocalLogoutSuccess(jakarta.servlet.http.HttpServletRequest request,
                                          jakarta.servlet.http.HttpServletResponse response,
                                          Authentication authentication) throws java.io.IOException {
        String returnTo = request.getParameter("returnTo");
        boolean safe = isSafeReturnTo(returnTo);
        log.info("[logout] local logout: method={}, returnTo='{}', safe={}",
                request.getMethod(), returnTo, safe);
        if (safe) {
            response.sendRedirect(returnTo);
            return;
        }
        response.sendRedirect("/");
    }

    private boolean isSafeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return false;
        }
        // Same-origin path (e.g. "/sso/login")
        if (returnTo.startsWith("/") && !returnTo.startsWith("//")) {
            return true;
        }
        // Absolute URL targeting a configured legacy app (e.g. the legacy
        // login page) is also allowed so users land back on the app they
        // logged out from.
        for (var app : props.getApps().values()) {
            String base = app.getLegacyAppUrl();
            if (base != null && !base.isBlank() && returnTo.startsWith(base)) {
                return true;
            }
        }
        String topLevel = props.getLegacyAppUrl();
        if (topLevel != null && !topLevel.isBlank() && returnTo.startsWith(topLevel)) {
            return true;
        }
        return false;
    }
}
