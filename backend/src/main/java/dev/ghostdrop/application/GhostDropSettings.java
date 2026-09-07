package dev.ghostdrop.application;

public record GhostDropSettings(long maximumFileSizeBytes, long minimumLifetimeSeconds, long maximumLifetimeSeconds, long uploadUrlSeconds, long downloadUrlSeconds) {}
