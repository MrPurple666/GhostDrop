package dev.ghostdrop.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.time.Instant;

class TemporaryFileTest {
    @Test
    void rejects_download_when_limit_is_reached() {
        var file =
                new TemporaryFile(
                        "id",
                        "uploads/key",
                        "report.pdf",
                        "application/pdf",
                        1,
                        Instant.EPOCH,
                        Instant.MAX,
                        3,
                        3,
                        null,
                        "token",
                        Instant.EPOCH,
                        FileStatus.AVAILABLE);

        assertFalse(file.canCreateDownload(Instant.EPOCH));
    }

    @Test
    void accepts_download_when_available_not_expired_and_under_limit() {
        var file =
                new TemporaryFile(
                        "id",
                        "uploads/key",
                        "report.pdf",
                        "application/pdf",
                        1,
                        Instant.EPOCH,
                        Instant.MAX,
                        2,
                        3,
                        null,
                        "token",
                        Instant.EPOCH,
                        FileStatus.AVAILABLE);

        assertTrue(file.canCreateDownload(Instant.EPOCH));
    }

    @Test
    void rejects_download_when_available_but_never_scanned() {
        var file =
                new TemporaryFile(
                        "id",
                        "uploads/key",
                        "report.pdf",
                        "application/pdf",
                        1,
                        Instant.EPOCH,
                        Instant.MAX,
                        0,
                        null,
                        null,
                        "token",
                        null,
                        FileStatus.AVAILABLE);

        assertFalse(file.canCreateDownload(Instant.EPOCH));
    }
}
