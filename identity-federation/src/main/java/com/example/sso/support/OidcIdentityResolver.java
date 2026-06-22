package com.example.sso.support;

import com.example.sso.config.SsoProperties;
import java.util.Map;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class OidcIdentityResolver {

    public ResolvedOidcIdentity resolve(OidcUser user, SsoProperties props) {
        Map<String, Object> claims = user.getClaims();
        SsoProperties.Claims claimConfig = props.getClaims();

        String subject = firstNonBlank(user.getSubject(), claimAsString(claims, "sub"));
        String username = firstNonBlank(
                claimAsString(claims, claimConfig.getUsername()),
                user.getPreferredUsername(),
                claimAsString(claims, "preferred_username"),
                claimAsString(claims, "email"),
                subject);
        String email = firstNonBlank(
                claimAsString(claims, claimConfig.getEmail()),
                user.getEmail(),
                claimAsString(claims, "email"));
        String displayName = firstNonBlank(
                claimAsString(claims, claimConfig.getDisplayName()),
                user.getFullName(),
                claimAsString(claims, "name"),
                username,
                subject);

        return new ResolvedOidcIdentity(subject, username, email, displayName);
    }

    private static String claimAsString(Map<String, Object> claims, String claimName) {
        if (claims == null || claimName == null || claimName.isBlank()) {
            return null;
        }
        Object value = claims.get(claimName);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}