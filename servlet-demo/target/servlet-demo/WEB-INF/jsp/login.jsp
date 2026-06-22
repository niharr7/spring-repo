<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <title>Sign in</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/login.css" />
</head>
<body>
    <div class="box">
        <h1>Sign in</h1>
        <p class="lead">Use your existing credentials or continue with SSO.</p>

        <c:if test="${not empty errorMessage}">
            <div class="errors">${errorMessage}</div>
        </c:if>

        <form id="localLoginForm" action="${pageContext.request.contextPath}/login" method="post">
            <c:if test="${not empty tenant}">
                <input type="hidden" name="tenant" value="${tenant}" />
            </c:if>

            <label for="username">Username</label>
            <input id="username" name="username" type="text" autocomplete="username" value="${username}" />
            <c:if test="${not empty usernameError}">
                <div class="field-error">${usernameError}</div>
            </c:if>

            <label for="password">Password</label>
            <input id="password" name="password" type="password" autocomplete="current-password" />
            <c:if test="${not empty passwordError}">
                <div class="field-error">${passwordError}</div>
            </c:if>

            <button class="local" type="submit">Sign in with username and password</button>
        </form>

        <div class="divider">or</div>

        <c:choose>
            <c:when test="${not empty signInHref}">
                <a id="ssoLoginLink" class="signin" href="${signInHref}">Sign in with SSO</a>
            </c:when>
            <c:otherwise>
                <div class="warn">
                    SSO is not configured. Set <code>SSO_CONNECTOR_LOGIN_URL</code> or
                    <code>SSO_CONNECTOR_BASE_URL</code> on the JVM or OS environment and restart.
                </div>
            </c:otherwise>
        </c:choose>

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
            var form = document.getElementById("localLoginForm");
            var ssoLink = document.getElementById("ssoLoginLink");

            function activate(message) {
                if (!overlay) {
                    return;
                }
                if (message) {
                    var label = overlay.querySelector(".loading-text");
                    if (label) {
                        label.textContent = message;
                    }
                }
                overlay.classList.add("active");
                overlay.setAttribute("aria-hidden", "false");
                document.body.classList.add("loading");
            }

            if (form) {
                form.addEventListener("submit", function () {
                    activate("Signing you in...");
                });
            }

            if (ssoLink) {
                ssoLink.addEventListener("click", function () {
                    activate("Redirecting to SSO...");
                });
            }
        })();
    </script>
</body>
</html>