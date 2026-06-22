package com.example.servletdemo.web;

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
 * Signs out the legacy session and delegates to connector logout for SSO sessions.
 */
public class LogoutServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        String tenant = resolveTenant(request, session);
        String localLoginUrl = buildLocalLoginUrl(request, tenant);

        if (session != null) {
            session.invalidate();
        }

        response.sendRedirect(localLoginUrl);
    }

    private String resolveTenant(HttpServletRequest request, HttpSession session) {
        String tenant = trimToNull(request.getParameter("tenant"));
        if (tenant != null) {
            return tenant;
        }
        if (session == null) {
            return null;
        }
        Object tenantObj = session.getAttribute(SsoTrustFilter.SESSION_TENANT_KEY);
        return tenantObj == null ? null : trimToNull(String.valueOf(tenantObj));
    }

    private String buildLocalLoginUrl(HttpServletRequest request, String tenant) {
        StringBuilder builder = new StringBuilder();
        builder.append(request.getScheme())
                .append("://")
                .append(request.getServerName());
        if (!isDefaultPort(request)) {
            builder.append(':').append(request.getServerPort());
        }
        builder.append(request.getContextPath()).append("/login");
        if (tenant != null) {
            builder.append("?tenant=")
                    .append(urlEncode(tenant));
        }
        return builder.toString();
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 must be supported", e);
        }
    }

    private boolean isDefaultPort(HttpServletRequest request) {
        return ("http".equalsIgnoreCase(request.getScheme()) && request.getServerPort() == 80)
                || ("https".equalsIgnoreCase(request.getScheme()) && request.getServerPort() == 443);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}