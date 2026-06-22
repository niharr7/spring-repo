package com.example.servletdemo.sso;

import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Establishes legacy servlet sessions from trusted connector headers.
 *
 * <p>This filter preserves the classic legacy login experience while allowing
 * the external SSO connector to authenticate the user and hand identity to the
 * app through trusted headers.
 */
public class SsoTrustFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(SsoTrustFilter.class.getName());

    public static final String SESSION_USER_KEY = "loggedInUser";
    public static final String SESSION_EMAIL_KEY = "ssoEmail";
    public static final String SESSION_NAME_KEY = "ssoName";
    public static final String SESSION_ROLES_KEY = "ssoRoles";
    public static final String SESSION_AUTH_METHOD_KEY = "authMethod";
    public static final String SESSION_TENANT_KEY = "ssoTenant";

    public static final String AUTH_METHOD_LOCAL = "LOCAL";
    public static final String AUTH_METHOD_SSO = "SSO";

    private String trustSecret;
    private String connectorLoginUrl;
    private String userHeader;
    private String emailHeader;
    private String nameHeader;
    private String rolesHeader;
    private String trustHeader;
    private List<String> publicPaths;

    @Override
    public void init(FilterConfig cfg) {
        this.trustSecret = param(cfg, "trustSecret", "");
        this.connectorLoginUrl = SsoConnectorUrls.resolveLoginUrl(cfg.getServletContext());
        this.userHeader = param(cfg, "userHeader", "X-SSO-User");
        this.emailHeader = param(cfg, "emailHeader", "X-SSO-Email");
        this.nameHeader = param(cfg, "nameHeader", "X-SSO-Name");
        this.rolesHeader = param(cfg, "rolesHeader", "X-SSO-Roles");
        this.trustHeader = param(cfg, "trustHeader", "X-SSO-Trust");

        String paths = param(cfg, "publicPaths", "");
        this.publicPaths = paths.isEmpty()
                ? Collections.<String>emptyList()
                : Arrays.asList(paths.split("\\s*,\\s*"));

        if (trustSecret == null || trustSecret.isEmpty()) {
            LOG.warning("[SSO] trustSecret is not configured. Header-based login is disabled.");
        }
        if (connectorLoginUrl == null || connectorLoginUrl.isEmpty()) {
            LOG.severe("[SSO] Connector login URL is not configured. Set "
                    + SsoConnectorUrls.CONNECTOR_LOGIN_URL_PROPERTY + " / "
                    + SsoConnectorUrls.CONNECTOR_LOGIN_URL_ENV + " or "
                    + SsoConnectorUrls.CONNECTOR_BASE_URL_PROPERTY + " / "
                    + SsoConnectorUrls.CONNECTOR_BASE_URL_ENV + '.');
        }
        LOG.info("[SSO] SsoTrustFilter initialized. connectorLoginUrl=" + connectorLoginUrl);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getRequestURI().substring(request.getContextPath().length());

        HttpSession session = request.getSession(false);
        String tenant = trimToNull(request.getParameter("tenant"));
        if (tenant != null) {
            request.getSession(true).setAttribute(SESSION_TENANT_KEY, tenant);
            session = request.getSession(false);
        }

        boolean authenticated = session != null && session.getAttribute(SESSION_USER_KEY) != null;
        if (authenticated && isLoginOrRoot(path)) {
            response.sendRedirect(request.getContextPath() + "/dashboard");
            return;
        }
        if (authenticated) {
            chain.doFilter(req, res);
            return;
        }

        String headerUser = request.getHeader(userHeader);
        String headerTrust = request.getHeader(trustHeader);
        if (headerUser != null && !headerUser.isEmpty() && validTrust(headerTrust)) {
            HttpSession s = request.getSession(true);
            s.setAttribute(SESSION_USER_KEY, headerUser);
            s.setAttribute(SESSION_AUTH_METHOD_KEY, AUTH_METHOD_SSO);
            putIfPresent(s, SESSION_EMAIL_KEY, request.getHeader(emailHeader));
            putIfPresent(s, SESSION_NAME_KEY, request.getHeader(nameHeader));
            putIfPresent(s, SESSION_ROLES_KEY, request.getHeader(rolesHeader));
            LOG.log(Level.INFO, "[SSO] Session created for user={0}", headerUser);

            if (isLoginOrRoot(path)) {
                response.sendRedirect(request.getContextPath() + "/dashboard");
                return;
            }
            chain.doFilter(req, res);
            return;
        }

        if (headerUser != null && !headerUser.isEmpty()) {
            LOG.warning("[SSO] Rejected request with SSO headers but invalid trust secret. Possible spoofing attempt from "
                    + request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid SSO trust token");
            return;
        }

        if (isPublic(path) || isLoginOrRoot(path)) {
            chain.doFilter(req, res);
            return;
        }

        response.sendRedirect(buildLocalLoginTarget(request, tenant, session));
    }

    @Override
    public void destroy() {
        // nothing to release
    }

    private String buildLocalLoginTarget(HttpServletRequest request, String tenant, HttpSession session) {
        String resolvedTenant = tenant;
        if (resolvedTenant == null && session != null) {
            Object tenantObj = session.getAttribute(SESSION_TENANT_KEY);
            resolvedTenant = tenantObj == null ? null : trimToNull(String.valueOf(tenantObj));
        }

        // If the connector login URL is configured, always redirect there so
        // authentication is handled by the SSO connector regardless of whether
        // the user accessed the legacy app directly or through the proxy.
        if (connectorLoginUrl != null && !connectorLoginUrl.isEmpty()) {
            String target = connectorLoginUrl;
            if (resolvedTenant != null) {
                target = target + "?tenant=" + urlEncode(resolvedTenant);
            }
            return target;
        }

        // Fallback: local login page (used when connector is not configured,
        // e.g. standalone development without the connector running).
        String target = request.getContextPath() + "/login";
        if (resolvedTenant != null) {
            target = target + "?tenant=" + urlEncode(resolvedTenant);
        }
        return target;
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 must be supported", e);
        }
    }

    private void putIfPresent(HttpSession session, String key, String value) {
        if (value != null && !value.isEmpty()) {
            session.setAttribute(key, value);
        }
    }

    private boolean validTrust(String supplied) {
        if (trustSecret == null || trustSecret.isEmpty() || supplied == null) {
            return false;
        }

        byte[] expected = trustSecret.getBytes(StandardCharsets.UTF_8);
        byte[] actual = supplied.getBytes(StandardCharsets.UTF_8);
        if (expected.length != actual.length) {
            return false;
        }

        int diff = 0;
        for (int i = 0; i < expected.length; i++) {
            diff |= expected[i] ^ actual[i];
        }
        return diff == 0;
    }

    private boolean isPublic(String path) {
        for (String publicPath : publicPaths) {
            if (path.equals(publicPath) || path.startsWith(publicPath)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLoginOrRoot(String path) {
        if (path == null) {
            return false;
        }
        return path.isEmpty()
                || path.equals("/")
                || path.equals("/index.jsp")
                || path.startsWith("/login");
    }

    private static String param(FilterConfig cfg, String name, String defaultValue) {
        String value = cfg.getInitParameter(name);
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}