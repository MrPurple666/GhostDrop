package dev.ghostdrop.domain;

public interface StorageService {
    String createUploadUrl(String storageKey, String contentType, long fileSize);
}
