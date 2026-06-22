package com.example.sso.support;

import com.example.sso.config.SsoProperties;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AppResolver {

    public static final String SESSION_APP_ID = "SSO_APP_ID";

    private final SsoProperties props;

    public AppResolver(SsoProperties props) {
        this.props = props;
    }

    @PostConstruct
    void validateConfiguration() {
        for (Map.Entry<String, SsoProperties.App> entry : props.getApps().entrySet()) {
            String appId = trimToNull(entry.getKey());
            SsoProperties.App app = entry.getValue();
            if (appId == null) {
                throw new IllegalStateException("Configured app id cannot be blank");
            }
            if (app == null || trimToNull(app.getLegacyAppUrl()) == null) {
                throw new IllegalStateException("Configured app '" + appId + "' is missing legacy-app-url");
            }
        }
    }

    public Optional<ResolvedApp> resolveApp(String appId) {
        String normalizedAppId = trimToNull(appId);
        if (normalizedAppId == null) {
            return Optional.empty();
        }

        SsoProperties.App app = props.getApps().get(normalizedAppId);
        if (app != null) {
            return Optional.of(toResolvedApp(normalizedAppId, app));
        }

        if ("default".equals(normalizedAppId)) {
            return defaultApp();
        }
        return Optional.empty();
    }

    public Optional<ResolvedApp> defaultApp() {
        if (!props.getApps().isEmpty()) {
            if (props.getApps().size() == 1) {
                Map.Entry<String, SsoProperties.App> onlyApp = props.getApps().entrySet().iterator().next();
                return Optional.of(toResolvedApp(onlyApp.getKey(), onlyApp.getValue()));
            }
            return Optional.empty();
        }

        String legacyUrl = trimToNull(props.getLegacyAppUrl());
        if (legacyUrl == null) {
            return Optional.empty();
        }

        return Optional.of(new ResolvedApp(
                "default",
                "Legacy App",
                legacyUrl,
                trimToNull(props.getTrustSecret()),
                computeProxyBasePath("default", null, legacyUrl)));
    }

    public Optional<ResolvedApp> resolveFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }

        Object appId = session.getAttribute(SESSION_APP_ID);
        if (appId == null) {
            return Optional.empty();
        }

        return resolveApp(String.valueOf(appId));
    }

    public ResolvedApp resolveForAuthenticatedRequest(HttpServletRequest request, String requestedAppId) {
        return resolveApp(requestedAppId)
                .or(() -> resolveFromSession(request))
                .or(() -> defaultApp())
                .orElseThrow(() -> new IllegalStateException("Unable to resolve app for authenticated request"));
    }

    public void rememberApp(HttpServletRequest request, ResolvedApp app) {
        request.getSession(true).setAttribute(SESSION_APP_ID, app.appId());
    }

    private ResolvedApp toResolvedApp(String appId, SsoProperties.App app) {
        String legacyUrl = trimToNull(app.getLegacyAppUrl());
        return new ResolvedApp(
                appId,
                firstNonBlank(app.getDisplayName(), appId),
                legacyUrl,
                trimToNull(app.getTrustSecret()),
                computeProxyBasePath(appId, trimToNull(app.getProxyBasePath()), legacyUrl));
    }

    /**
     * Picks the connector path that proxies to the legacy app.
     * <ol>
     *   <li>Explicit {@code proxy-base-path} from configuration.</li>
     *   <li>Path component of {@code legacy-app-url} (e.g. {@code /struts-demo}).</li>
     *   <li>Fallback {@code /apps/{appId}/proxy}.</li>
     * </ol>
     */
    public static String computeProxyBasePath(String appId, String configuredPath, String legacyAppUrl) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            return normalisePath(configuredPath);
        }
        if (legacyAppUrl != null && !legacyAppUrl.isBlank()) {
            try {
                String path = java.net.URI.create(legacyAppUrl).getPath();
                if (path != null && !path.isEmpty() && !"/".equals(path)) {
                    return normalisePath(path);
                }
            } catch (IllegalArgumentException ignored) {
                // fall through to default
            }
        }
        return "/apps/" + appId + "/proxy";
    }

    private static String normalisePath(String path) {
        String p = path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (trimToNull(value) != null) {
                return value;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}