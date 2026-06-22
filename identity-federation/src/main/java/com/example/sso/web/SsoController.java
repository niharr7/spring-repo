package com.example.sso.web;

import com.example.sso.config.SsoProperties;
import com.example.sso.support.AppResolver;
import com.example.sso.support.OidcIdentityResolver;
import com.example.sso.support.ResolvedApp;
import com.example.sso.support.ResolvedOidcIdentity;
import com.example.sso.support.ResolvedTenant;
import com.example.sso.support.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
public class SsoController {

    private static final Logger log = LoggerFactory.getLogger(SsoController.class);

    private final SsoProperties props;
    private final OidcIdentityResolver identityResolver;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final TenantResolver tenantResolver;
    private final AppResolver appResolver;

    public SsoController(SsoProperties props,
                         OidcIdentityResolver identityResolver,
                         ClientRegistrationRepository clientRegistrationRepository,
                         TenantResolver tenantResolver,
                         AppResolver appResolver) {
        this.props = props;
        this.identityResolver = identityResolver;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.tenantResolver = tenantResolver;
        this.appResolver = appResolver;
    }

    @GetMapping("/")
    @ResponseBody
    public String index() {
        StringBuilder html = new StringBuilder("<h1>SSO Connector</h1>");
        List<String> configuredApps = props.getApps().keySet().stream().sorted().toList();
        if (configuredApps.isEmpty()) {
            html.append("<p><a href=\"/sso/login\">Login with Single Sign-On</a></p>");
            html.append("<p><a href=\"/sso/forward\">Continue to legacy app</a></p>");
        } else {
            html.append("<p>Configured applications:</p><ul>");
            for (String appId : configuredApps) {
                SsoProperties.App app = props.getApps().get(appId);
                String label = app != null && app.getDisplayName() != null && !app.getDisplayName().isBlank()
                        ? app.getDisplayName()
                        : appId;
                html.append("<li><a href=\"/apps/")
                        .append(appId)
                        .append("/sso/login\">")
                        .append(label)
                        .append("</a></li>");
            }
            html.append("</ul>");
        }
        html.append("<p><a href=\"/sso/me\">Show my identity (JSON)</a></p>");
        html.append("<p><a href=\"/sso/logout\">Logout</a></p>");
        return html.toString();
    }

    /** Convenience entry point that triggers OAuth2 login. */
    @GetMapping({"/sso/login", "/apps/{appId}/sso/login"})
    public Object login(@PathVariable(name = "appId", required = false) String appId,
                        HttpServletRequest request,
                        Model model) {
        ResolvedApp app = resolveRequestedApp(appId);
        ResolvedTenant tenant = tenantResolver.resolveFromRequest(request).orElse(null);
        if (tenant != null && app != null) {
            return beginLogin(request, tenant, app);
        }

        populateLoginModel(model, app, "", app == null
                ? "Choose an application-specific sign-in link before continuing."
                : null);
        return "login";
    }

    @PostMapping({"/sso/login", "/apps/{appId}/sso/login"})
    public Object loginWithEmail(@RequestParam("email") String email,
                                 @PathVariable(name = "appId", required = false) String appId,
                                 HttpServletRequest request,
                                 Model model) {
        ResolvedApp app = resolveRequestedApp(appId);
        if (app == null) {
            populateLoginModel(model,
                    null,
                    email,
                    "Choose an application-specific sign-in link before continuing.");
            return "login";
        }

        ResolvedTenant tenant = tenantResolver.resolveFromEmail(email).orElse(null);
        if (tenant != null) {
            return beginLogin(request, tenant, app);
        }

        populateLoginModel(model, app, email, "No tenant matched that email domain.");
        return "login";
    }

    /** Landing page after successful OIDC authentication. Auto-forwards the
     *  browser into the resolved app's proxy root so multi-tenant + multi-app
     *  deployments land directly on the legacy app's post-login page. */
    @GetMapping("/sso/success")
    public Object success(@AuthenticationPrincipal OidcUser user,
                          Authentication authentication,
                          HttpServletRequest request,
                          Model model) {
        ResolvedOidcIdentity identity = identityResolver.resolve(user, props);
        ResolvedTenant tenant = tenantResolver.resolveForAuthenticatedRequest(
                request,
                registrationId(authentication));
        ResolvedApp app = appResolver.resolveForAuthenticatedRequest(request, null);
        if (app != null && app.proxyBasePath() != null && !app.proxyBasePath().isBlank()) {
            return new RedirectView(app.proxyBasePath() + "/");
        }
        model.addAttribute("user", user);
        model.addAttribute("identity", identity);
        model.addAttribute("legacyUrl", app == null ? null : app.legacyAppUrl());
        model.addAttribute("appDisplayName", app == null ? null : app.displayName());
        model.addAttribute("forwardUrl", app == null ? "/" : app.proxyBasePath() + "/");
        model.addAttribute("providerDisplayName", tenant.providerDisplayName());
        return "success";
    }

    /**
     * Renders a connector-hosted intermediate page that issues a CSRF-protected
     * POST to Spring Security's {@code /logout} endpoint.
     *
     * <p>Legacy applications redirect users here (instead of directly to
     * {@code /logout}) because Spring Security's logout endpoint requires a
     * POST with a valid CSRF token bound to the connector session. The page
     * auto-submits via JavaScript and falls back to a manual button when
     * JavaScript is disabled.
     *
     * <p>The {@code returnTo} parameter is forwarded as a hidden form field so
     * the logout success handler can redirect the user back to the requesting
     * legacy application. The success handler validates the value against the
     * configured legacy app URLs before issuing the redirect.
     */
    @GetMapping("/sso/logout")
    public String logoutForm(@RequestParam(name = "returnTo", required = false) String returnTo,
                             Model model) {
        log.info("[logout] /sso/logout received returnTo='{}'", returnTo);
        model.addAttribute("returnTo", returnTo == null ? "" : returnTo);
        return "logout";
    }

    /** Return the current identity payload (useful for diagnostics & SPAs). */
    @GetMapping("/sso/me")
    @ResponseBody
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser user,
                                  Authentication authentication,
                                  HttpServletRequest request) {
        Map<String, Object> out = new HashMap<>();
        if (user == null) {
            out.put("authenticated", false);
            return out;
        }
        ResolvedOidcIdentity identity = identityResolver.resolve(user, props);
        ResolvedTenant tenant = tenantResolver.resolveForAuthenticatedRequest(
                request,
                registrationId(authentication));
        ResolvedApp app = appResolver.resolveForAuthenticatedRequest(request, null);
        out.put("authenticated", true);
        out.put("tenant", tenant.tenantId());
        out.put("appId", app.appId());
        out.put("appDisplayName", app.displayName());
        out.put("registrationId", tenant.registrationId());
        out.put("provider", tenant.providerDisplayName());
        out.put("subject", identity.subject());
        out.put("username", identity.username());
        out.put("email", identity.email());
        out.put("name", identity.displayName());
        out.put("claims", user.getClaims());
        return out;
    }

    /** Forwards the authenticated user to the legacy application. */
    @GetMapping({"/sso/forward", "/apps/{appId}/sso/forward"})
    public RedirectView forward(Authentication authentication,
                                @PathVariable(name = "appId", required = false) String appId,
                                HttpServletRequest request) {
        tenantResolver.resolveForAuthenticatedRequest(request, registrationId(authentication));
        ResolvedApp app = appResolver.resolveForAuthenticatedRequest(request, appId);
        return new RedirectView(app.proxyBasePath() + "/");
    }

    private RedirectView beginLogin(HttpServletRequest request,
                                    ResolvedTenant tenant,
                                    ResolvedApp app) {
        tenantResolver.rememberTenant(request, tenant);
        appResolver.rememberApp(request, app);
        return new RedirectView("/oauth2/authorization/" + tenant.registrationId());
    }

    private ResolvedApp resolveRequestedApp(String appId) {
        if (appId != null && !appId.isBlank()) {
            return appResolver.resolveApp(appId)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Unknown app: " + appId));
        }
        return appResolver.defaultApp().orElse(null);
    }

    private void populateLoginModel(Model model,
                                    ResolvedApp app,
                                    String email,
                                    String error) {
        model.addAttribute("email", email);
        model.addAttribute("error", error);
        model.addAttribute("selectedAppId", app == null ? null : app.appId());
        model.addAttribute("selectedAppName", app == null ? null : app.displayName());
        model.addAttribute("formAction", app == null ? "/sso/login" : "/apps/" + app.appId() + "/sso/login");
        model.addAttribute("apps", props.getApps());
    }

    private String registrationId(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken oauth) {
            return oauth.getAuthorizedClientRegistrationId();
        }
        String configured = props.getRegistrationId();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (clientRegistrationRepository instanceof Iterable<?> registrations) {
            for (Object entry : registrations) {
                if (entry instanceof ClientRegistration registration) {
                    return registration.getRegistrationId();
                }
            }
        }
        throw new IllegalStateException("No OAuth2 client registration is configured for /sso/login");
    }
}
