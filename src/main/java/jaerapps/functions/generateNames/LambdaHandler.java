package jaerapps.functions.generateNames;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * The Lambda behind the secret santa HTTP API (API Gateway HTTP API, payload v2). API Gateway answers CORS itself
 * (aws/lib/app-stack.js), so this only turns events into {@link SantaApi} requests and back.
 */
public class LambdaHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    // Built once per container, so warm requests reuse the clients and the Cognito keys.
    private static final SantaApi api = new SantaApi();

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        Map<String, String> headers = event.getHeaders() == null ? Map.of() : event.getHeaders();
        String body = event.getBody();
        if (body != null && event.getIsBase64Encoded()) {
            body = new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
        }

        SantaApi.Response response = api.handle(new SantaApi.Request(
                event.getRequestContext().getHttp().getMethod(),
                event.getRawPath(),
                // HTTP API lower-cases header names.
                headers.get("authorization"),
                body));

        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(response.status())
                .withHeaders(Map.of("Content-Type", "application/json", "Cache-Control", "no-store"))
                .withBody(response.body())
                .build();
    }
}
