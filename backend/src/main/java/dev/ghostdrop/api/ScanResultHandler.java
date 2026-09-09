package dev.ghostdrop.api;

import static dev.ghostdrop.api.HandlerEnvironment.required;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.ghostdrop.application.ScanOutcome;
import dev.ghostdrop.application.ScanResultService;
import dev.ghostdrop.configuration.AwsConfiguration;
import dev.ghostdrop.infrastructure.dynamodb.DynamoDbFileRepository;
import dev.ghostdrop.infrastructure.s3.S3StorageService;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

public final class ScanResultHandler implements RequestHandler<Map<String, Object>, Void> {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final String BUCKET = required("FILES_BUCKET");
    private final S3StorageService storage =
            new S3StorageService(
                    BUCKET,
                    AwsConfiguration.s3(),
                    AwsConfiguration.presigner(),
                    Duration.ofSeconds(900));
    private final ScanResultService scans =
            new ScanResultService(
                    new DynamoDbFileRepository(
                            AwsConfiguration.dynamoDb(), required("FILES_TABLE")),
                    storage::delete,
                    Clock.systemUTC());

    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        try {
            JsonNode detail = JSON.valueToTree(event).path("detail");
            String bucket = detail.path("s3ObjectDetails").path("bucketName").asText();
            String objectKey = detail.path("s3ObjectDetails").path("objectKey").asText();
            String scanStatus = detail.path("scanStatus").asText();
            String resultStatus =
                    detail.path("scanResultDetails").path("scanResultStatus").asText();
            if (!BUCKET.equals(bucket) || objectKey.isBlank()) return null;
            var outcome = outcome(scanStatus, resultStatus);
            var file = scans.apply(objectKey, outcome);
            System.err.println(
                    "scan "
                            + outcome.name().toLowerCase()
                            + " objectKey="
                            + objectKey
                            + " scanStatus="
                            + scanStatus
                            + " resultStatus="
                            + resultStatus
                            + " applied="
                            + file.isPresent());
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return null;
    }

    private static ScanOutcome outcome(String scanStatus, String resultStatus) {
        if ("COMPLETED".equals(scanStatus) && "NO_THREATS_FOUND".equals(resultStatus))
            return ScanOutcome.CLEAN;
        if ("COMPLETED".equals(scanStatus) && "THREATS_FOUND".equals(resultStatus))
            return ScanOutcome.INFECTED;
        return ScanOutcome.SCAN_FAILED;
    }
}
