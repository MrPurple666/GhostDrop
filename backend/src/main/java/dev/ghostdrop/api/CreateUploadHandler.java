package dev.ghostdrop.api;

import static dev.ghostdrop.api.HandlerEnvironment.number;
import static dev.ghostdrop.api.HandlerEnvironment.required;
import static dev.ghostdrop.api.HttpResponses.internalError;
import static dev.ghostdrop.api.HttpResponses.response;

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

public final class CreateUploadHandler
        implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private final UploadService uploads =
            new UploadService(
                    new DynamoDbFileRepository(
                            AwsConfiguration.dynamoDb(), required("FILES_TABLE")),
                    new S3StorageService(
                            required("FILES_BUCKET"),
                            AwsConfiguration.presigner(),
                            Duration.ofSeconds(number("GHOSTDROP_UPLOAD_URL_SECONDS", 900))),
                    new Argon2PasswordHasher(),
                    Clock.systemUTC(),
                    new GhostDropSettings(
                            number("GHOSTDROP_MAX_FILE_SIZE_BYTES", 524_288_000),
                            number("GHOSTDROP_MIN_LIFETIME_SECONDS", 300),
                            number("GHOSTDROP_MAX_LIFETIME_SECONDS", 2_592_000),
                            number("GHOSTDROP_UPLOAD_URL_SECONDS", 900),
                            number("GHOSTDROP_DOWNLOAD_URL_SECONDS", 300)));

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            var request = JSON.readValue(event.getBody(), CreateUploadRequest.class);
            var result = uploads.create(request);
            return response(201, JSON.writeValueAsString(result));
        } catch (IllegalArgumentException exception) {
            return response(
                    400,
                    "{\"code\":\"INVALID_REQUEST\",\"message\":\"The upload request is"
                            + " invalid.\"}");
        } catch (Exception exception) {
            exception.printStackTrace();
            return internalError();
        }
    }
}
