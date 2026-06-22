package com.example.servletdemo.web;

import com.example.servletdemo.sso.SsoConnectorUrls;
import com.example.servletdemo.sso.SsoTrustFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Serves the legacy login page and handles demo username/password sign-in.
 */
public class LoginServlet extends HttpServlet {

    private static final String VALID_USERNAME = "admin";
    private static final String VALID_PASSWORD = "admin123";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (isAuthenticated(request.getSession(false))) {
            response.sendRedirect(request.getContextPath() + "/dashboard");
            return;
        }

        prepareViewModel(request, null, null, null, null);
        request.getRequestDispatcher("/WEB-INF/jsp/login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String username = trimToNull(request.getParameter("username"));
        String password = trimToNull(request.getParameter("password"));

        String usernameError = username == null ? "Username is required." : null;
        String passwordError = password == null ? "Password is required." : null;
        if (usernameError != null || passwordError != null) {
            prepareViewModel(request, username, usernameError, passwordError, null);
            request.getRequestDispatcher("/WEB-INF/jsp/login.jsp").forward(request, response);
            return;
        }

        if (!VALID_USERNAME.equals(username) || !VALID_PASSWORD.equals(password)) {
            prepareViewModel(request, username, null, null, "Invalid username or password.");
            request.getRequestDispatcher("/WEB-INF/jsp/login.jsp").forward(request, response);
            return;
        }

        HttpSession session = request.getSession(true);
        session.setAttribute(SsoTrustFilter.SESSION_USER_KEY, username);
        session.setAttribute(SsoTrustFilter.SESSION_NAME_KEY, "Administrator");
        session.setAttribute(SsoTrustFilter.SESSION_EMAIL_KEY, "admin@legacy.local");
        session.setAttribute(SsoTrustFilter.SESSION_ROLES_KEY, "ADMIN,OPERATIONS");
        session.setAttribute(SsoTrustFilter.SESSION_AUTH_METHOD_KEY, SsoTrustFilter.AUTH_METHOD_LOCAL);

        String tenant = resolveTenant(request);
        if (tenant != null) {
            session.setAttribute(SsoTrustFilter.SESSION_TENANT_KEY, tenant);
        }

        response.sendRedirect(request.getContextPath() + "/dashboard");
    }

    private void prepareViewModel(HttpServletRequest request,
                                  String username,
                                  String usernameError,
                                  String passwordError,
                                  String errorMessage) {
        String tenant = resolveTenant(request);
        String connectorUrl = SsoConnectorUrls.resolveLoginUrl(getServletContext());
        String signInHref = buildSignInHref(connectorUrl, tenant);

        request.setAttribute("username", username == null ? "" : username);
        request.setAttribute("usernameError", usernameError);
        request.setAttribute("passwordError", passwordError);
        request.setAttribute("errorMessage", errorMessage);
        request.setAttribute("tenant", tenant);
        request.setAttribute("signInHref", signInHref);
    }

    private String buildSignInHref(String connectorUrl, String tenant) {
        if (connectorUrl == null || connectorUrl.isEmpty()) {
            return null;
        }
        if (tenant == null) {
            return connectorUrl;
        }
        return connectorUrl
                + (connectorUrl.contains("?") ? "&" : "?")
                + "tenant=" + urlEncode(tenant);
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 must be supported", e);
        }
    }

    private String resolveTenant(HttpServletRequest request) {
        String tenant = trimToNull(request.getParameter("tenant"));
        if (tenant != null) {
            return tenant;
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            Object tenantObj = session.getAttribute(SsoTrustFilter.SESSION_TENANT_KEY);
            if (tenantObj != null) {
                tenant = trimToNull(String.valueOf(tenantObj));
                if (tenant != null) {
                    return tenant;
                }
            }
        }

        return firstNonBlank(
                trimToNull(System.getProperty("sso.connector.tenant")),
                trimToNull(System.getenv("SSO_CONNECTOR_TENANT")));
    }

    private boolean isAuthenticated(HttpSession session) {
        return session != null && session.getAttribute(SsoTrustFilter.SESSION_USER_KEY) != null;
    }

    private String firstNonBlank(String first, String second) {
        return first != null ? first : second;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}