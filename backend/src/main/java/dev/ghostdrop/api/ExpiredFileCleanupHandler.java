package dev.ghostdrop.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import dev.ghostdrop.configuration.AwsConfiguration;
import dev.ghostdrop.infrastructure.dynamodb.DynamoDbFileRepository;
import dev.ghostdrop.infrastructure.s3.S3StorageService;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

public final class ExpiredFileCleanupHandler implements RequestHandler<Map<String, Object>, Void> {
    private final DynamoDbFileRepository files = new DynamoDbFileRepository(AwsConfiguration.dynamoDb(), required("FILES_TABLE"));
    private final S3StorageService storage = new S3StorageService(required("FILES_BUCKET"), AwsConfiguration.s3(), AwsConfiguration.presigner(), Duration.ofSeconds(900));

    @Override public Void handleRequest(Map<String, Object> event, Context context) {
        for (var file : files.findExpired(Clock.systemUTC().instant())) {
            try {
                storage.delete(file.storageKey());
                files.delete(file.id());
            } catch (Exception ignored) {
                // A later idempotent cleanup run retries the object deletion.
            }
        }
        return null;
    }
    private static String required(String name) { var value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required"); return value; }
}
