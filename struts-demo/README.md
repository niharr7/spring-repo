# Struts 2 Demo — Login + Sample Page

A minimal Struts 2 web application with a login page and a post-login sample (welcome) page. Credentials are hardcoded for demo purposes.

## Credentials
- **Username:** `admin`
- **Password:** `admin123`

## Project layout
```
struts-demo/
├── pom.xml
└── src/main/
    ├── java/com/example/struts/action/
    │   ├── LoginAction.java
    │   └── LogoutAction.java
    ├── resources/
    │   └── struts.xml
    └── webapp/
        ├── index.jsp
        └── WEB-INF/
            ├── web.xml
            └── jsp/
                ├── login.jsp
                └── welcome.jsp
```

## Build & Run

### Option 1 — Run with embedded Tomcat (quickest)
```powershell
mvn clean tomcat7:run
```
Then open: http://localhost:8080/struts-demo/

### Option 2 — Build WAR and deploy
```powershell
mvn clean package
```
Deploy `target/struts-demo.war` to any servlet container (Tomcat 8/9, Jetty, etc.).

## Flow
1. `/` → redirects to `/login.action`
2. Submit form → `doLogin` (`LoginAction`) validates against hardcoded credentials.
3. On success → redirects to `/welcome.action` (sample page).
4. On failure → returns to login page with an error message.
5. `Logout` clears the session and returns to login.

## Notes
- Credentials are intentionally hardcoded for this demo. Replace `LoginAction` with a real authentication mechanism (DB / LDAP / OAuth) for production.
- Session attribute `loggedInUser` gates access to the welcome page.
