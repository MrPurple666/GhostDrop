package dev.ghostdrop.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Argon2PasswordHasherTest {
    @Test
    void verifies_only_the_original_password() {
        var hasher = new Argon2PasswordHasher();
        var hash = hasher.hash("correct horse battery staple");

        assertTrue(hasher.verify("correct horse battery staple", hash));
        assertFalse(hasher.verify("wrong password", hash));
    }
}
