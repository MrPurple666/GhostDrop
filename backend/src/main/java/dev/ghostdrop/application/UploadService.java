package dev.ghostdrop.application;

import dev.ghostdrop.domain.FileRepository;
import dev.ghostdrop.domain.FileStatus;
import dev.ghostdrop.domain.StorageService;
import dev.ghostdrop.domain.TemporaryFile;
import dev.ghostdrop.infrastructure.security.PasswordHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

public final class UploadService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final FileRepository repository;
    private final StorageService storage;
    private final PasswordHasher passwords;
    private final Clock clock;
    private final GhostDropSettings settings;

    public UploadService(FileRepository repository, StorageService storage, PasswordHasher passwords, Clock clock, GhostDropSettings settings) {
        this.repository = repository;
        this.storage = storage;
        this.passwords = passwords;
        this.clock = clock;
        this.settings = settings;
    }

    public CreateUploadResult create(CreateUploadRequest request) {
        validate(request);
        var now = clock.instant();
        var id = token(12);
        var storageKey = "uploads/" + token(24);
        var deletionToken = token(32);
        var file = new TemporaryFile(id, storageKey, request.fileName().trim(), request.contentType().trim(), request.fileSize(), now,
                now.plusSeconds(request.expiresInSeconds()), 0, request.maxDownloads(), blankToNull(request.password()) == null ? null : passwords.hash(request.password()), sha256(deletionToken), FileStatus.PENDING_UPLOAD);
        repository.save(file);
        return new CreateUploadResult(id, storage.createUploadUrl(storageKey, file.contentType(), file.fileSize()), "/d/" + id, deletionToken, file.expiresAt());
    }

    private void validate(CreateUploadRequest request) {
        if (request == null || blankToNull(request.fileName()) == null || request.fileName().length() > 255 || blankToNull(request.contentType()) == null || request.fileSize() < 1 || request.fileSize() > settings.maximumFileSizeBytes() || request.expiresInSeconds() < settings.minimumLifetimeSeconds() || request.expiresInSeconds() > settings.maximumLifetimeSeconds() || request.maxDownloads() != null && request.maxDownloads() < 1) {
            throw new IllegalArgumentException("Upload request is invalid");
        }
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

    private static String token(int bytes) {
        var value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String sha256(String value) {
        try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
}
