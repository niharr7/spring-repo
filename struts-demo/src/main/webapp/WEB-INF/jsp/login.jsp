<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.example.struts.sso.SsoConnectorUrls" %>
<%@ page import="com.example.struts.sso.SsoTrustFilter" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%!
    private static String esc(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&':  sb.append("&amp;");  break;
                case '<':  sb.append("&lt;");   break;
                case '>':  sb.append("&gt;");   break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&#39;");  break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }
%>
<%
    // The login page itself is tenant-neutral and identical for all clients.
    // We still forward the tenant hint (from ?tenant= or SSO_CONNECTOR_TENANT)
    // to the connector so the correct IdP is selected after Sign in.
    String tenant = request.getParameter("tenant");
    if (tenant == null || tenant.trim().isEmpty()) {
        Object sessionTenant = session == null ? null : session.getAttribute(SsoTrustFilter.SESSION_TENANT_KEY);
        if (sessionTenant != null) {
            tenant = String.valueOf(sessionTenant);
        }
    }
    if (tenant == null || tenant.trim().isEmpty()) {
        tenant = System.getProperty("sso.connector.tenant",
                System.getenv("SSO_CONNECTOR_TENANT"));
    }

    String connectorUrl = SsoConnectorUrls.resolveLoginUrl(null);
     System.out.println("[login.jsp] connectorUrl=" + connectorUrl);
    String signInHref;
    if (connectorUrl == null || connectorUrl.isEmpty()) {
        signInHref = null;
    } else if (tenant == null || tenant.trim().isEmpty()) {
        signInHref = connectorUrl;
    } else {
        signInHref = connectorUrl
                + (connectorUrl.contains("?") ? "&" : "?")
                + "tenant=" + java.net.URLEncoder.encode(tenant.trim(), "UTF-8");
    }
    System.out.println("[login.jsp] signInHref=" + signInHref);

    String hrefAttr = signInHref == null ? null : esc(signInHref);
    System.out.println("[login.jsp] hrefAttr=" + hrefAttr);

%>
<!DOCTYPE html>
<html>
<head>
    <title>Sign in</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/login.css" />
</head>
<body>
    <div class="box">
        <h1>Sign in</h1>
        <p class="lead">Use your existing credentials or continue with SSO.</p>

        <s:if test="hasActionErrors() || hasFieldErrors()">
            <div class="errors">
                <s:actionerror />
                <s:fielderror />
            </div>
        </s:if>

        <form action="<s:url action='doLogin'/>" method="post">
            <label for="username">Username</label>
            <input id="username" name="username" type="text" autocomplete="username" />

            <label for="password">Password</label>
            <input id="password" name="password" type="password" autocomplete="current-password" />

            <button class="local" type="submit">Sign in with username and password</button>
        </form>

        <div class="divider">or</div>

        <% if (hrefAttr != null) { %>
            <a class="signin" href="<%= hrefAttr %>">Sign in with SSO</a>
        <% } else { %>
            <div class="warn">
                SSO is not configured. Set
                <code>SSO_CONNECTOR_LOGIN_URL</code> or
                <code>SSO_CONNECTOR_BASE_URL</code> on the JVM and restart.
            </div>
        <% } %>

        <div class="footer">Need help? Contact your IT support desk.</div>
    </div>

    <div id="loginLoadingOverlay" class="loading-overlay" aria-hidden="true">
        <div class="loading-card" role="status" aria-live="polite">
            <div class="spinner"></div>
            <p class="loading-text">Signing you in...</p>
        </div>
    </div>

    <script>
        (function () {
            var overlay = document.getElementById("loginLoadingOverlay");
            var form = document.querySelector("form[action]");
            var submitButton = form ? form.querySelector("button[type='submit']") : null;
            var delayMs = 2000;
            var isSubmitting = false;
            if (!overlay || !form) {
                return;
            }

            form.addEventListener("submit", function (event) {
                if (isSubmitting) {
                    return;
                }

                event.preventDefault();
                isSubmitting = true;
                overlay.classList.add("active");
                overlay.setAttribute("aria-hidden", "false");
                document.body.classList.add("loading");
                if (submitButton) {
                    submitButton.disabled = true;
                }

                window.setTimeout(function () {
                    HTMLFormElement.prototype.submit.call(form);
                }, delayMs);
            });
        })();
    </script>
</body>
</html>
