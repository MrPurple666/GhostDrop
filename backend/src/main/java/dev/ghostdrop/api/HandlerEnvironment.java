package dev.ghostdrop.api;

final class HandlerEnvironment {
    private HandlerEnvironment() {}

    static String required(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }

    static long number(String name, long defaultValue) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Long.parseLong(value);
    }
}
