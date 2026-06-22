# servlet-demo

Plain Servlet/JSP sample application that supports both local username/password login and SSO connector login.

## Features

- Local demo login with hardcoded credentials
- SSO entry point that links to the external connector
- Trust-header filter that accepts authenticated connector requests
- Sample dashboard page after login
- SSO-aware logout that returns to the connector when needed

## Local credentials

- Username: `admin`
- Password: `admin123`

## Connector configuration

The legacy app resolves connector URLs in this order:

1. Servlet context init-param
2. JVM system property
3. OS environment variable
4. Build from connector base URL and default path

Supported settings:

- Login URL: `sso.connector.login-url` or `SSO_CONNECTOR_LOGIN_URL`
- Logout URL: `sso.connector.logout-url` or `SSO_CONNECTOR_LOGOUT_URL`
- Base URL: `sso.connector.base-url` or `SSO_CONNECTOR_BASE_URL`
- Login path: `sso.connector.login-path` or `SSO_CONNECTOR_LOGIN_PATH`
- Logout path: `sso.connector.logout-path` or `SSO_CONNECTOR_LOGOUT_PATH`

Examples:

```powershell
$env:SSO_CONNECTOR_BASE_URL = "http://localhost:8081"
$env:SSO_CONNECTOR_TENANT = "acme"
```

or:

```powershell
$env:SSO_CONNECTOR_LOGIN_URL = "http://localhost:8081/sso/login"
$env:SSO_CONNECTOR_LOGOUT_URL = "http://localhost:8081/logout"
```

To complete full SSO integration, configure the connector to proxy the legacy app under `/servlet-demo` and to send the same shared trust secret in `X-SSO-Trust`.

## Build and run

```powershell
Set-Location "c:\Projects\AI-POC\servlet-demo"
mvn package cargo:run
```

The app will run at `http://localhost:8082/servlet-demo`.

## Notes

- Static resources under `/css`, `/images`, `/js`, and `/static` bypass the auth filter.
- The dashboard is sample data only.
- The trust filter writes legacy-friendly session keys: `loggedInUser`, `ssoEmail`, `ssoName`, `ssoRoles`, `authMethod`, and `ssoTenant`.