package dev.ghostdrop.api;

import static dev.ghostdrop.api.HandlerEnvironment.required;
import static dev.ghostdrop.api.HttpResponses.internalError;
import static dev.ghostdrop.api.HttpResponses.response;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import dev.ghostdrop.application.DeleteFileService;
import dev.ghostdrop.configuration.AwsConfiguration;
import dev.ghostdrop.infrastructure.dynamodb.DynamoDbFileRepository;
import dev.ghostdrop.infrastructure.s3.S3StorageService;

import java.time.Duration;

public final class DeleteFileHandler
        implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private final S3StorageService storage =
            new S3StorageService(
                    required("FILES_BUCKET"),
                    AwsConfiguration.s3(),
                    AwsConfiguration.presigner(),
                    Duration.ofSeconds(900));
    private final DeleteFileService deletion =
            new DeleteFileService(
                    new DynamoDbFileRepository(
                            AwsConfiguration.dynamoDb(), required("FILES_TABLE")),
                    storage::delete);

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        var authorization =
                event.getHeaders() == null ? null : event.getHeaders().get("authorization");
        var token =
                authorization != null && authorization.startsWith("Bearer ")
                        ? authorization.substring(7)
                        : null;
        try {
            return deletion.delete(event.getPathParameters().get("id"), token)
                    ? response(204, "")
                    : response(
                            401,
                            "{\"code\":\"UNAUTHORIZED\",\"message\":\"Deletion authorization"
                                    + " failed.\"}");
        } catch (Exception exception) {
            return internalError();
        }
    }
}
