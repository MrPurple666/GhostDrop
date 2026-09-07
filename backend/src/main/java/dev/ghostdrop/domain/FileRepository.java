package dev.ghostdrop.domain;

import java.util.List;
import java.time.Instant;
import java.util.Optional;

public interface FileRepository {
    Optional<TemporaryFile> findById(String id);
    Optional<TemporaryFile> reserveDownload(String id, Instant now);
    boolean markAvailable(String storageKey);
    List<TemporaryFile> findExpired(Instant now);
    void delete(String id);
    void save(TemporaryFile file);
}
