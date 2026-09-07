package dev.ghostdrop.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ghostdrop.application.DownloadService;
import dev.ghostdrop.configuration.AwsConfiguration;
import dev.ghostdrop.infrastructure.dynamodb.DynamoDbFileRepository;
import dev.ghostdrop.infrastructure.s3.S3StorageService;
import dev.ghostdrop.infrastructure.security.Argon2PasswordHasher;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

public final class CreateDownloadHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long DOWNLOAD_LIFETIME_SECONDS = number("GHOSTDROP_DOWNLOAD_URL_SECONDS", 300);
    private final S3StorageService storage = new S3StorageService(required("FILES_BUCKET"), AwsConfiguration.presigner(), Duration.ofSeconds(number("GHOSTDROP_UPLOAD_URL_SECONDS", 900)));
    private final DownloadService downloads = new DownloadService(new DynamoDbFileRepository(AwsConfiguration.dynamoDb(), required("FILES_TABLE")), key -> storage.createDownloadUrl(key, Duration.ofSeconds(DOWNLOAD_LIFETIME_SECONDS)), new Argon2PasswordHasher()::verify, Clock.systemUTC());

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            JsonNode body = event.getBody() == null || event.getBody().isBlank() ? JSON.createObjectNode() : JSON.readTree(event.getBody());
            var url = downloads.create(event.getPathParameters().get("id"), body.path("password").asText(null));
            return url.map(value -> response(200, json(Map.of("downloadUrl", value, "expiresInSeconds", DOWNLOAD_LIFETIME_SECONDS)))).orElseGet(() -> response(404, "{\"code\":\"FILE_NOT_FOUND\",\"message\":\"This GhostDrop is no longer available.\"}"));
        } catch (Exception exception) {
            return response(500, "{\"code\":\"INTERNAL_ERROR\",\"message\":\"The request could not be completed.\"}");
        }
    }

    private static String json(Object value) { try { return JSON.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private static APIGatewayV2HTTPResponse response(int status, String body) { return APIGatewayV2HTTPResponse.builder().withStatusCode(status).withHeaders(Map.of("content-type", "application/json", "access-control-allow-origin", System.getenv().getOrDefault("GHOSTDROP_ALLOWED_ORIGIN", "http://localhost:5173"))).withBody(body).build(); }
    private static String required(String name) { var value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required"); return value; }
    private static long number(String name, long defaultValue) { var value = System.getenv(name); return value == null || value.isBlank() ? defaultValue : Long.parseLong(value); }
}
