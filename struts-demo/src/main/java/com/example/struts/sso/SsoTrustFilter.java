package com.example.struts.sso;

import java.io.IOException;
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
 * SSO Trust Filter.
 *
 * <p>Replaces the legacy form-based login. On every request:
 * <ol>
 *   <li>If the user already has a logged-in session, let the request through.</li>
 *   <li>If the request carries the trusted SSO headers from the connector
 *       (validated via a shared secret), create the session and let the
 *       request through.</li>
 *   <li>Otherwise, redirect the browser to the SSO connector to start the
 *       Azure login flow.</li>
 * </ol>
 *
 * <p>Configuration (web.xml init-params):
 * <ul>
 *   <li><b>trustSecret</b> - shared secret expected in {@code X-SSO-Trust}</li>
 *   <li><b>connectorLoginUrl</b> - where to send unauthenticated users</li>
 *   <li><b>userHeader</b> / <b>emailHeader</b> / <b>nameHeader</b> / <b>rolesHeader</b>
 *       - header names (defaults: X-SSO-User, X-SSO-Email, X-SSO-Name, X-SSO-Roles)</li>
 *   <li><b>publicPaths</b> - comma-separated path prefixes to skip (e.g. /health,/static)</li>
 * </ul>
 */
public class SsoTrustFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(SsoTrustFilter.class.getName());

    /** Session key that the rest of the app already reads. */
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
        this.connectorLoginUrl = SsoConnectorUrls.resolveLoginUrl(cfg);
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
            LOG.warning("[SSO] trustSecret is not configured. "
                    + "Header-based login is DISABLED for security.");
        }
        if (connectorLoginUrl == null || connectorLoginUrl.isEmpty()) {
            LOG.severe("[SSO] Connector login URL is not configured. Set "
                    + SsoConnectorUrls.CONNECTOR_LOGIN_URL_PROPERTY + " / "
                    + SsoConnectorUrls.CONNECTOR_LOGIN_URL_ENV + " or "
                    + SsoConnectorUrls.CONNECTOR_BASE_URL_PROPERTY + " / "
                    + SsoConnectorUrls.CONNECTOR_BASE_URL_ENV + ".");
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

        // 0. Authenticated user landed on the branded login/root page → skip
        //    straight to the post-login destination.
        if (authenticated && isLoginOrRoot(path)) {
            response.sendRedirect(request.getContextPath() + "/welcome.action");
            return;
        }

        // 1. Already authenticated → fast path
        if (authenticated) {
            chain.doFilter(req, res);
            return;
        }

        // 2. Try to authenticate via SSO headers from the connector. This MUST
        //    run before the public / login-page bypass so that a proxied
        //    request to /login.action with valid SSO headers establishes the
        //    session instead of rendering the sign-in page again.
        String headerUser = request.getHeader(userHeader);
        String headerTrust = request.getHeader(trustHeader);

        if (headerUser != null && !headerUser.isEmpty() && validTrust(headerTrust)) {
            HttpSession s = request.getSession(true);
            s.setAttribute(SESSION_USER_KEY, headerUser);
            s.setAttribute(SESSION_AUTH_METHOD_KEY, AUTH_METHOD_SSO);
            String email = request.getHeader(emailHeader);
            String name = request.getHeader(nameHeader);
            String roles = request.getHeader(rolesHeader);
            if (email != null) s.setAttribute(SESSION_EMAIL_KEY, email);
            if (name  != null) s.setAttribute(SESSION_NAME_KEY,  name);
            if (roles != null) s.setAttribute(SESSION_ROLES_KEY, roles);
            LOG.log(Level.INFO, "[SSO] Session created for user={0}", headerUser);

            // If the user landed on the login page or root, send them to the
            // post-login page instead of showing the login form again.
            if (isLoginOrRoot(path)) {
                response.sendRedirect(request.getContextPath() + "/welcome.action");
                return;
            }
            chain.doFilter(req, res);
            return;
        }

        // 3. Header present but trust secret bad → reject hard
        if (headerUser != null && !headerUser.isEmpty()) {
            LOG.warning("[SSO] Rejected request with SSO headers but invalid trust secret. "
                    + "Possible spoofing attempt from " + request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Invalid SSO trust token");
            return;
        }

        // 4. Public paths and the branded login landing page bypass the filter
        //    so the JSP can render its "Sign in with SSO" button. This runs
        //    AFTER header auth so a proxied authenticated request can't be
        //    mistaken for an anonymous visit to the login page.
        if (isPublic(path) || isLoginOrRoot(path)) {
            chain.doFilter(req, res);
            return;
        }

        // 5. No SSO headers, no session → redirect to the connector login so
        //    authentication is always handled by the SSO connector.
        //    Falls back to the local login page when the connector is not configured.
        String targetTenant = tenant;
        if (targetTenant == null && session != null) {
            Object tenantFromSession = session.getAttribute(SESSION_TENANT_KEY);
            targetTenant = tenantFromSession == null ? null : trimToNull(String.valueOf(tenantFromSession));
        }

        String target;
        if (connectorLoginUrl != null && !connectorLoginUrl.isEmpty()) {
            // Redirect to connector — it handles tenant discovery and SSO
            target = connectorLoginUrl;
            if (targetTenant != null) {
                String encoded = java.net.URLEncoder.encode(targetTenant, "UTF-8");
                target = target + "?tenant=" + encoded;
            }
        } else {
            // Fallback: local login page (no connector configured)
            target = request.getContextPath() + "/login.action";
            if (targetTenant != null) {
                String encoded = java.net.URLEncoder.encode(targetTenant, "UTF-8");
                target = target + "?tenant=" + encoded;
            }
        }
        response.sendRedirect(target);
    }

    @Override
    public void destroy() {
        // nothing to release
    }

    private boolean validTrust(String supplied) {
        if (trustSecret == null || trustSecret.isEmpty()) {
            return false; // fail closed if not configured
        }
        if (supplied == null) {
            return false;
        }
        // constant-time compare to avoid timing leaks
        byte[] a = trustSecret.getBytes();
        byte[] b = supplied.getBytes();
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    private boolean isPublic(String path) {
        for (String p : publicPaths) {
            if (path.equals(p) || path.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLoginOrRoot(String path) {
        if (path == null) return false;
        return path.isEmpty()                  // /struts-demo (no trailing slash)
                || path.equals("/")
                || path.equals("/index.jsp")
                || path.startsWith("/login")    // /login.action, /login.jsp, /login
                || path.startsWith("/doLogin"); // form submit URL
    }

    private static String param(FilterConfig cfg, String name, String defaultValue) {
        String v = cfg.getInitParameter(name);
        return (v == null || v.isEmpty()) ? defaultValue : v;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
