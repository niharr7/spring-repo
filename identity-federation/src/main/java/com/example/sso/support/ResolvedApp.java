package com.example.sso.support;

public record ResolvedApp(
        String appId,
        String displayName,
        String legacyAppUrl,
        String trustSecret,
        String proxyBasePath) {
}