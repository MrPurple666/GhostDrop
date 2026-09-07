package dev.ghostdrop.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TemporaryFileTest {
    @Test
    void rejects_download_when_limit_is_reached() {
        var file = new TemporaryFile("id", "uploads/key", "report.pdf", "application/pdf", 1,
                Instant.EPOCH, Instant.MAX, 3, 3, null, "token", FileStatus.AVAILABLE);

        assertFalse(file.canCreateDownload(Instant.EPOCH));
    }

    @Test
    void accepts_download_when_available_not_expired_and_under_limit() {
        var file = new TemporaryFile("id", "uploads/key", "report.pdf", "application/pdf", 1,
                Instant.EPOCH, Instant.MAX, 2, 3, null, "token", FileStatus.AVAILABLE);

        assertTrue(file.canCreateDownload(Instant.EPOCH));
    }
}
