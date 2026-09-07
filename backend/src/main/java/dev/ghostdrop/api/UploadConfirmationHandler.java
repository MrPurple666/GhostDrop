package dev.ghostdrop.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import dev.ghostdrop.configuration.AwsConfiguration;
import dev.ghostdrop.infrastructure.dynamodb.DynamoDbFileRepository;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class UploadConfirmationHandler implements RequestHandler<S3Event, Void> {
    private final DynamoDbFileRepository files = new DynamoDbFileRepository(AwsConfiguration.dynamoDb(), required("FILES_TABLE"));

    @Override public Void handleRequest(S3Event event, Context context) {
        for (var record : event.getRecords()) files.markAvailable(URLDecoder.decode(record.getS3().getObject().getKey(), StandardCharsets.UTF_8));
        return null;
    }
    private static String required(String name) { var value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required"); return value; }
}
