package com.example.sso.filter;

import com.example.sso.config.SsoProperties;
import com.example.sso.support.OidcIdentityResolver;
import com.example.sso.support.ResolvedOidcIdentity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Pulls the authenticated user from the OIDC principal and pushes it into the
 * HttpSession + request attributes so legacy applications can read identity
 * without OIDC awareness.
 */
@Component
public class LegacySessionHandoverFilter extends OncePerRequestFilter {

    public static final String SESSION_USER_KEY = "SSO_USER";
    public static final String SESSION_EMAIL_KEY = "SSO_EMAIL";
    public static final String SESSION_NAME_KEY = "SSO_NAME";
    public static final String SESSION_ROLES_KEY = "SSO_ROLES";
    public static final String SESSION_ID_TOKEN_KEY = "SSO_ID_TOKEN";
    public static final String SESSION_ACCESS_TOKEN_KEY = "SSO_ACCESS_TOKEN";

    private static final Logger log = LoggerFactory.getLogger(LegacySessionHandoverFilter.class);

    private final SsoProperties props;
    private final OidcIdentityResolver identityResolver;
    private final OAuth2AuthorizedClientService authorizedClientService;

    public LegacySessionHandoverFilter(SsoProperties props,
                                       OidcIdentityResolver identityResolver,
                                       OAuth2AuthorizedClientService authorizedClientService) {
        this.props = props;
        this.identityResolver = identityResolver;
        this.authorizedClientService = authorizedClientService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof OidcUser oidc) {
            HttpSession session = request.getSession(true);
            ResolvedOidcIdentity identity = identityResolver.resolve(oidc, props);

            String user = identity.username();
            String email = identity.email();
            String name = identity.displayName();
            String roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(","));

            // Session attributes (for in-process legacy apps sharing the session)
            session.setAttribute(SESSION_USER_KEY, user);
            session.setAttribute(SESSION_EMAIL_KEY, email);
            session.setAttribute(SESSION_NAME_KEY, name);
            session.setAttribute(SESSION_ROLES_KEY, roles);

            // Request attributes (for forward/include to legacy servlets)
            request.setAttribute(props.getHeaders().getUser(), user);
            request.setAttribute(props.getHeaders().getEmail(), email);
            request.setAttribute(props.getHeaders().getName(), name);
            request.setAttribute(props.getHeaders().getRoles(), roles);

            // Also expose raw claims for advanced legacy needs
            Map<String, Object> claims = oidc.getClaims();
            if (claims != null) {
                session.setAttribute("SSO_CLAIMS", claims);
            }

            // ID token (always available for OIDC login)
            String idTokenValue = oidc.getIdToken() != null ? oidc.getIdToken().getTokenValue() : null;
            if (idTokenValue != null) {
                session.setAttribute(SESSION_ID_TOKEN_KEY, idTokenValue);
            }

            // Access token (lookup the authorized client by registration id)
            if (auth instanceof OAuth2AuthenticationToken oauthAuth) {
                OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                        oauthAuth.getAuthorizedClientRegistrationId(),
                        oauthAuth.getName());
                if (client != null && client.getAccessToken() != null) {
                    String accessTokenValue = client.getAccessToken().getTokenValue();
                    session.setAttribute(SESSION_ACCESS_TOKEN_KEY, accessTokenValue);
                    log.debug("[SSO] Stored access token for user={} (type={}, expiresAt={})",
                            user,
                            client.getAccessToken().getTokenType().getValue(),
                            client.getAccessToken().getExpiresAt());
                } else {
                    log.warn("[SSO] No access token available for user={}", user);
                }
            }
        }

        chain.doFilter(request, response);
    }
}
