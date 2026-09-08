package dev.ghostdrop.api;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import java.util.Map;

final class HttpResponses {
    private static final Map<String, String> HEADERS = Map.of(
            "content-type", "application/json",
            "access-control-allow-origin", System.getenv().getOrDefault("GHOSTDROP_ALLOWED_ORIGIN", "http://localhost:5173"));

    private HttpResponses() {}

    static APIGatewayV2HTTPResponse response(int status, String body) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(status)
                .withHeaders(HEADERS)
                .withBody(body)
                .build();
    }

    static APIGatewayV2HTTPResponse notFound() {
        return response(404, "{\"code\":\"FILE_NOT_FOUND\",\"message\":\"This GhostDrop is no longer available.\"}");
    }

    static APIGatewayV2HTTPResponse internalError() {
        return response(500, "{\"code\":\"INTERNAL_ERROR\",\"message\":\"The request could not be completed.\"}");
    }
}
