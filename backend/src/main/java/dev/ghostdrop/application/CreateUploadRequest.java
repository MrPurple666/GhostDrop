package dev.ghostdrop.application;

public record CreateUploadRequest(String fileName, String contentType, long fileSize, long expiresInSeconds, String password, Integer maxDownloads) {}
