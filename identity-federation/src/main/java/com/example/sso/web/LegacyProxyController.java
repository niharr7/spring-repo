package com.example.sso.web;

import com.example.sso.config.SsoProperties;
import com.example.sso.support.AppResolver;
import com.example.sso.support.OidcIdentityResolver;
import com.example.sso.support.ResolvedApp;
import com.example.sso.support.ResolvedOidcIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Reverse proxy that forwards every request under the configured app path
 * (e.g. {@code /struts-demo/**}) from the connector ({@code :8081}) to the
 * legacy application ({@code :8080}), attaching trusted identity headers on
 * each call.
 *
 * <p>Strips any inbound {@code X-SSO-*} headers from the browser to prevent
 * spoofing.
 */
@Controller
public class LegacyProxyController {

    private static final Logger log = LoggerFactory.getLogger(LegacyProxyController.class);

    /** Headers we must NOT copy to the upstream / downstream response. */
    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade",
            "content-length", "host");

    /** Inbound headers from the browser that must NEVER reach the legacy app. */
    private static final Set<String> STRIPPED_INBOUND = Set.of(
            "x-sso-user", "x-sso-email", "x-sso-name", "x-sso-roles", "x-sso-trust");

    private final SsoProperties props;
    private final OidcIdentityResolver identityResolver;
    private final AppResolver appResolver;
    private final org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping handlerMapping;
    private final HttpClient httpClient;

    public LegacyProxyController(SsoProperties props,
                                 OidcIdentityResolver identityResolver,
                                 AppResolver appResolver,
                                 org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping handlerMapping) {
        this.props = props;
        this.identityResolver = identityResolver;
        this.appResolver = appResolver;
        this.handlerMapping = handlerMapping;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Registers one MVC mapping per configured app so the connector exposes
     * each legacy application at its configured/derived path (for example
     * {@code /struts-demo/**}) instead of a hard-coded prefix.
     */
    @jakarta.annotation.PostConstruct
    void registerAppRoutes() throws NoSuchMethodException {
        java.lang.reflect.Method handler = LegacyProxyController.class.getDeclaredMethod(
                "proxy", HttpServletRequest.class, HttpServletResponse.class, OidcUser.class);

        java.util.Set<String> registered = new java.util.HashSet<>();
        for (Map.Entry<String, com.example.sso.config.SsoProperties.App> e : props.getApps().entrySet()) {
            String base = AppResolver.computeProxyBasePath(
                    e.getKey(),
                    e.getValue() == null ? null : e.getValue().getProxyBasePath(),
                    e.getValue() == null ? null : e.getValue().getLegacyAppUrl());
            if (registered.add(base)) {
                registerProxyMapping(base, handler);
            }
        }
        // Single-app deployments using the top-level legacy-app-url
        if (props.getApps().isEmpty() && props.getLegacyAppUrl() != null
                && !props.getLegacyAppUrl().isBlank()) {
            String base = AppResolver.computeProxyBasePath("default", null, props.getLegacyAppUrl());
            if (registered.add(base)) {
                registerProxyMapping(base, handler);
            }
        }
    }

    private void registerProxyMapping(String base,
                                      java.lang.reflect.Method handler) {
        org.springframework.web.servlet.mvc.method.RequestMappingInfo.BuilderConfiguration options =
                new org.springframework.web.servlet.mvc.method.RequestMappingInfo.BuilderConfiguration();
        options.setPatternParser(handlerMapping.getPatternParser());

        org.springframework.web.servlet.mvc.method.RequestMappingInfo info =
                org.springframework.web.servlet.mvc.method.RequestMappingInfo
                        .paths(base, base + "/**")
                        .options(options)
                        .build();
        handlerMapping.registerMapping(info, this, handler);
        log.info("[Proxy] Registered proxy route {} (and {}/**)", base, base);
    }

    public void proxy(HttpServletRequest request,
                      HttpServletResponse response,
                      @AuthenticationPrincipal OidcUser user) throws IOException {

        if (user == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        ResolvedApp app = resolveAppForRequest(request);
        if (app == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Build target URL: legacyAppUrl + (path after prefix) + query string
        String prefix = app.proxyBasePath();
        String legacyBase = stripTrailingSlash(app.legacyAppUrl());
        String incomingPath = request.getRequestURI(); // e.g. /struts-demo/welcome.action
        String remainder = incomingPath.startsWith(prefix)
                ? incomingPath.substring(prefix.length())
                : incomingPath;
        if (remainder.isEmpty()) {
            remainder = "/";
        }
        String query = request.getQueryString();
        String targetUrl = legacyBase + remainder + (query != null ? "?" + query : "");

        // Read request body (may be empty for GET/HEAD)
        byte[] body;
        try (InputStream in = request.getInputStream()) {
            body = in.readAllBytes();
        }

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .method(request.getMethod(),
                        body.length == 0
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofByteArray(body));

        // Copy safe inbound headers (skip hop-by-hop and any spoofed X-SSO-*)
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            String lower = name.toLowerCase(Locale.ROOT);
            if (HOP_BY_HOP.contains(lower) || STRIPPED_INBOUND.contains(lower)) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                try {
                    rb.header(name, values.nextElement());
                } catch (IllegalArgumentException ignored) {
                    // HttpClient restricts some header names; skip silently
                }
            }
        }

        // Inject trusted identity headers
        ResolvedOidcIdentity identity = identityResolver.resolve(user, props);
        addIfPresent(rb, props.getHeaders().getUser(), identity.username());
        addIfPresent(rb, props.getHeaders().getEmail(), identity.email());
        addIfPresent(rb, props.getHeaders().getName(), identity.displayName());
        // roles can be added later from authorities; left empty for now
        if (app.trustSecret() != null && !app.trustSecret().isEmpty()) {
            rb.header("X-SSO-Trust", app.trustSecret());
        }

        HttpResponse<InputStream> upstream;
        try {
            upstream = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Proxy interrupted");
            return;
        } catch (IOException ioe) {
            log.error("[Proxy] Failed to reach legacy app at {}", targetUrl, ioe);
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY,
                    "Unable to reach legacy application");
            return;
        }

        // Copy status + headers back to the browser. Location headers from
        // the legacy app point at the legacy context path (e.g. /struts-demo/
        // welcome.action) which does not exist on the connector; rewrite them
        // so the browser stays inside the connector-owned proxy path.
        response.setStatus(upstream.statusCode());
        String legacyContextPath = extractContextPath(app.legacyAppUrl());
        for (Map.Entry<String, List<String>> e : upstream.headers().map().entrySet()) {
            String name = e.getKey();
            if (name == null) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            if (HOP_BY_HOP.contains(lower)) continue;
            for (String v : e.getValue()) {
                String value = v;
                if ("location".equals(lower)) {
                    value = rewriteLocation(v, app.legacyAppUrl(), legacyContextPath, prefix);
                }
                response.addHeader(name, value);
            }
        }

        // Stream body
        try (InputStream in = upstream.body();
             OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }

    private static void addIfPresent(HttpRequest.Builder rb, String name, String value) {
        if (name != null && !name.isEmpty() && value != null && !value.isEmpty()) {
            rb.header(name, value);
        }
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * Picks the configured app whose {@code proxyBasePath} is the longest
     * matching prefix of the incoming request URI. Returns {@code null} if no
     * app claims the path.
     */
    private ResolvedApp resolveAppForRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        ResolvedApp best = null;
        int bestLen = -1;
        for (String appId : props.getApps().keySet()) {
            ResolvedApp candidate = appResolver.resolveApp(appId).orElse(null);
            if (candidate == null) continue;
            String base = candidate.proxyBasePath();
            if (base == null || base.isEmpty()) continue;
            if (uri.equals(base) || uri.startsWith(base + "/")) {
                if (base.length() > bestLen) {
                    best = candidate;
                    bestLen = base.length();
                }
            }
        }
        if (best == null) {
            ResolvedApp fallback = appResolver.defaultApp().orElse(null);
            if (fallback != null) {
                String base = fallback.proxyBasePath();
                if (base != null && (uri.equals(base) || uri.startsWith(base + "/"))) {
                    return fallback;
                }
            }
        }
        return best;
    }

    /**
     * Extracts the path portion of {@code legacyAppUrl} so we can recognise
     * redirects that point back at the legacy app's context path (for example
     * {@code /struts-demo}) and rewrite them through the connector proxy.
     */
    private static String extractContextPath(String legacyAppUrl) {
        if (legacyAppUrl == null || legacyAppUrl.isEmpty()) return "";
        try {
            String path = URI.create(legacyAppUrl).getPath();
            if (path == null || path.isEmpty() || "/".equals(path)) return "";
            return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    /**
     * Rewrites a {@code Location} header coming back from the legacy app so the
     * browser stays inside the connector-owned proxy path. Absolute URLs that
     * point at the configured legacy host and context-relative paths are both
     * mapped onto {@code /apps/{appId}/proxy/...}.
     */
    private static String rewriteLocation(String location,
                                          String legacyAppUrl,
                                          String legacyContextPath,
                                          String proxyPrefix) {
        if (location == null || location.isEmpty()) {
            return location;
        }

        // Absolute URL pointing at the configured legacy app: strip the base.
        if (legacyAppUrl != null && !legacyAppUrl.isEmpty()
                && location.startsWith(legacyAppUrl)) {
            String remainder = location.substring(legacyAppUrl.length());
            if (remainder.isEmpty()) {
                remainder = "/";
            } else if (!remainder.startsWith("/") && !remainder.startsWith("?")) {
                remainder = "/" + remainder;
            }
            return proxyPrefix + remainder;
        }

        // Context-relative path like /struts-demo/welcome.action
        if (!legacyContextPath.isEmpty() && location.startsWith(legacyContextPath)) {
            String remainder = location.substring(legacyContextPath.length());
            if (remainder.isEmpty()) {
                remainder = "/";
            } else if (!remainder.startsWith("/") && !remainder.startsWith("?")) {
                remainder = "/" + remainder;
            }
            return proxyPrefix + remainder;
        }

        // Root-relative path with no legacy context (e.g. legacyAppUrl is just
        // a host) → still scope it under the proxy so the browser does not
        // bounce off the connector root.
        if (legacyContextPath.isEmpty() && location.startsWith("/")
                && !location.startsWith(proxyPrefix + "/")
                && !location.equals(proxyPrefix)) {
            return proxyPrefix + location;
        }

        return location;
    }
}
