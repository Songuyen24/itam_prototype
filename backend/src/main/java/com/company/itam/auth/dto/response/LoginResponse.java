package com.company.itam.auth.dto.response;

public class LoginResponse {

    private String token;
    private String type = "Bearer";
    private LoginUserInfo user;

    public LoginResponse() {}

    public LoginResponse(String token, LoginUserInfo user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public LoginUserInfo getUser() { return user; }
    public void setUser(LoginUserInfo user) { this.user = user; }
}
