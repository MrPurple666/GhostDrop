package dev.ghostdrop.application;

import dev.ghostdrop.domain.FileRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.function.Consumer;

public final class DeleteFileService {
    private final FileRepository repository;
    private final Consumer<String> deleteObject;

    public DeleteFileService(FileRepository repository, Consumer<String> deleteObject) {
        this.repository = repository;
        this.deleteObject = deleteObject;
    }

    public boolean delete(String id, String token) {
        var file = repository.findById(id);
        if (file.isEmpty() || token == null || !MessageDigest.isEqual(hash(token), file.get().deletionTokenHash().getBytes(StandardCharsets.UTF_8))) return false;
        deleteObject.accept(file.get().storageKey());
        repository.delete(id);
        return true;
    }

    private static byte[] hash(String token) {
        try { return Base64.getEncoder().encode(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
}
