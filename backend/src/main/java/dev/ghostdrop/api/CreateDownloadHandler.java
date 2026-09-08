package dev.ghostdrop.api;

import static dev.ghostdrop.api.HandlerEnvironment.number;
import static dev.ghostdrop.api.HandlerEnvironment.required;
import static dev.ghostdrop.api.HttpResponses.internalError;
import static dev.ghostdrop.api.HttpResponses.response;

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

public final class CreateDownloadHandler
        implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final long DOWNLOAD_LIFETIME_SECONDS =
            number("GHOSTDROP_DOWNLOAD_URL_SECONDS", 300);
    private final S3StorageService storage =
            new S3StorageService(
                    required("FILES_BUCKET"),
                    AwsConfiguration.presigner(),
                    Duration.ofSeconds(number("GHOSTDROP_UPLOAD_URL_SECONDS", 900)));
    private final DownloadService downloads =
            new DownloadService(
                    new DynamoDbFileRepository(
                            AwsConfiguration.dynamoDb(), required("FILES_TABLE")),
                    file ->
                            storage.createDownloadUrl(
                                    file.storageKey(),
                                    file.originalFileName(),
                                    Duration.ofSeconds(DOWNLOAD_LIFETIME_SECONDS)),
                    new Argon2PasswordHasher()::verify,
                    Clock.systemUTC());

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            JsonNode body =
                    event.getBody() == null || event.getBody().isBlank()
                            ? JSON.createObjectNode()
                            : JSON.readTree(event.getBody());
            var url =
                    downloads.create(
                            event.getPathParameters().get("id"),
                            body.path("password").asText(null));
            return url.map(
                            value ->
                                    response(
                                            200,
                                            json(
                                                    Map.of(
                                                            "downloadUrl",
                                                            value,
                                                            "expiresInSeconds",
                                                            DOWNLOAD_LIFETIME_SECONDS))))
                    .orElseGet(HttpResponses::notFound);
        } catch (Exception exception) {
            return internalError();
        }
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
