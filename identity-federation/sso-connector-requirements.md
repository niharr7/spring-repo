# SSO Connector for Legacy Applications (OIDC)

## 1. Objective

Build a reusable **SSO Connector Module** that enables **legacy applications** to support **Single Sign-On (SSO)** using **OpenID Connect (OIDC)** with standards-compliant providers such as **Microsoft Entra ID (Azure AD)**, **Okta**, **Keycloak**, and **AWS Cognito**.

The key requirement is to **minimize changes in the existing legacy codebase**.  
All authentication logic should be handled by the new module.

---

## 2. Key Design Principles

- ✅ **Zero / minimal changes** in legacy application code  
- ✅ Authentication handled completely outside legacy logic  
- ✅ Plug-and-play integration (WAR/JAR or filter-based)  
- ✅ Standards-based (OIDC with Authorization Code Flow)  
- ✅ Secure session handover to legacy app  
- ✅ Works with multiple tenants (future scope)

---

## 3. High-Level Architecture

```
Browser
   |
   v
Legacy Application (Protected URLs)
   |
   v
SSO Connector (Spring Boot 3)
   |
  v
OIDC Identity Provider
```

---

## 4. Functional Requirements

### 4.1 Authentication Handling

- Implement **OIDC Authorization Code Flow**
- Use Spring Security with OAuth2 client support

### 4.2 Session Management

- Create server-side session after successful login
- Store user identity and roles

### 4.3 Legacy Application Integration

- Provide Servlet Filter for request interception
- Inject authenticated user into session

---

## 5. OIDC Configuration (application.yml)

```yaml
server:
  port: 8080

spring:
  security:
    oauth2:
      client:
        registration:
          oidc:
            client-id: <CLIENT_ID>
            client-secret: <CLIENT_SECRET>
            scope:
              - openid
              - profile
              - email
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/oidc"
        provider:
          oidc:
            issuer-uri: https://issuer.example.com/realms/customer

sso:
  connector:
    registration-id: oidc
    provider-display-name: Customer SSO
```

---

## 6. Deliverables

- Spring Boot 3 SSO Connector module
- YAML configuration
- Integration guide for legacy apps

---

## 7. Success Criteria

- OIDC login works
- Session created
- Legacy app receives authenticated user
