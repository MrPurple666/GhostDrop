package dev.ghostdrop.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

public final class Argon2PasswordHasher implements PasswordHasher {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String hash(String password) {
        var salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(salt) + "$" + Base64.getUrlEncoder().withoutPadding().encodeToString(derive(password, salt));
    }

    @Override
    public boolean verify(String password, String hash) {
        if (password == null || hash == null) return false;
        var parts = hash.split("\\$", -1);
        if (parts.length != 2) return false;
        try {
            var salt = Base64.getUrlDecoder().decode(parts[0]);
            return java.security.MessageDigest.isEqual(derive(password, salt), Base64.getUrlDecoder().decode(parts[1]));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt) {
        var output = new byte[32];
        var parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13).withSalt(salt).withIterations(3).withMemoryAsKB(65_536).withParallelism(1).build();
        var generator = new Argon2BytesGenerator();
        generator.init(parameters);
        generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), output);
        return output;
    }
}
