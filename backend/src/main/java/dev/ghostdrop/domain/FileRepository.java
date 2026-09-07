package dev.ghostdrop.domain;

import java.time.Instant;
import java.util.Optional;

public interface FileRepository {
    Optional<TemporaryFile> findById(String id);
    Optional<TemporaryFile> reserveDownload(String id, Instant now);
    void save(TemporaryFile file);
}
