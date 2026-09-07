package dev.ghostdrop.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ghostdrop.application.CreateUploadRequest;
import dev.ghostdrop.application.GhostDropSettings;
import dev.ghostdrop.application.UploadService;
import dev.ghostdrop.configuration.AwsConfiguration;
import dev.ghostdrop.infrastructure.dynamodb.DynamoDbFileRepository;
import dev.ghostdrop.infrastructure.s3.S3StorageService;
import dev.ghostdrop.infrastructure.security.Argon2PasswordHasher;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

public final class CreateUploadHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private final UploadService uploads = new UploadService(new DynamoDbFileRepository(AwsConfiguration.dynamoDb(), required("FILES_TABLE")), new S3StorageService(required("FILES_BUCKET"), AwsConfiguration.presigner(), Duration.ofSeconds(number("GHOSTDROP_UPLOAD_URL_SECONDS", 900))), new Argon2PasswordHasher(), Clock.systemUTC(), new GhostDropSettings(number("GHOSTDROP_MAX_FILE_SIZE_BYTES", 524_288_000), number("GHOSTDROP_MIN_LIFETIME_SECONDS", 300), number("GHOSTDROP_MAX_LIFETIME_SECONDS", 2_592_000), number("GHOSTDROP_UPLOAD_URL_SECONDS", 900), number("GHOSTDROP_DOWNLOAD_URL_SECONDS", 300)));

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            var request = JSON.readValue(event.getBody(), CreateUploadRequest.class);
            var result = uploads.create(request);
            return response(201, JSON.writeValueAsString(result));
        } catch (IllegalArgumentException exception) {
            return response(400, "{\"code\":\"INVALID_REQUEST\",\"message\":\"The upload request is invalid.\"}");
        } catch (Exception exception) {
            exception.printStackTrace();
            return response(500, "{\"code\":\"INTERNAL_ERROR\",\"message\":\"The request could not be completed.\"}");
        }
    }

    private static APIGatewayV2HTTPResponse response(int status, String body) { return APIGatewayV2HTTPResponse.builder().withStatusCode(status).withHeaders(Map.of("content-type", "application/json", "access-control-allow-origin", System.getenv().getOrDefault("GHOSTDROP_ALLOWED_ORIGIN", "http://localhost:5173"))).withBody(body).build(); }
    private static String required(String name) { var value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required"); return value; }
    private static long number(String name, long defaultValue) { var value = System.getenv(name); return value == null || value.isBlank() ? defaultValue : Long.parseLong(value); }
}
