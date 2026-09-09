package dev.ghostdrop.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.ghostdrop.domain.FileRepository;
import dev.ghostdrop.domain.TemporaryFile;
import dev.ghostdrop.infrastructure.security.PasswordHasher;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

class UploadServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void creates_pending_upload_with_generated_storage_key() {
        var repository = new InMemoryRepository();
        var service =
                new UploadService(
                        repository,
                        (key, type, size) -> "https://upload.example/" + key,
                        new PasswordHasher() {
                            public String hash(String password) {
                                return "hash:" + password;
                            }

                            public boolean verify(String password, String hash) {
                                return hash.equals("hash:" + password);
                            }
                        },
                        clock,
                        new GhostDropSettings(500, 300, 2_592_000, 900, 300));

        var result =
                service.create(
                        new CreateUploadRequest(
                                "report.pdf", "application/pdf", 10, 3_600, "secret", 3));

        assertEquals("https://upload.example/" + repository.file.storageKey(), result.uploadUrl());
        assertEquals(TemporaryFile.class, repository.file.getClass());
        assertEquals(3, repository.file.maxDownloads());
        assertEquals("hash:secret", repository.file.passwordHash());
        assertEquals("PENDING_UPLOAD", repository.file.status().name());
    }

    @Test
    void rejects_expiration_below_configured_minimum() {
        var service =
                new UploadService(
                        new InMemoryRepository(),
                        (key, type, size) -> "url",
                        new PasswordHasher() {
                            public String hash(String password) {
                                return password;
                            }

                            public boolean verify(String password, String hash) {
                                return true;
                            }
                        },
                        clock,
                        new GhostDropSettings(500, 300, 2_592_000, 900, 300));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.create(
                                new CreateUploadRequest(
                                        "report.pdf", "application/pdf", 10, 299, null, null)));
    }

    private static final class InMemoryRepository implements FileRepository {
        private TemporaryFile file;

        public Optional<TemporaryFile> findById(String id) {
            return Optional.ofNullable(file).filter(value -> value.id().equals(id));
        }

        public Optional<TemporaryFile> reserveDownload(String id, Instant now) {
            return Optional.empty();
        }

        public void beginScan(String storageKey) {}

        public Optional<TemporaryFile> markAvailableAfterScan(
                String storageKey, Instant scannedAt) {
            return Optional.empty();
        }

        public Optional<TemporaryFile> markInfected(String storageKey, Instant scannedAt) {
            return Optional.empty();
        }

        public Optional<TemporaryFile> markScanFailed(String storageKey, Instant scannedAt) {
            return Optional.empty();
        }

        public java.util.List<TemporaryFile> findExpired(Instant now) {
            return java.util.List.of();
        }

        public void save(TemporaryFile value) {
            file = value;
        }

        public void delete(String id) {
            file = null;
        }
    }
}
