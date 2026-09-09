package dev.ghostdrop.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FileRepository {
    Optional<TemporaryFile> findById(String id);

    Optional<TemporaryFile> reserveDownload(String id, Instant now);

    void beginScan(String storageKey);

    Optional<TemporaryFile> markAvailableAfterScan(String storageKey, Instant scannedAt);

    Optional<TemporaryFile> markInfected(String storageKey, Instant scannedAt);

    Optional<TemporaryFile> markScanFailed(String storageKey, Instant scannedAt);

    List<TemporaryFile> findExpired(Instant now);

    void delete(String id);

    void save(TemporaryFile file);
}
