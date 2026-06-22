package com.example.sso.support;

import com.example.sso.config.SsoProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TenantResolver {

    public static final String SESSION_TENANT_ID = "SSO_TENANT_ID";

    private final SsoProperties props;

    public TenantResolver(SsoProperties props) {
        this.props = props;
    }

    public Optional<ResolvedTenant> resolveFromRequest(HttpServletRequest request) {
        List<String> order = props.getTenantDiscovery().getOrder();
        if (order == null || order.isEmpty()) {
            return defaultTenant();
        }

        for (String resolverName : order) {
            Optional<ResolvedTenant> resolved = switch (normalizeKey(resolverName)) {
                case "host" -> resolveFromHost(request);
                case "path" -> resolveFromPath(request);
                case "query" -> resolveFromQuery(request);
                default -> Optional.empty();
            };
            if (resolved.isPresent()) {
                return resolved;
            }
        }

        return defaultTenant();
    }

    public Optional<ResolvedTenant> resolveFromEmail(String email) {
        String normalizedEmail = trimToNull(email);
        if (normalizedEmail == null) {
            return Optional.empty();
        }

        int atIndex = normalizedEmail.lastIndexOf('@');
        if (atIndex < 0 || atIndex == normalizedEmail.length() - 1) {
            return Optional.empty();
        }

        String domain = normalizeKey(normalizedEmail.substring(atIndex + 1));
        for (Map.Entry<String, SsoProperties.Tenant> entry : props.getTenants().entrySet()) {
            if (matchesAlias(domain, entry.getValue().getEmailDomains(), entry.getKey())) {
                return Optional.of(toResolvedTenant(entry.getKey(), entry.getValue()));
            }
        }

        return Optional.empty();
    }

    public Optional<ResolvedTenant> resolveFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }

        Object tenantId = session.getAttribute(SESSION_TENANT_ID);
        if (tenantId == null) {
            return Optional.empty();
        }

        return resolveByTenantId(String.valueOf(tenantId));
    }

    public Optional<ResolvedTenant> resolveFromRegistrationId(String registrationId) {
        String normalizedRegistrationId = trimToNull(registrationId);
        if (normalizedRegistrationId == null) {
            return Optional.empty();
        }

        for (Map.Entry<String, SsoProperties.Tenant> entry : props.getTenants().entrySet()) {
            if (normalizedRegistrationId.equals(entry.getValue().getRegistrationId())) {
                return Optional.of(toResolvedTenant(entry.getKey(), entry.getValue()));
            }
        }

        ResolvedTenant fallback = defaultTenant().orElse(null);
        if (fallback != null && normalizedRegistrationId.equals(fallback.registrationId())) {
            return Optional.of(fallback);
        }
        return Optional.empty();
    }

    public Optional<ResolvedTenant> defaultTenant() {
        if (!props.getTenants().isEmpty()) {
            return Optional.empty();
        }

        String registrationId = trimToNull(props.getRegistrationId());
        if (registrationId == null) {
            return Optional.empty();
        }

        return Optional.of(new ResolvedTenant(
                "default",
                registrationId,
                firstNonBlank(props.getProviderDisplayName(), registrationId),
                trimToNull(props.getLegacyAppUrl()),
                trimToNull(props.getAppPathPrefix()),
                trimToNull(props.getTrustSecret()),
                props.getClaims()));
    }

    public ResolvedTenant resolveForAuthenticatedRequest(HttpServletRequest request, String registrationId) {
        return resolveFromSession(request)
                .or(() -> resolveFromRegistrationId(registrationId))
                .or(() -> defaultTenant())
                .orElseThrow(() -> new IllegalStateException("Unable to resolve tenant for authenticated request"));
    }

    public void rememberTenant(HttpServletRequest request, ResolvedTenant tenant) {
        request.getSession(true).setAttribute(SESSION_TENANT_ID, tenant.tenantId());
    }

    private Optional<ResolvedTenant> resolveFromHost(HttpServletRequest request) {
        String host = request.getServerName();
        if (trimToNull(host) == null) {
            return Optional.empty();
        }

        String normalizedHost = normalizeHost(host);
        for (Map.Entry<String, SsoProperties.Tenant> entry : props.getTenants().entrySet()) {
            if (matchesAlias(normalizedHost, entry.getValue().getHosts(), entry.getKey())) {
                return Optional.of(toResolvedTenant(entry.getKey(), entry.getValue()));
            }
        }
        return Optional.empty();
    }

    private Optional<ResolvedTenant> resolveFromPath(HttpServletRequest request) {
        String path = trimToNull(request.getRequestURI());
        if (path == null) {
            return Optional.empty();
        }

        String[] segments = path.split("/");
        for (String segment : segments) {
            String normalizedSegment = normalizeKey(segment);
            if (normalizedSegment == null) {
                continue;
            }

            for (Map.Entry<String, SsoProperties.Tenant> entry : props.getTenants().entrySet()) {
                if (matchesAlias(normalizedSegment, entry.getValue().getPaths(), entry.getKey())) {
                    return Optional.of(toResolvedTenant(entry.getKey(), entry.getValue()));
                }
            }
        }

        return Optional.empty();
    }

    private Optional<ResolvedTenant> resolveFromQuery(HttpServletRequest request) {
        String paramName = trimToNull(props.getTenantDiscovery().getQueryParamName());
        if (paramName == null) {
            return Optional.empty();
        }

        String queryValue = normalizeKey(request.getParameter(paramName));
        if (queryValue == null) {
            return Optional.empty();
        }

        for (Map.Entry<String, SsoProperties.Tenant> entry : props.getTenants().entrySet()) {
            if (matchesAlias(queryValue, entry.getValue().getQueryValues(), entry.getKey())) {
                return Optional.of(toResolvedTenant(entry.getKey(), entry.getValue()));
            }
        }

        return Optional.empty();
    }

    private Optional<ResolvedTenant> resolveByTenantId(String tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }

        SsoProperties.Tenant tenant = props.getTenants().get(tenantId);
        if (tenant == null) {
            return Optional.empty();
        }

        return Optional.of(toResolvedTenant(tenantId, tenant));
    }

    private ResolvedTenant toResolvedTenant(String tenantId, SsoProperties.Tenant tenant) {
        return new ResolvedTenant(
                tenantId,
                firstNonBlank(tenant.getRegistrationId(), props.getRegistrationId()),
                firstNonBlank(tenant.getProviderDisplayName(), props.getProviderDisplayName(), tenantId),
                firstNonBlank(tenant.getLegacyAppUrl(), props.getLegacyAppUrl()),
                firstNonBlank(tenant.getAppPathPrefix(), props.getAppPathPrefix()),
                firstNonBlank(tenant.getTrustSecret(), props.getTrustSecret()),
                tenant.getClaims() == null ? props.getClaims() : tenant.getClaims());
    }

    private boolean matchesAlias(String candidate, List<String> aliases, String tenantId) {
        if (candidate == null) {
            return false;
        }

        for (String alias : aliases) {
            if (candidate.equals(normalizeKey(alias))) {
                return true;
            }
        }

        return candidate.equals(normalizeKey(tenantId));
    }

    private static String normalizeHost(String host) {
        String normalized = normalizeKey(host);
        if (normalized == null) {
            return null;
        }
        int colonIndex = normalized.indexOf(':');
        return colonIndex >= 0 ? normalized.substring(0, colonIndex) : normalized;
    }

    private static String normalizeKey(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (trimToNull(value) != null) {
                return value;
            }
        }
        return null;
    }
}