<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%
    if (session.getAttribute("loggedInUser") == null) {
        response.sendRedirect(request.getContextPath() + "/login.action");
        return;
    }

    Object userObj  = session.getAttribute("loggedInUser");
    Object emailObj = session.getAttribute("ssoEmail");
    Object nameObj  = session.getAttribute("ssoName");
    Object rolesObj = session.getAttribute("ssoRoles");

    String displayName = nameObj != null && !nameObj.toString().isEmpty()
            ? nameObj.toString()
            : (userObj != null ? userObj.toString() : "User");
        Object tenantObj = session.getAttribute("ssoTenant");
        String tenant = tenantObj != null && !tenantObj.toString().isEmpty()
            ? tenantObj.toString()
            : null;
        StringBuilder logoutHrefBuilder = new StringBuilder();
        logoutHrefBuilder.append(request.getScheme())
            .append("://")
            .append(request.getServerName());
        if (!("http".equalsIgnoreCase(request.getScheme()) && request.getServerPort() == 80)
            && !("https".equalsIgnoreCase(request.getScheme()) && request.getServerPort() == 443)) {
        logoutHrefBuilder.append(':').append(request.getServerPort());
        }
        logoutHrefBuilder.append(request.getContextPath()).append("/logout.action");
        if (tenant != null) {
        logoutHrefBuilder.append("?tenant=")
            .append(java.net.URLEncoder.encode(tenant, "UTF-8"));
        }
        String logoutHref = logoutHrefBuilder.toString();

    String initials;
    String[] parts = displayName.trim().split("\\s+");
    if (parts.length >= 2) {
        initials = ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    } else if (!displayName.isEmpty()) {
        initials = displayName.substring(0, Math.min(2, displayName.length())).toUpperCase();
    } else {
        initials = "U";
    }
%>
<!DOCTYPE html>
<html>
<head>
    <title>Dashboard</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/welcome.css" />
</head>
<body>
    <div class="topbar">
        <div class="brand">Corporate Portal</div>
        <div class="user">
            <span class="avatar"><%= initials %></span>
            <div class="meta">
                <div class="name"><s:property value="#session.ssoName != null and #session.ssoName != '' ? #session.ssoName : #session.loggedInUser"/></div>
                <% if (emailObj != null) { %>
                    <div class="email"><s:property value="#session.ssoEmail"/></div>
                <% } %>
            </div>
            <a class="logout" href="<%= logoutHref %>">Logout</a>
        </div>
    </div>

    <div id="logoutLoadingOverlay" class="loading-overlay" aria-hidden="true">
        <div class="loading-card" role="status" aria-live="polite">
            <div class="spinner"></div>
            <p class="loading-text">Signing you out...</p>
        </div>
    </div>

    <div class="container">
        <h1>Welcome back, <s:property value="#session.ssoName != null and #session.ssoName != '' ? #session.ssoName : #session.loggedInUser"/>.</h1>
        <p class="subtitle">Here's a quick snapshot of your account.</p>

        <div class="grid">
            <div class="card">
                <h3>Open tasks</h3>
                <div class="value">12</div>
                <div class="hint">3 due this week</div>
            </div>
            <div class="card">
                <h3>Notifications</h3>
                <div class="value">5</div>
                <div class="hint">2 unread</div>
            </div>
            <div class="card">
                <h3>Reports</h3>
                <div class="value">8</div>
                <div class="hint">Last run today</div>
            </div>
            <div class="card">
                <h3>Status</h3>
                <div class="value" style="color:#198754;">Online</div>
                <div class="hint">All systems normal</div>
            </div>
        </div>

        <div class="panel">
            <h2>Your profile</h2>
            <dl class="kv">
                <dt>User ID</dt>
                <dd><s:property value="#session.loggedInUser"/></dd>

                <dt>Display name</dt>
                <dd><s:property value="#session.ssoName"/></dd>

                <dt>Email</dt>
                <dd><s:property value="#session.ssoEmail"/></dd>

                <dt>Roles</dt>
                <dd>
                    <%
                        if (rolesObj != null && !rolesObj.toString().isEmpty()) {
                            String[] roles = rolesObj.toString().split("[,\\s]+");
                            for (String r : roles) {
                                if (!r.isEmpty()) {
                    %>
                                <span class="badge"><%= r %></span>
                    <%
                                }
                            }
                        } else {
                    %>
                        <span style="color:#6c757d;">No roles assigned</span>
                    <% } %>
                </dd>
            </dl>
        </div>

        <div class="panel">
            <h2>Recent activity</h2>
            <ul style="margin:0; padding-left:20px; font-size:14px; line-height:1.8;">
                <li>Signed in via SSO just now</li>
                <li>Profile synced from identity provider</li>
                <li>Session established successfully</li>
            </ul>
        </div>
    </div>

    <script>
        (function () {
            var overlay = document.getElementById("logoutLoadingOverlay");
            var logoutLink = document.querySelector("a.logout");
            var delayMs = 2000;
            var isNavigating = false;
            if (!overlay || !logoutLink) {
                return;
            }

            logoutLink.addEventListener("click", function (event) {
                if (isNavigating) {
                    return;
                }

                event.preventDefault();
                isNavigating = true;
                overlay.classList.add("active");
                overlay.setAttribute("aria-hidden", "false");
                document.body.classList.add("loading");
                logoutLink.setAttribute("aria-disabled", "true");

                window.setTimeout(function () {
                    window.location.assign(logoutLink.href);
                }, delayMs);
            });
        })();
    </script>
</body>
</html>
