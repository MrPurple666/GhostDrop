package dev.ghostdrop.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ghostdrop.configuration.AwsConfiguration;
import dev.ghostdrop.infrastructure.dynamodb.DynamoDbFileRepository;
import java.time.Clock;
import java.util.Map;

public final class GetFileHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private final DynamoDbFileRepository files = new DynamoDbFileRepository(AwsConfiguration.dynamoDb(), System.getenv("FILES_TABLE"));

    @Override public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            var id = event.getPathParameters().get("id");
            var file = files.findById(id).filter(value -> value.canCreateDownload(Clock.systemUTC().instant()));
            if (file.isEmpty()) return response(404, "{\"code\":\"FILE_NOT_FOUND\",\"message\":\"This GhostDrop is no longer available.\"}");
            var value = file.get();
            var response = new java.util.LinkedHashMap<String, Object>();
            response.put("id", value.id()); response.put("fileName", value.originalFileName()); response.put("contentType", value.contentType()); response.put("fileSize", value.fileSize()); response.put("expiresAt", value.expiresAt()); response.put("passwordProtected", value.requiresPassword()); response.put("remainingDownloads", value.maxDownloads() == null ? null : value.maxDownloads() - value.downloadCount());
            return response(200, JSON.writeValueAsString(response));
        } catch (Exception exception) { return response(500, "{\"code\":\"INTERNAL_ERROR\",\"message\":\"The request could not be completed.\"}"); }
    }
    private static APIGatewayV2HTTPResponse response(int status, String body) { return APIGatewayV2HTTPResponse.builder().withStatusCode(status).withHeaders(Map.of("content-type", "application/json", "access-control-allow-origin", System.getenv().getOrDefault("GHOSTDROP_ALLOWED_ORIGIN", "http://localhost:5173"))).withBody(body).build(); }
}
