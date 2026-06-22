package com.example.sso.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sso.connector")
public class SsoProperties {

    private String legacyAppUrl;
    /** Shared secret sent as X-SSO-Trust to the legacy app's trust filter. */
    private String trustSecret;
    /** Incoming path prefix on the connector that is proxied to the legacy app. */
    private String appPathPrefix;
    private String registrationId;
    private String providerDisplayName = "OpenID Connect";
    private Headers headers = new Headers();
    private Claims claims = new Claims();
    private Logout logout = new Logout();
    private Login login = new Login();
    private TenantDiscovery tenantDiscovery = new TenantDiscovery();
    private Map<String, Tenant> tenants = new LinkedHashMap<>();
    private Map<String, App> apps = new LinkedHashMap<>();
    private List<String> publicPaths = new ArrayList<>();

    public String getLegacyAppUrl() { return legacyAppUrl; }
    public void setLegacyAppUrl(String legacyAppUrl) { this.legacyAppUrl = legacyAppUrl; }

    public String getTrustSecret() { return trustSecret; }
    public void setTrustSecret(String trustSecret) { this.trustSecret = trustSecret; }

    public String getAppPathPrefix() { return appPathPrefix; }
    public void setAppPathPrefix(String appPathPrefix) { this.appPathPrefix = appPathPrefix; }

    public String getRegistrationId() { return registrationId; }
    public void setRegistrationId(String registrationId) { this.registrationId = registrationId; }

    public String getProviderDisplayName() { return providerDisplayName; }
    public void setProviderDisplayName(String providerDisplayName) {
        this.providerDisplayName = providerDisplayName;
    }

    public Headers getHeaders() { return headers; }
    public void setHeaders(Headers headers) { this.headers = headers; }

    public Claims getClaims() { return claims; }
    public void setClaims(Claims claims) { this.claims = claims; }

    public Logout getLogout() { return logout; }
    public void setLogout(Logout logout) { this.logout = logout; }

    public Login getLogin() { return login; }
    public void setLogin(Login login) { this.login = login; }

    public TenantDiscovery getTenantDiscovery() { return tenantDiscovery; }
    public void setTenantDiscovery(TenantDiscovery tenantDiscovery) {
        this.tenantDiscovery = tenantDiscovery;
    }

    public Map<String, Tenant> getTenants() { return tenants; }
    public void setTenants(Map<String, Tenant> tenants) { this.tenants = tenants; }

    public Map<String, App> getApps() { return apps; }
    public void setApps(Map<String, App> apps) { this.apps = apps; }

    public List<String> getPublicPaths() { return publicPaths; }
    public void setPublicPaths(List<String> publicPaths) { this.publicPaths = publicPaths; }

    public static class TenantDiscovery {
        private List<String> order = new ArrayList<>(List.of("host", "path", "query", "email-domain"));
        private String queryParamName = "tenant";

        public List<String> getOrder() { return order; }
        public void setOrder(List<String> order) { this.order = order; }

        public String getQueryParamName() { return queryParamName; }
        public void setQueryParamName(String queryParamName) { this.queryParamName = queryParamName; }
    }

    public static class Logout {
        /**
         * When enabled, the connector performs OIDC RP-initiated logout
         * against the configured provider instead of only ending the local
         * connector session.
         */
        private boolean singleLogoutEnabled;

        /**
         * Post-logout redirect URI template used by Spring Security's OIDC
         * logout handler. Common values are "{baseUrl}/" or a dedicated
         * landing page registered with the identity provider.
         */
        private String postLogoutRedirectUri = "{baseUrl}/";

        public boolean isSingleLogoutEnabled() { return singleLogoutEnabled; }
        public void setSingleLogoutEnabled(boolean singleLogoutEnabled) {
            this.singleLogoutEnabled = singleLogoutEnabled;
        }

        public String getPostLogoutRedirectUri() { return postLogoutRedirectUri; }
        public void setPostLogoutRedirectUri(String postLogoutRedirectUri) {
            this.postLogoutRedirectUri = postLogoutRedirectUri;
        }
    }

    public static class Login {
        /**
         * Optional OIDC {@code prompt} parameter added to the authorization
         * request. Set to "login" to force the identity provider to ask for
         * credentials on every sign-in, even when the provider still has an
         * active session (useful after single logout so re-login is not
         * silently re-authenticated). Leave blank for default provider
         * behavior.
         */
        private String prompt = "";

        public String getPrompt() { return prompt; }
        public void setPrompt(String prompt) { this.prompt = prompt; }
    }

    public static class Tenant {
        private String registrationId;
        private String providerDisplayName;
        private String legacyAppUrl;
        private String trustSecret;
        private String appPathPrefix;
        private Claims claims = new Claims();
        private List<String> hosts = new ArrayList<>();
        private List<String> paths = new ArrayList<>();
        private List<String> queryValues = new ArrayList<>();
        private List<String> emailDomains = new ArrayList<>();

        public String getRegistrationId() { return registrationId; }
        public void setRegistrationId(String registrationId) { this.registrationId = registrationId; }

        public String getProviderDisplayName() { return providerDisplayName; }
        public void setProviderDisplayName(String providerDisplayName) {
            this.providerDisplayName = providerDisplayName;
        }

        public String getLegacyAppUrl() { return legacyAppUrl; }
        public void setLegacyAppUrl(String legacyAppUrl) { this.legacyAppUrl = legacyAppUrl; }

        public String getTrustSecret() { return trustSecret; }
        public void setTrustSecret(String trustSecret) { this.trustSecret = trustSecret; }

        public String getAppPathPrefix() { return appPathPrefix; }
        public void setAppPathPrefix(String appPathPrefix) { this.appPathPrefix = appPathPrefix; }

        public Claims getClaims() { return claims; }
        public void setClaims(Claims claims) { this.claims = claims; }

        public List<String> getHosts() { return hosts; }
        public void setHosts(List<String> hosts) { this.hosts = hosts; }

        public List<String> getPaths() { return paths; }
        public void setPaths(List<String> paths) { this.paths = paths; }

        public List<String> getQueryValues() { return queryValues; }
        public void setQueryValues(List<String> queryValues) { this.queryValues = queryValues; }

        public List<String> getEmailDomains() { return emailDomains; }
        public void setEmailDomains(List<String> emailDomains) { this.emailDomains = emailDomains; }
    }

    public static class App {
        private String displayName;
        private String legacyAppUrl;
        private String trustSecret;
        /**
         * Optional override for the connector path that proxies to this app.
         * When unset, the connector derives it from {@code legacyAppUrl}'s
         * context path (e.g. {@code /struts-demo}) and falls back to
         * {@code /apps/{appId}/proxy}.
         */
        private String proxyBasePath;

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public String getLegacyAppUrl() { return legacyAppUrl; }
        public void setLegacyAppUrl(String legacyAppUrl) { this.legacyAppUrl = legacyAppUrl; }

        public String getTrustSecret() { return trustSecret; }
        public void setTrustSecret(String trustSecret) { this.trustSecret = trustSecret; }

        public String getProxyBasePath() { return proxyBasePath; }
        public void setProxyBasePath(String proxyBasePath) { this.proxyBasePath = proxyBasePath; }
    }

    public static class Headers {
        private String user = "X-SSO-User";
        private String email = "X-SSO-Email";
        private String name = "X-SSO-Name";
        private String roles = "X-SSO-Roles";

        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getRoles() { return roles; }
        public void setRoles(String roles) { this.roles = roles; }
    }

    public static class Claims {
        private String username = "preferred_username";
        private String email = "email";
        private String displayName = "name";

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }
}
