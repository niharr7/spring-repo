package com.example.sso.support;

import com.example.sso.config.SsoProperties;

public record ResolvedTenant(
        String tenantId,
        String registrationId,
        String providerDisplayName,
        String legacyAppUrl,
        String appPathPrefix,
        String trustSecret,
        SsoProperties.Claims claims) {
}