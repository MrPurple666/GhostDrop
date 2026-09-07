package dev.ghostdrop.domain;

import java.util.Optional;

public interface FileRepository {
    Optional<TemporaryFile> findById(String id);
    void save(TemporaryFile file);
}
