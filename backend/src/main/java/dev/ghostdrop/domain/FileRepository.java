package dev.ghostdrop.domain;

import java.time.Instant;
import java.util.Optional;

public interface FileRepository {
    Optional<TemporaryFile> findById(String id);
    Optional<TemporaryFile> reserveDownload(String id, Instant now);
    boolean markAvailable(String storageKey);
    void delete(String id);
    void save(TemporaryFile file);
}
