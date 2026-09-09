package dev.ghostdrop.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ghostdrop.domain.FileRepository;
import dev.ghostdrop.domain.FileStatus;
import dev.ghostdrop.domain.TemporaryFile;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class ScanResultServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC);

    private static TemporaryFile file(FileStatus status, Instant scannedAt) {
        return new TemporaryFile(
                "id",
                "uploads/key",
                "report.pdf",
                "application/pdf",
                10,
                Instant.EPOCH,
                Instant.MAX,
                0,
                null,
                null,
                "token",
                scannedAt,
                status);
    }

    @Test
    void clean_result_moves_pending_scan_to_available() {
        var repository = new InMemoryRepository(file(FileStatus.PENDING_SCAN, null));
        var service = new ScanResultService(repository, key -> {}, clock);

        var result = service.apply("uploads/key", ScanOutcome.CLEAN);

        assertTrue(result.isPresent());
        assertEquals(FileStatus.AVAILABLE, result.get().status());
        assertEquals(clock.instant(), result.get().scannedAt());
    }

    @Test
    void infected_result_moves_pending_scan_to_infected_and_deletes_object() {
        var repository = new InMemoryRepository(file(FileStatus.PENDING_SCAN, null));
        var deleted = new ArrayList<String>();
        var service = new ScanResultService(repository, deleted::add, clock);

        var result = service.apply("uploads/key", ScanOutcome.INFECTED);

        assertTrue(result.isPresent());
        assertEquals(FileStatus.INFECTED, result.get().status());
        assertEquals(List.of("uploads/key"), deleted);
    }

    @Test
    void failed_result_moves_pending_scan_to_scan_failed_without_deleting() {
        var repository = new InMemoryRepository(file(FileStatus.PENDING_SCAN, null));
        var deleted = new ArrayList<String>();
        var service = new ScanResultService(repository, deleted::add, clock);

        var result = service.apply("uploads/key", ScanOutcome.SCAN_FAILED);

        assertTrue(result.isPresent());
        assertEquals(FileStatus.SCAN_FAILED, result.get().status());
        assertTrue(deleted.isEmpty());
    }

    @Test
    void clean_result_accepts_upload_confirm_event_ordering() {
        var repository = new InMemoryRepository(file(FileStatus.PENDING_UPLOAD, null));
        var service = new ScanResultService(repository, key -> {}, clock);

        var result = service.apply("uploads/key", ScanOutcome.CLEAN);

        assertTrue(result.isPresent());
        assertEquals(FileStatus.AVAILABLE, result.get().status());
    }

    @Test
    void delayed_clean_after_infected_is_ignored() {
        var repository = new InMemoryRepository(file(FileStatus.INFECTED, clock.instant()));
        var service = new ScanResultService(repository, key -> {}, clock);

        var result = service.apply("uploads/key", ScanOutcome.CLEAN);

        assertTrue(result.isEmpty());
        assertEquals(FileStatus.INFECTED, repository.byKey("uploads/key").orElseThrow().status());
    }

    @Test
    void delayed_infected_after_available_is_ignored() {
        var repository = new InMemoryRepository(file(FileStatus.AVAILABLE, clock.instant()));
        var deleted = new ArrayList<String>();
        var service = new ScanResultService(repository, deleted::add, clock);

        var result = service.apply("uploads/key", ScanOutcome.INFECTED);

        assertTrue(result.isEmpty());
        assertTrue(deleted.isEmpty());
        assertEquals(FileStatus.AVAILABLE, repository.byKey("uploads/key").orElseThrow().status());
    }

    @Test
    void expired_file_never_becomes_available() {
        var expired =
                new TemporaryFile(
                        "id",
                        "uploads/key",
                        "report.pdf",
                        "application/pdf",
                        10,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        0,
                        null,
                        null,
                        "token",
                        null,
                        FileStatus.PENDING_SCAN);
        var repository = new InMemoryRepository(expired);
        var service = new ScanResultService(repository, key -> {}, clock);

        var result = service.apply("uploads/key", ScanOutcome.CLEAN);

        assertTrue(result.isEmpty());
        assertFalse(
                repository.byKey("uploads/key").orElseThrow().canCreateDownload(clock.instant()));
    }

    @Test
    void duplicate_clean_is_idempotent() {
        var repository = new InMemoryRepository(file(FileStatus.AVAILABLE, clock.instant()));
        var service = new ScanResultService(repository, key -> {}, clock);

        var result = service.apply("uploads/key", ScanOutcome.CLEAN);

        assertTrue(result.isEmpty());
    }

    @Test
    void unknown_key_is_ignored() {
        var repository = new InMemoryRepository(file(FileStatus.PENDING_SCAN, null));
        var service = new ScanResultService(repository, key -> {}, clock);

        assertTrue(service.apply("uploads/other", ScanOutcome.CLEAN).isEmpty());
    }

    private static final class InMemoryRepository implements FileRepository {
        private TemporaryFile file;

        private InMemoryRepository(TemporaryFile file) {
            this.file = file;
        }

        private Optional<TemporaryFile> byKey(String storageKey) {
            return Optional.ofNullable(file).filter(value -> value.storageKey().equals(storageKey));
        }

        private Optional<TemporaryFile> terminal(
                String storageKey, FileStatus status, Instant now) {
            return byKey(storageKey)
                    .filter(
                            value ->
                                    (value.status() == FileStatus.PENDING_UPLOAD
                                                    || value.status() == FileStatus.PENDING_SCAN)
                                            && value.expiresAt().isAfter(now))
                    .map(
                            value -> {
                                file =
                                        new TemporaryFile(
                                                value.id(),
                                                value.storageKey(),
                                                value.originalFileName(),
                                                value.contentType(),
                                                value.fileSize(),
                                                value.createdAt(),
                                                value.expiresAt(),
                                                value.downloadCount(),
                                                value.maxDownloads(),
                                                value.passwordHash(),
                                                value.deletionTokenHash(),
                                                now,
                                                status);
                                return file;
                            });
        }

        public Optional<TemporaryFile> findById(String id) {
            return Optional.ofNullable(file).filter(value -> value.id().equals(id));
        }

        public Optional<TemporaryFile> reserveDownload(String id, Instant now) {
            return Optional.empty();
        }

        public void beginScan(String storageKey) {}

        public Optional<TemporaryFile> markAvailableAfterScan(
                String storageKey, Instant scannedAt) {
            return terminal(storageKey, FileStatus.AVAILABLE, scannedAt);
        }

        public Optional<TemporaryFile> markInfected(String storageKey, Instant scannedAt) {
            return terminal(storageKey, FileStatus.INFECTED, scannedAt);
        }

        public Optional<TemporaryFile> markScanFailed(String storageKey, Instant scannedAt) {
            return terminal(storageKey, FileStatus.SCAN_FAILED, scannedAt);
        }

        public List<TemporaryFile> findExpired(Instant now) {
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
