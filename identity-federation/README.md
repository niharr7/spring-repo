# SSO Connector (OIDC)

Reusable Spring Boot 3 module that adds **OpenID Connect SSO** in front of
legacy applications with **zero or minimal changes** in the legacy codebase.

It is designed to work with any standards-compliant OIDC provider such as
**Azure Entra ID**, **Okta**, **Keycloak**, or **AWS Cognito**.

## Run

```powershell
# Set OIDC credentials (or place an external application.yml next to the JAR)
$env:OIDC_CLIENT_ID="<client-id>"
$env:OIDC_CLIENT_SECRET="<client-secret>"

mvn spring-boot:run
```

Open http://localhost:8081 and click **Login with your configured identity provider**.

## Endpoints

| URL              | Purpose                                              |
|------------------|------------------------------------------------------|
| `/`              | Landing page                                          |
| `/sso/login`     | Starts OAuth2 Authorization Code flow                 |
| `/sso/success`   | Post-login landing page (shows identity)              |
| `/sso/me`        | JSON identity payload                                 |
| `/sso/forward`   | Redirects authenticated user to the legacy app        |
| `/logout`        | Ends the SSO session                                  |

## Logout modes

By default, `/logout` ends only the connector's local Spring Security session.

If a customer wants identity-provider logout as well, enable OIDC RP-initiated
single logout in configuration:

```yml
sso:
	connector:
		logout:
			single-logout-enabled: true
			post-logout-redirect-uri: "{baseUrl}/"
```

Use `single-logout-enabled: true` only when the provider supports OIDC logout
and the `post-logout-redirect-uri` is registered with that provider.

## How the legacy app reads identity

After login, [`LegacySessionHandoverFilter`](src/main/java/com/example/sso/filter/LegacySessionHandoverFilter.java)
publishes the identity in two ways:

**1. HttpSession attributes** (when legacy app shares the same session/JVM)

```java
String user  = (String) session.getAttribute("SSO_USER");
String email = (String) session.getAttribute("SSO_EMAIL");
String name  = (String) session.getAttribute("SSO_NAME");
String roles = (String) session.getAttribute("SSO_ROLES");
```

**2. Request attributes** (for forward/include)

`X-SSO-User`, `X-SSO-Email`, `X-SSO-Name`, `X-SSO-Roles`
(names configurable in `application.yml`)

## Integration patterns for legacy apps

| Pattern                | How                                                                 |
|------------------------|---------------------------------------------------------------------|
| **Same JVM/WAR**       | Drop the connector classes in; share `JSESSIONID`                   |
| **Reverse proxy**      | Front legacy app with this connector; forward `X-SSO-*` headers     |
| **Servlet filter**     | Package `LegacySessionHandoverFilter` as a library and register it  |

## OIDC Provider Setup

- Redirect URI: `http://localhost:8081/login/oauth2/code/{registrationId}`
- Scopes: `openid`, `profile`, `email`
- Configure `spring.security.oauth2.client.registration.{registrationId}` and
	`spring.security.oauth2.client.provider.{registrationId}` in external config.
- Set `sso.connector.registration-id` to the same registration id.
