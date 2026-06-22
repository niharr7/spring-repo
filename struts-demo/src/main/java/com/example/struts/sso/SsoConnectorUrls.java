package com.example.struts.sso;

import javax.servlet.FilterConfig;

/**
 * Resolves the SSO connector endpoints used by the legacy Struts app.
 *
 * <p>The legacy app should not hardcode connector URLs because they vary by
 * environment: local development, shared test environments, and deployed
 * client environments can all point at different connector hosts or paths.
 * This helper centralizes that lookup so the login JSP and logout flow use the
 * same precedence rules and can be configured without code changes.
 */
public final class SsoConnectorUrls {

    public static final String CONNECTOR_LOGIN_URL_PROPERTY = "sso.connector.login-url";
    public static final String CONNECTOR_LOGOUT_URL_PROPERTY = "sso.connector.logout-url";
    public static final String CONNECTOR_BASE_URL_PROPERTY = "sso.connector.base-url";
    public static final String CONNECTOR_LOGIN_PATH_PROPERTY = "sso.connector.login-path";
    public static final String CONNECTOR_LOGOUT_PATH_PROPERTY = "sso.connector.logout-path";

    public static final String CONNECTOR_LOGIN_URL_ENV = "SSO_CONNECTOR_LOGIN_URL";
    public static final String CONNECTOR_LOGOUT_URL_ENV = "SSO_CONNECTOR_LOGOUT_URL";
    public static final String CONNECTOR_BASE_URL_ENV = "SSO_CONNECTOR_BASE_URL";
    public static final String CONNECTOR_LOGIN_PATH_ENV = "SSO_CONNECTOR_LOGIN_PATH";
    public static final String CONNECTOR_LOGOUT_PATH_ENV = "SSO_CONNECTOR_LOGOUT_PATH";

    public static final String DEFAULT_CONNECTOR_LOGIN_PATH = "/apps/struts/sso/login";
    /**
     * Connector endpoint that renders a CSRF-protected POST form targeting
     * Spring Security's {@code /logout} endpoint. Legacy apps redirect here
     * (rather than directly to {@code /logout}) so the connector can issue
     * a valid CSRF token before performing the POST.
     */
    public static final String DEFAULT_CONNECTOR_LOGOUT_PATH = "/sso/logout";

    private SsoConnectorUrls() {
    }

    /**
     * Resolves the connector login URL for the legacy application's sign-in link.
     *
     * <p>Precedence is:
     * filter init param, JVM system property, OS environment variable, then a
     * URL built from the configured connector base URL plus the login path.
     */
    public static String resolveLoginUrl(FilterConfig cfg) {
        return normalizeLegacyLoginUrl(firstNonBlank(
                trimToNull(cfg == null ? null : cfg.getInitParameter("connectorLoginUrl")),
                trimToNull(System.getProperty(CONNECTOR_LOGIN_URL_PROPERTY)),
                trimToNull(System.getenv(CONNECTOR_LOGIN_URL_ENV)),
            buildFromBaseUrl(DEFAULT_CONNECTOR_LOGIN_PATH)));
    }

    /**
     * Resolves the connector logout URL used when the legacy app signs the user out.
     *
     * <p>Unlike login, there is no filter init-param override here because the
     * logout link is typically resolved from JSPs and helpers outside filter
     * initialization. The method first honors an explicit logout URL and then
     * falls back to building one from the configured connector base URL.
     */
    public static String resolveLogoutUrl() {
        return firstNonBlank(
                trimToNull(System.getProperty(CONNECTOR_LOGOUT_URL_PROPERTY)),
                trimToNull(System.getenv(CONNECTOR_LOGOUT_URL_ENV)),
                buildFromBaseUrl(DEFAULT_CONNECTOR_LOGOUT_PATH),
                deriveFromLoginUrl(DEFAULT_CONNECTOR_LOGOUT_PATH));
    }

    /**
     * Derives the connector logout URL from a configured login URL when no
     * explicit base/logout URL is available. The connector login and logout
     * endpoints always share the same origin, so we can reuse the login URL's
     * scheme + authority and swap in the requested path.
     */
    private static String deriveFromLoginUrl(String path) {
        String loginUrl = firstNonBlank(
                trimToNull(System.getProperty(CONNECTOR_LOGIN_URL_PROPERTY)),
                trimToNull(System.getenv(CONNECTOR_LOGIN_URL_ENV)));
        if (loginUrl == null) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(loginUrl);
            String authority = uri.getAuthority();
            String scheme = uri.getScheme();
            if (scheme == null || authority == null) {
                return null;
            }
            return scheme + "://" + authority + (path.startsWith("/") ? path : "/" + path);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Builds a full connector endpoint from the configured connector base URL.
     *
     * <p>This supports deployments where only the connector host is known ahead
     * of time and the login/logout paths should use standard defaults or
     * environment-specific overrides.
     */
    private static String buildFromBaseUrl(String defaultPath) {
        String baseUrl = firstNonBlank(
                trimToNull(System.getProperty(CONNECTOR_BASE_URL_PROPERTY)),
                trimToNull(System.getenv(CONNECTOR_BASE_URL_ENV)));
        if (baseUrl == null) {
            return null;
        }

        String path = defaultPath;
        if (DEFAULT_CONNECTOR_LOGIN_PATH.equals(defaultPath)) {
            path = firstNonBlank(
                    trimToNull(System.getProperty(CONNECTOR_LOGIN_PATH_PROPERTY)),
                    trimToNull(System.getenv(CONNECTOR_LOGIN_PATH_ENV)),
                    DEFAULT_CONNECTOR_LOGIN_PATH);
        } else if (DEFAULT_CONNECTOR_LOGOUT_PATH.equals(defaultPath)) {
            path = firstNonBlank(
                    trimToNull(System.getProperty(CONNECTOR_LOGOUT_PATH_PROPERTY)),
                    trimToNull(System.getenv(CONNECTOR_LOGOUT_PATH_ENV)),
                    DEFAULT_CONNECTOR_LOGOUT_PATH);
        }

        return joinUrl(baseUrl, path);
    }

    /**
     * Safely joins a connector base URL and endpoint path into one URL.
     *
     * <p>This normalizes slashes so callers can provide either
     * {@code http://host:port} or {@code http://host:port/} and still get a
     * valid endpoint.
     */
    private static String joinUrl(String baseUrl, String path) {
        String normalizedBase = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    private static String normalizeLegacyLoginUrl(String url) {
        if (url == null) {
            return null;
        }

        if (url.endsWith("/sso/login")) {
            return url.substring(0, url.length() - "/sso/login".length()) + DEFAULT_CONNECTOR_LOGIN_PATH;
        }
        return url;
    }

    /**
     * Returns the first configured value that is already known to be non-blank.
     *
     * <p>The callers pass values through {@link #trimToNull(String)} first, so a
     * simple null check here is enough to implement configuration precedence.
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * Converts blank configuration values to {@code null} after trimming.
     *
     * <p>This lets the resolver treat unset, empty, and whitespace-only values
     * the same way when evaluating fallback order.
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}