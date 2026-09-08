package dev.ghostdrop.application;

import dev.ghostdrop.domain.FileRepository;
import dev.ghostdrop.domain.TemporaryFile;
import java.time.Clock;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;

public final class DownloadService {
    private final FileRepository repository;
    private final Function<TemporaryFile, String> downloadUrl;
    private final BiPredicate<String, String> passwords;
    private final Clock clock;

    public DownloadService(FileRepository repository, Function<TemporaryFile, String> downloadUrl, BiPredicate<String, String> passwords, Clock clock) {
        this.repository = repository;
        this.downloadUrl = downloadUrl;
        this.passwords = passwords;
        this.clock = clock;
    }

    public Optional<String> create(String id, String password) {
        var file = repository.findById(id).filter(value -> value.canCreateDownload(clock.instant()));
        if (file.isEmpty() || file.get().requiresPassword() && !passwords.test(password == null ? "" : password, file.get().passwordHash())) return Optional.empty();
        return repository.reserveDownload(id, clock.instant()).map(downloadUrl::apply);
    }
}
