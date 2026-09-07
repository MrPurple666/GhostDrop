package dev.ghostdrop.infrastructure.security;

public interface PasswordHasher {
    String hash(String password);
    boolean verify(String password, String hash);
}
