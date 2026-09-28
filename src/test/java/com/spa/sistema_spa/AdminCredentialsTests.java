package com.spa.sistema_spa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdminCredentialsTests {

    @Test
    void missingCredentialsFailClosed() {
        AdminCredentials credentials = new AdminCredentials("", "");

        assertFalse(credentials.isConfigured());
        assertFalse(credentials.matches("admin", "1234"));
    }

    @Test
    void onlyConfiguredCredentialsAuthenticate() {
        AdminCredentials credentials = new AdminCredentials("operator", "a-strong-password");

        assertTrue(credentials.isConfigured());
        assertTrue(credentials.matches("operator", "a-strong-password"));
        assertFalse(credentials.matches("operator", "wrong-password"));
    }
}