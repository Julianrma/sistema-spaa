package com.spa.sistema_spa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AdminCredentials {

    private final String username;
    private final String password;

    public AdminCredentials(@Value("${spa.admin.username:}") String username,
                            @Value("${spa.admin.password:}") String password) {
        this.username = username;
        this.password = password;
    }

    public boolean isConfigured() {
        return !username.isBlank() && !password.isBlank();
    }

    public boolean matches(String candidateUsername, String candidatePassword) {
        return isConfigured() && username.equals(candidateUsername) && password.equals(candidatePassword);
    }
}