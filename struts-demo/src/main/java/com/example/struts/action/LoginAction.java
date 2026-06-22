package com.example.struts.action;

import com.example.struts.sso.SsoTrustFilter;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.interceptor.SessionAware;

import java.util.Map;

public class LoginAction extends ActionSupport implements SessionAware {

    private static final long serialVersionUID = 1L;

    // Hardcoded credentials (for demo only)
    private static final String VALID_USERNAME = "admin";
    private static final String VALID_PASSWORD = "admin123";

    private String username;
    private String password;
    private Map<String, Object> session;

    @Override
    public String execute() {
        if (VALID_USERNAME.equals(username) && VALID_PASSWORD.equals(password)) {
            session.put(SsoTrustFilter.SESSION_USER_KEY, username);
            session.put(SsoTrustFilter.SESSION_AUTH_METHOD_KEY, SsoTrustFilter.AUTH_METHOD_LOCAL);
            session.remove(SsoTrustFilter.SESSION_EMAIL_KEY);
            session.remove(SsoTrustFilter.SESSION_NAME_KEY);
            session.remove(SsoTrustFilter.SESSION_ROLES_KEY);
            return SUCCESS;
        }
        addActionError("Invalid username or password.");
        return ERROR;
    }

    @Override
    public void validate() {
        if (username == null || username.trim().isEmpty()) {
            addFieldError("username", "Username is required.");
        }
        if (password == null || password.trim().isEmpty()) {
            addFieldError("password", "Password is required.");
        }
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    @Override
    public void setSession(Map<String, Object> session) { this.session = session; }
}
