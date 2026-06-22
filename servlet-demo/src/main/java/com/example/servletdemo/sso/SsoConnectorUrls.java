package com.example.servletdemo.sso;

import javax.servlet.ServletContext;

/**
 * Resolves the SSO connector endpoints used by the servlet-based legacy app.
 *
 * <p>The legacy application should not hardcode connector hosts because they
 * vary between local development, shared environments, and client deployments.
 * This utility centralizes the lookup and keeps login and logout flows aligned
 * on the same precedence rules.
 */
public final class SsoConnectorUrls {

    public static final String CONNECTOR_LOGIN_URL_CONTEXT_PARAM = "connectorLoginUrl";
    public static final String CONNECTOR_LOGOUT_URL_CONTEXT_PARAM = "connectorLogoutUrl";

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

    public static final String DEFAULT_CONNECTOR_LOGIN_PATH = "/apps/servlet/sso/login";
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
     * Resolves the connector login URL used by the login JSP SSO button.
     */
    public static String resolveLoginUrl(ServletContext servletContext) {
        return normalizeLegacyLoginUrl(firstNonBlank(
                trimToNull(servletContext == null ? null : servletContext.getInitParameter(CONNECTOR_LOGIN_URL_CONTEXT_PARAM)),
                trimToNull(System.getProperty(CONNECTOR_LOGIN_URL_PROPERTY)),
                trimToNull(System.getenv(CONNECTOR_LOGIN_URL_ENV)),
            buildFromBaseUrl(DEFAULT_CONNECTOR_LOGIN_PATH)));
    }

    /**
     * Resolves the connector logout URL used when an SSO session signs out.
     */
    public static String resolveLogoutUrl(ServletContext servletContext) {
        return firstNonBlank(
                trimToNull(servletContext == null ? null : servletContext.getInitParameter(CONNECTOR_LOGOUT_URL_CONTEXT_PARAM)),
                trimToNull(System.getProperty(CONNECTOR_LOGOUT_URL_PROPERTY)),
                trimToNull(System.getenv(CONNECTOR_LOGOUT_URL_ENV)),
                buildFromBaseUrl(DEFAULT_CONNECTOR_LOGOUT_PATH),
                deriveFromLoginUrl(servletContext, DEFAULT_CONNECTOR_LOGOUT_PATH));
    }

    /**
     * Derives the connector logout URL from a configured login URL when no
     * explicit base/logout URL is available. The connector login and logout
     * endpoints always share the same origin, so we can reuse the login URL's
     * scheme + authority and swap in the requested path.
     */
    private static String deriveFromLoginUrl(ServletContext servletContext, String path) {
        String loginUrl = firstNonBlank(
                trimToNull(servletContext == null ? null : servletContext.getInitParameter(CONNECTOR_LOGIN_URL_CONTEXT_PARAM)),
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null) {
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