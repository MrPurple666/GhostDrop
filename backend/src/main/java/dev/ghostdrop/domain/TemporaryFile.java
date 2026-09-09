package dev.ghostdrop.domain;

import java.time.Instant;
import java.util.Objects;

public record TemporaryFile(
        String id,
        String storageKey,
        String originalFileName,
        String contentType,
        long fileSize,
        Instant createdAt,
        Instant expiresAt,
        int downloadCount,
        Integer maxDownloads,
        String passwordHash,
        String deletionTokenHash,
        Instant scannedAt,
        FileStatus status) {

    public TemporaryFile {
        Objects.requireNonNull(id);
        Objects.requireNonNull(storageKey);
        Objects.requireNonNull(originalFileName);
        Objects.requireNonNull(contentType);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(expiresAt);
        Objects.requireNonNull(deletionTokenHash);
        Objects.requireNonNull(status);
        if (fileSize < 0 || downloadCount < 0 || maxDownloads != null && maxDownloads < 1) {
            throw new IllegalArgumentException("File metadata values are invalid");
        }
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean requiresPassword() {
        return passwordHash != null;
    }

    public boolean hasReachedDownloadLimit() {
        return maxDownloads != null && downloadCount >= maxDownloads;
    }

    public boolean canCreateDownload(Instant now) {
        return status == FileStatus.AVAILABLE
                && scannedAt != null
                && !isExpired(now)
                && !hasReachedDownloadLimit();
    }
}
