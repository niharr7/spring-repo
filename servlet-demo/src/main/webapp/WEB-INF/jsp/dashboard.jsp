<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%
    Object tenantObj = request.getAttribute("tenant");
    String tenant = tenantObj == null ? null : tenantObj.toString();
    StringBuilder logoutHrefBuilder = new StringBuilder();
    logoutHrefBuilder.append(request.getScheme())
            .append("://")
            .append(request.getServerName());
    if (!("http".equalsIgnoreCase(request.getScheme()) && request.getServerPort() == 80)
            && !("https".equalsIgnoreCase(request.getScheme()) && request.getServerPort() == 443)) {
        logoutHrefBuilder.append(':').append(request.getServerPort());
    }
    logoutHrefBuilder.append(request.getContextPath()).append("/logout");
    if (tenant != null && !tenant.isEmpty()) {
        logoutHrefBuilder.append("?tenant=")
                .append(java.net.URLEncoder.encode(tenant, "UTF-8"));
    }
    String logoutHref = logoutHrefBuilder.toString();
%>
<!DOCTYPE html>
<html>
<head>
    <title>Dashboard</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard.css" />
</head>
<body>
    <div class="topbar">
        <div class="brand">Servlet Demo &mdash; Corporate Portal</div>
        <div class="user">
            <span class="avatar">${initials}</span>
            <div class="meta">
                <div class="name">${displayName}</div>
                <div class="email">${email}</div>
            </div>
            <a id="logoutLink" class="logout" href="<%= logoutHref %>">Logout</a>
        </div>
    </div>

    <div id="logoutLoadingOverlay" class="loading-overlay" aria-hidden="true">
        <div class="loading-card" role="status" aria-live="polite">
            <div class="spinner"></div>
            <p class="loading-text">Signing you out...</p>
        </div>
    </div>

    <div class="container">
        <h1>Welcome back, ${displayName}.</h1>
        <p class="subtitle">Your legacy app session is active and ready for work.</p>

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
                <div class="hint">Last refresh today</div>
            </div>
            <div class="card">
                <h3>Status</h3>
                <div class="value status-online">Online</div>
                <div class="hint">Session established successfully</div>
            </div>
        </div>

        <div class="panel">
            <h2>Your profile</h2>
            <dl class="kv">
                <dt>User ID</dt>
                <dd>${username}</dd>

                <dt>Display name</dt>
                <dd>${displayName}</dd>

                <dt>Email</dt>
                <dd>${email}</dd>

                <dt>Tenant</dt>
                <dd>${tenant}</dd>

                <dt>Authentication</dt>
                <dd>${authMethod}</dd>

                <dt>Roles</dt>
                <dd>
                    <c:choose>
                        <c:when test="${not empty roles}">
                            <c:forEach items="${roles}" var="role">
                                <span class="badge">${role}</span>
                            </c:forEach>
                        </c:when>
                        <c:otherwise>
                            <span class="muted">No roles assigned</span>
                        </c:otherwise>
                    </c:choose>
                </dd>
            </dl>
        </div>

        <div class="panel">
            <h2>Recent activity</h2>
            <ul class="activity-list">
                <li>Signed in through the legacy login experience</li>
                <li>Connector-ready SSO entry point available on the login screen</li>
                <li>Session attributes prepared for downstream legacy pages</li>
            </ul>
        </div>
    </div>

    <script>
        (function () {
            var overlay = document.getElementById("logoutLoadingOverlay");
            var logoutLink = document.getElementById("logoutLink");
            if (!overlay || !logoutLink) {
                return;
            }

            logoutLink.addEventListener("click", function () {
                overlay.classList.add("active");
                overlay.setAttribute("aria-hidden", "false");
                document.body.classList.add("loading");
            });
        })();
    </script>
</body>
</html>