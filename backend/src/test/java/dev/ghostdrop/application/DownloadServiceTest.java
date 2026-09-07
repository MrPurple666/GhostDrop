package dev.ghostdrop.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.ghostdrop.domain.FileRepository;
import dev.ghostdrop.domain.FileStatus;
import dev.ghostdrop.domain.StorageService;
import dev.ghostdrop.domain.TemporaryFile;
import dev.ghostdrop.infrastructure.security.PasswordHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class DownloadServiceTest {
    @Test
    void reserves_only_one_final_download_under_concurrency() throws Exception {
        var repository = new ReservingRepository(new TemporaryFile("id", "uploads/key", "report.pdf", "application/pdf", 10, Instant.EPOCH, Instant.MAX, 2, 3, null, "token", FileStatus.AVAILABLE));
        var service = new DownloadService(repository, key -> "https://download.example/" + key, (password, hash) -> true, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        try (var executor = Executors.newFixedThreadPool(5)) {
            var tasks = java.util.stream.IntStream.range(0, 5).mapToObj(ignored -> executor.submit(() -> service.create("id", null).isPresent())).toList();
            assertEquals(1, tasks.stream().filter(task -> { try { return task.get(); } catch (Exception exception) { throw new AssertionError(exception); } }).count());
        }
    }

    private static final class ReservingRepository implements FileRepository {
        private TemporaryFile file;
        private ReservingRepository(TemporaryFile file) { this.file = file; }
        public synchronized Optional<TemporaryFile> findById(String id) { return Optional.ofNullable(file); }
        public void save(TemporaryFile value) { file = value; }
        public synchronized Optional<TemporaryFile> reserveDownload(String id, Instant now) {
            if (!file.canCreateDownload(now)) return Optional.empty();
            file = new TemporaryFile(file.id(), file.storageKey(), file.originalFileName(), file.contentType(), file.fileSize(), file.createdAt(), file.expiresAt(), file.downloadCount() + 1, file.maxDownloads(), file.passwordHash(), file.deletionTokenHash(), file.status());
            return Optional.of(file);
        }
    }
}
