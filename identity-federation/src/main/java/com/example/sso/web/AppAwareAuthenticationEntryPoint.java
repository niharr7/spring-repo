package com.example.sso.web;

import com.example.sso.config.SsoProperties;
import com.example.sso.support.AppResolver;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.stereotype.Component;

/**
 * Intercepts unauthenticated requests and redirects to the app-specific login
 * entry point derived from the request path.
 *
 * <p>For example, an unauthenticated GET to {@code /erp/dashboard} will be
 * saved in the session and the browser will be redirected to
 * {@code /apps/erp/sso/login} so the correct tenant and app are resolved
 * before the OAuth flow starts. After a successful login Spring Security will
 * replay the saved request, landing the user directly on {@code /erp/dashboard}
 * rather than on the generic success page.
 *
 * <p>When no app can be matched from the path the fallback is {@code /sso/login}.
 */
@Component
public class AppAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(AppAwareAuthenticationEntryPoint.class);

    private final SsoProperties props;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    public AppAwareAuthenticationEntryPoint(SsoProperties props) {
        this.props = props;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        // Persist the original request so Spring Security can replay it after login.
        requestCache.saveRequest(request, response);

        String path = request.getRequestURI();
        String appId = resolveAppIdFromPath(path);

        if (appId != null) {
            log.debug("[SSO] Unauthenticated request for path={} → redirecting to app login for appId={}", path, appId);
            response.sendRedirect(request.getContextPath() + "/apps/" + appId + "/sso/login");
        } else {
            log.debug("[SSO] Unauthenticated request for path={} → no app matched, redirecting to generic login", path);
            response.sendRedirect(request.getContextPath() + "/sso/login");
        }
    }

    /**
     * Walks the configured apps and returns the first appId whose proxy base
     * path is a prefix of the incoming request path.
     */
    private String resolveAppIdFromPath(String path) {
        for (java.util.Map.Entry<String, SsoProperties.App> entry : props.getApps().entrySet()) {
            String appId = entry.getKey();
            SsoProperties.App app = entry.getValue();
            String proxyBasePath = AppResolver.computeProxyBasePath(
                    appId,
                    app.getProxyBasePath(),
                    app.getLegacyAppUrl());
            if (path.startsWith(proxyBasePath)) {
                return appId;
            }
        }
        return null;
    }
}
