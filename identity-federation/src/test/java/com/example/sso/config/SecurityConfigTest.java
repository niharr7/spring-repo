package com.example.sso.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class SecurityConfigTest {

    @Test
    void localLogoutRedirectsToValidatedReturnTo() throws Exception {
        SsoProperties properties = new SsoProperties();
        SsoProperties.App app = new SsoProperties.App();
        app.setLegacyAppUrl("http://legacy-app.local/app");
        properties.getApps().put("legacy", app);

        SecurityConfig config = new SecurityConfig(properties, clientRegistrationRepository());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("returnTo", "http://legacy-app.local/app/logout-complete");
        MockHttpServletResponse response = new MockHttpServletResponse();

        config.logoutSuccessHandler().onLogoutSuccess(request, response, null);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://legacy-app.local/app/logout-complete");
    }

    @Test
    void localLogoutFallsBackToRootForUnsafeReturnTo() throws Exception {
        SsoProperties properties = new SsoProperties();

        SecurityConfig config = new SecurityConfig(properties, clientRegistrationRepository());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("returnTo", "https://evil.example/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();

        config.logoutSuccessHandler().onLogoutSuccess(request, response, null);

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void singleLogoutRedirectsToIdpWithValidatedReturnTo() throws Exception {
        SsoProperties properties = new SsoProperties();
        properties.getLogout().setSingleLogoutEnabled(true);
        properties.getLogout().setPostLogoutRedirectUri("{baseUrl}/signed-out");
        SsoProperties.App app = new SsoProperties.App();
        app.setLegacyAppUrl("http://localhost:8080/struts-demo");
        properties.getApps().put("struts", app);

        SecurityConfig config = new SecurityConfig(properties, clientRegistrationRepository());

        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token-value")
                .subject("user-1")
                .build();
        OidcUser oidcUser = new DefaultOidcUser(
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                oidcUser, oidcUser.getAuthorities(), "oidc");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("returnTo", "http://localhost:8080/struts-demo/login.action?tenant=contoso");
        MockHttpServletResponse response = new MockHttpServletResponse();

        config.logoutSuccessHandler().onLogoutSuccess(request, response, authentication);

        String redirect = response.getRedirectedUrl();
        assertThat(redirect).startsWith("https://issuer.example.com/oauth2/v1/logout");
        assertThat(redirect).contains("id_token_hint=id-token-value");
        assertThat(redirect).contains("struts-demo");
    }

    @Test
    void singleLogoutFallsBackToConfiguredUriForUnsafeReturnTo() throws Exception {
        SsoProperties properties = new SsoProperties();
        properties.getLogout().setSingleLogoutEnabled(true);
        properties.getLogout().setPostLogoutRedirectUri("https://connector.local/signed-out");

        SecurityConfig config = new SecurityConfig(properties, clientRegistrationRepository());

        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token-value")
                .subject("user-1")
                .build();
        OidcUser oidcUser = new DefaultOidcUser(
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                oidcUser, oidcUser.getAuthorities(), "oidc");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("returnTo", "https://evil.example/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();

        config.logoutSuccessHandler().onLogoutSuccess(request, response, authentication);

        String redirect = response.getRedirectedUrl();
        assertThat(redirect).startsWith("https://issuer.example.com/oauth2/v1/logout");
        assertThat(redirect).contains("connector.local");
    }

    private ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("oidc")
                .clientId("client-id")
                .clientSecret("client-secret")
                .scope("openid")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://issuer.example.com/oauth2/v1/authorize")
                .tokenUri("https://issuer.example.com/oauth2/v1/token")
                .issuerUri("https://issuer.example.com")
                .jwkSetUri("https://issuer.example.com/oauth2/v1/keys")
                .userInfoUri("https://issuer.example.com/oauth2/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("OIDC")
                .providerConfigurationMetadata(java.util.Map.of(
                        "end_session_endpoint", "https://issuer.example.com/oauth2/v1/logout"))
                .build();
        return registrationId -> registration;
    }
}