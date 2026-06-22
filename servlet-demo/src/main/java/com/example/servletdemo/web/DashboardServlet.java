package com.example.servletdemo.web;

import com.example.servletdemo.sso.SsoTrustFilter;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Renders a sample authenticated dashboard using legacy session state.
 */
public class DashboardServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SsoTrustFilter.SESSION_USER_KEY) == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String username = valueOf(session.getAttribute(SsoTrustFilter.SESSION_USER_KEY), "User");
        String displayName = valueOf(session.getAttribute(SsoTrustFilter.SESSION_NAME_KEY), username);
        String email = valueOf(session.getAttribute(SsoTrustFilter.SESSION_EMAIL_KEY), "Not available");
        String roles = valueOf(session.getAttribute(SsoTrustFilter.SESSION_ROLES_KEY), "");
        String authMethod = valueOf(session.getAttribute(SsoTrustFilter.SESSION_AUTH_METHOD_KEY), SsoTrustFilter.AUTH_METHOD_LOCAL);
        String tenant = valueOf(session.getAttribute(SsoTrustFilter.SESSION_TENANT_KEY), "default");

        request.setAttribute("username", username);
        request.setAttribute("displayName", displayName);
        request.setAttribute("email", email);
        request.setAttribute("roles", roles.isEmpty() ? new String[0] : roles.split("\\s*,\\s*"));
        request.setAttribute("authMethod", authMethod);
        request.setAttribute("tenant", tenant);
        request.setAttribute("initials", initials(displayName));

        request.getRequestDispatcher("/WEB-INF/jsp/dashboard.jsp").forward(request, response);
    }

    private String valueOf(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private String initials(String displayName) {
        String[] parts = displayName.trim().split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        }
        return displayName.substring(0, Math.min(2, displayName.length())).toUpperCase();
    }
}