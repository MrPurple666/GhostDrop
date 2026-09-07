package dev.ghostdrop.application;

import java.time.Instant;

public record CreateUploadResult(String id, String uploadUrl, String shareUrl, String deletionToken, Instant expiresAt) {}
