package dev.ghostdrop.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ghostdrop.domain.FileRepository;
import dev.ghostdrop.domain.FileStatus;
import dev.ghostdrop.domain.TemporaryFile;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

class DownloadServiceTest {
    private static final TemporaryFile AVAILABLE =
            new TemporaryFile(
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
                    Instant.EPOCH,
                    FileStatus.AVAILABLE);
    private static final TemporaryFile ONE_LEFT =
            new TemporaryFile(
                    "id",
                    "uploads/key",
                    "report.pdf",
                    "application/pdf",
                    10,
                    Instant.EPOCH,
                    Instant.MAX,
                    2,
                    3,
                    null,
                    "token",
                    Instant.EPOCH,
                    FileStatus.AVAILABLE);

    private static DownloadService service(FileStatus status) {
        var file =
                status == FileStatus.AVAILABLE
                        ? AVAILABLE
                        : new TemporaryFile(
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
                                null,
                                status);
        return new DownloadService(
                new ReservingRepository(file),
                file_ -> "https://download.example/" + file_.storageKey(),
                (password, hash) -> true,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    @Test
    void reserves_only_one_final_download_under_concurrency() throws Exception {
        var repository = new ReservingRepository(ONE_LEFT);
        var service =
                new DownloadService(
                        repository,
                        file -> "https://download.example/" + file.storageKey(),
                        (password, hash) -> true,
                        Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        try (var executor = Executors.newFixedThreadPool(5)) {
            var tasks =
                    java.util.stream.IntStream.range(0, 5)
                            .mapToObj(
                                    ignored ->
                                            executor.submit(
                                                    () -> service.create("id", null).isPresent()))
                            .toList();
            assertEquals(
                    1,
                    tasks.stream()
                            .filter(
                                    task -> {
                                        try {
                                            return task.get();
                                        } catch (Exception exception) {
                                            throw new AssertionError(exception);
                                        }
                                    })
                            .count());
        }
    }

    @Test
    void denies_download_for_pending_scan() {
        assertTrue(service(FileStatus.PENDING_SCAN).create("id", null).isEmpty());
    }

    @Test
    void denies_download_for_infected() {
        assertTrue(service(FileStatus.INFECTED).create("id", null).isEmpty());
    }

    @Test
    void denies_download_for_scan_failed() {
        assertTrue(service(FileStatus.SCAN_FAILED).create("id", null).isEmpty());
    }

    @Test
    void denies_download_for_available_without_scan() {
        var file =
                new TemporaryFile(
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
                        null,
                        FileStatus.AVAILABLE);
        var service =
                new DownloadService(
                        new ReservingRepository(file),
                        file_ -> "https://download.example/" + file_.storageKey(),
                        (password, hash) -> true,
                        Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertTrue(service.create("id", null).isEmpty());
    }

    @Test
    void allows_download_for_available_scanned_file() {
        assertTrue(service(FileStatus.AVAILABLE).create("id", null).isPresent());
    }

    private static final class ReservingRepository implements FileRepository {
        private TemporaryFile file;

        private ReservingRepository(TemporaryFile file) {
            this.file = file;
        }

        public synchronized Optional<TemporaryFile> findById(String id) {
            return Optional.ofNullable(file);
        }

        public void save(TemporaryFile value) {
            file = value;
        }

        public synchronized Optional<TemporaryFile> reserveDownload(String id, Instant now) {
            if (!file.canCreateDownload(now)) return Optional.empty();
            file =
                    new TemporaryFile(
                            file.id(),
                            file.storageKey(),
                            file.originalFileName(),
                            file.contentType(),
                            file.fileSize(),
                            file.createdAt(),
                            file.expiresAt(),
                            file.downloadCount() + 1,
                            file.maxDownloads(),
                            file.passwordHash(),
                            file.deletionTokenHash(),
                            file.scannedAt(),
                            file.status());
            return Optional.of(file);
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

        public List<TemporaryFile> findExpired(Instant now) {
            return List.of();
        }

        public void delete(String id) {
            file = null;
        }
    }
}
