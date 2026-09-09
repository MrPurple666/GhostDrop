package dev.ghostdrop.application;

import dev.ghostdrop.domain.FileRepository;
import dev.ghostdrop.domain.TemporaryFile;

import java.time.Clock;
import java.util.Optional;
import java.util.function.Consumer;

public final class ScanResultService {
    private final FileRepository repository;
    private final Consumer<String> deleteObject;
    private final Clock clock;

    public ScanResultService(
            FileRepository repository, Consumer<String> deleteObject, Clock clock) {
        this.repository = repository;
        this.deleteObject = deleteObject;
        this.clock = clock;
    }

    public Optional<TemporaryFile> apply(String storageKey, ScanOutcome outcome) {
        var now = clock.instant();
        return switch (outcome) {
            case CLEAN -> repository.markAvailableAfterScan(storageKey, now);
            case INFECTED ->
                    repository
                            .markInfected(storageKey, now)
                            .map(
                                    file -> {
                                        deleteObject.accept(storageKey);
                                        return file;
                                    });
            case SCAN_FAILED -> repository.markScanFailed(storageKey, now);
        };
    }
}
