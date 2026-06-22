package com.example.sso.support;

public record ResolvedOidcIdentity(
        String subject,
        String username,
        String email,
        String displayName) {
}