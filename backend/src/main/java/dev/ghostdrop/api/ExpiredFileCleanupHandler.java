package dev.ghostdrop.api;

import static dev.ghostdrop.api.HandlerEnvironment.required;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import dev.ghostdrop.configuration.AwsConfiguration;
import dev.ghostdrop.infrastructure.dynamodb.DynamoDbFileRepository;
import dev.ghostdrop.infrastructure.s3.S3StorageService;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

public final class ExpiredFileCleanupHandler implements RequestHandler<Map<String, Object>, Void> {
    private final DynamoDbFileRepository files =
            new DynamoDbFileRepository(AwsConfiguration.dynamoDb(), required("FILES_TABLE"));
    private final S3StorageService storage =
            new S3StorageService(
                    required("FILES_BUCKET"),
                    AwsConfiguration.s3(),
                    AwsConfiguration.presigner(),
                    Duration.ofSeconds(900));

    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        var expired = files.findExpired(Clock.systemUTC().instant());
        int deleted = 0;
        int failed = 0;
        for (var file : expired) {
            try {
                storage.delete(file.storageKey());
                files.delete(file.id());
                deleted++;
            } catch (Exception exception) {
                failed++;
                System.err.println(
                        "cleanup failed id="
                                + file.id()
                                + " storageKey="
                                + file.storageKey()
                                + " "
                                + exception);
            }
        }
        System.err.println(
                "cleanup summary expired="
                        + expired.size()
                        + " deleted="
                        + deleted
                        + " failed="
                        + failed);
        return null;
    }
}
