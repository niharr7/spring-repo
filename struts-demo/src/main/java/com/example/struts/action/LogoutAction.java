package com.example.struts.action;

import com.example.struts.sso.SsoConnectorUrls;
import com.example.struts.sso.SsoTrustFilter;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LogoutAction extends ActionSupport implements ServletRequestAware, ServletResponseAware {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(LogoutAction.class.getName());
    private HttpServletRequest request;
    private HttpServletResponse response;

    @Override
    public String execute() throws IOException {
        HttpSession session = request == null ? null : request.getSession(false);
        String tenant = request == null ? null : trimToNull(request.getParameter("tenant"));
        if (tenant == null && session != null) {
            Object tenantObj = session.getAttribute(SsoTrustFilter.SESSION_TENANT_KEY);
            tenant = tenantObj == null ? null : trimToNull(String.valueOf(tenantObj));
        }

        String localLoginUrl = buildLocalLoginUrl(tenant);
        if (session != null) {
            session.invalidate();
        }

        // Hand control to the SSO connector so it can end the connector session
        // and, when single logout is enabled, the identity-provider session too.
        // The connector redirects the browser back to returnTo (the legacy login
        // page) when single logout is disabled. When single logout is enabled the
        // connector uses its configured post-logout-redirect-uri instead.
        String connectorLogoutUrl = SsoConnectorUrls.resolveLogoutUrl();
        LOG.info("[logout] LogoutAction: tenant=" + tenant
                + ", localLoginUrl=" + localLoginUrl
                + ", connectorLogoutUrl=" + connectorLogoutUrl);
        if (connectorLogoutUrl != null) {
            String redirect = appendReturnTo(connectorLogoutUrl, localLoginUrl);
            LOG.info("[logout] LogoutAction redirecting to connector: " + redirect);
            response.sendRedirect(redirect);
            return NONE;
        }

        LOG.info("[logout] LogoutAction no connector URL, redirecting to local login: " + localLoginUrl);
        response.sendRedirect(localLoginUrl);
        return NONE;
    }

    private String appendReturnTo(String connectorLogoutUrl, String returnTo) throws IOException {
        String separator = connectorLogoutUrl.indexOf('?') >= 0 ? "&" : "?";
        return connectorLogoutUrl
                + separator
                + "returnTo="
                + URLEncoder.encode(returnTo, "UTF-8");
    }

    private String buildLocalLoginUrl(String tenant) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append(request.getScheme())
                .append("://")
                .append(request.getServerName());
        if (!isDefaultPort(request)) {
            builder.append(':').append(request.getServerPort());
        }
        builder.append(request.getContextPath()).append("/login.action");
        if (tenant != null) {
            builder.append("?tenant=")
                    .append(URLEncoder.encode(tenant, "UTF-8"));
        }
        return builder.toString();
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

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public void setServletResponse(HttpServletResponse response) {
        this.response = response;
    }
}
